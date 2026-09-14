package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.RotatingCategoryDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.domain.model.CardLookupResult
import com.travelbenefits.app.domain.model.Quarters
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.WalletCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val walletCardDao: WalletCardDao,
    private val cardLookupCacheDao: CardLookupCacheDao,
    private val rotatingCategoryDao: RotatingCategoryDao,
    private val json: Json,
) {
    fun observeWalletCards(): Flow<List<WalletCard>> =
        walletCardDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Wallet cards paired with their catalog entry or last cached lookup result, ready for the UI. */
    fun observeResolvedCards(): Flow<List<ResolvedWalletCard>> =
        combine(walletCardDao.observeAll(), rotatingCategoryDao.observeAll()) { entities, rotating ->
            val quarter = Quarters.keyFor()
            val byCard = rotating.filter { it.quarterKey == quarter }.associateBy { it.walletCardId }
            entities.map { resolve(it, byCard[it.id]?.toDomain()) }
        }

    private suspend fun resolve(entity: WalletCardEntity, rotating: RotatingSelection? = null): ResolvedWalletCard {
        val domain = entity.toDomain()
        val catalogId = entity.catalogCardId
        if (catalogId != null) {
            CardCatalog.findById(catalogId)?.let { entry ->
                val selection = if (entry.rotatingKind != null) rotating ?: rotatingCategoryDao.find(entity.id, Quarters.keyFor())?.toDomain() else null
                return ResolvedWalletCard.Catalog(domain, entry, selection)
            }
        }
        val name = entity.customCardName ?: catalogId.orEmpty()
        val cached = cardLookupCacheDao.find(normalizeCardName(name))
        val lookup = cached?.let {
            runCatching { json.decodeFromString<CardLookupResult>(it.resultJson) }.getOrNull()
        }
        return ResolvedWalletCard.Custom(domain, lookup)
    }

    suspend fun addCatalogCard(catalogId: String, nickname: String?, notes: String?): Long =
        walletCardDao.insert(
            WalletCardEntity(
                nickname = nickname,
                catalogCardId = catalogId,
                customCardName = null,
                dateAdded = System.currentTimeMillis(),
                notes = notes,
            ),
        )

    suspend fun addCustomCard(name: String, nickname: String?, notes: String?): Long =
        walletCardDao.insert(
            WalletCardEntity(
                nickname = nickname,
                catalogCardId = null,
                customCardName = name,
                dateAdded = System.currentTimeMillis(),
                notes = notes,
            ),
        )

    suspend fun removeCard(id: Long) = walletCardDao.deleteById(id)

    /** All wallet cards resolved once (for matching statement emails and building advisor context outside a Flow). */
    suspend fun getResolvedCards(): List<ResolvedWalletCard> = walletCardDao.getAll().map { resolve(it) }

    /** Records a rewards balance on a card. Returns the previous balance so callers can report the change. */
    suspend fun updateRewardsBalance(id: Long, balance: Long?, asOf: Long, last4: String? = null): Long? {
        val existing = walletCardDao.findById(id) ?: return null
        walletCardDao.update(
            existing.copy(
                rewardsBalance = balance,
                rewardsBalanceAsOf = if (balance != null) asOf else existing.rewardsBalanceAsOf,
                last4 = last4 ?: existing.last4,
            ),
        )
        return existing.rewardsBalance
    }

    suspend fun updateCardDetails(
        id: Long,
        nickname: String?,
        last4: String?,
        notes: String?,
        memberName: String?,
        dateOpenedEpochDay: Long?,
        bonusSpendRequiredUsd: Long?,
        bonusDeadlineEpochDay: Long?,
        bonusSpendToDateUsd: Long?,
        bonusEarned: Boolean,
    ) {
        val existing = walletCardDao.findById(id) ?: return
        walletCardDao.update(
            existing.copy(
                nickname = nickname,
                last4 = last4,
                notes = notes,
                memberName = memberName,
                dateOpenedEpochDay = dateOpenedEpochDay,
                bonusSpendRequiredUsd = bonusSpendRequiredUsd,
                bonusDeadlineEpochDay = bonusDeadlineEpochDay,
                bonusSpendToDateUsd = bonusSpendToDateUsd,
                bonusEarnedAt = if (bonusEarned) existing.bonusEarnedAt ?: System.currentTimeMillis() else null,
            ),
        )
    }

    /** Adds a statement's new purchases to the welcome-bonus progress, for statements dated after the card was opened and while the bonus is unearned. */
    suspend fun addBonusSpend(id: Long, purchasesUsd: Long, statementAt: Long): Boolean {
        val existing = walletCardDao.findById(id) ?: return false
        val required = existing.bonusSpendRequiredUsd ?: return false
        if (existing.bonusEarnedAt != null) return false
        val opened = existing.dateOpenedEpochDay
        if (opened != null && statementAt / 86_400_000L < opened) return false
        val total = (existing.bonusSpendToDateUsd ?: 0L) + purchasesUsd
        walletCardDao.update(existing.copy(bonusSpendToDateUsd = total, bonusEarnedAt = if (total >= required) System.currentTimeMillis() else null))
        return true
    }

    fun observeRotatingSelections(): Flow<List<RotatingSelection>> =
        rotatingCategoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun setRotatingSelection(walletCardId: Long, quarterKey: String, categories: List<SpendingCategory>, activated: Boolean) {
        rotatingCategoryDao.upsert(RotatingSelection(walletCardId, quarterKey, categories, activated).toEntity())
    }

    suspend fun getRotatingSelections(): List<RotatingSelection> = rotatingCategoryDao.getAll().map { it.toDomain() }

    companion object {
        fun normalizeCardName(name: String): String = name.trim().lowercase()
    }
}
