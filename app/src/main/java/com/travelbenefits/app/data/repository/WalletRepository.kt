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

    companion object {
        fun normalizeCardName(name: String): String = name.trim().lowercase()
    }
}
