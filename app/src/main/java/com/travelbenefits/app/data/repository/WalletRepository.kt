package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.data.local.dao.CardLookupCacheDao
import com.travelbenefits.app.data.local.dao.WalletCardDao
import com.travelbenefits.app.data.local.entity.WalletCardEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.domain.model.CardLookupResult
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.WalletCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(
    private val walletCardDao: WalletCardDao,
    private val cardLookupCacheDao: CardLookupCacheDao,
    private val json: Json,
) {
    fun observeWalletCards(): Flow<List<WalletCard>> =
        walletCardDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    /** Wallet cards paired with their catalog entry or last cached lookup result, ready for the UI. */
    fun observeResolvedCards(): Flow<List<ResolvedWalletCard>> =
        walletCardDao.observeAll().map { entities -> entities.map { resolve(it) } }

    private suspend fun resolve(entity: WalletCardEntity): ResolvedWalletCard {
        val domain = entity.toDomain()
        val catalogId = entity.catalogCardId
        if (catalogId != null) {
            CardCatalog.findById(catalogId)?.let { return ResolvedWalletCard.Catalog(domain, it) }
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

    suspend fun updateCardDetails(id: Long, nickname: String?, last4: String?, notes: String?) {
        val existing = walletCardDao.findById(id) ?: return
        walletCardDao.update(existing.copy(nickname = nickname, last4 = last4, notes = notes))
    }

    companion object {
        fun normalizeCardName(name: String): String = name.trim().lowercase()
    }
}
