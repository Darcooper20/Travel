package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.dao.PlaidDao
import com.travelbenefits.app.data.local.entity.PlaidAccountEntity
import com.travelbenefits.app.data.local.entity.PlaidItemEntity
import com.travelbenefits.app.data.local.entity.TransactionEntity
import com.travelbenefits.app.data.remote.plaid.BackendAccount
import com.travelbenefits.app.data.remote.plaid.ExchangeRequest
import com.travelbenefits.app.data.remote.plaid.PlaidBackendApi
import com.travelbenefits.app.data.remote.plaid.SyncRequest
import com.travelbenefits.app.domain.PlaidCategoryMapper
import com.travelbenefits.app.domain.SpendAnalyzer
import com.travelbenefits.app.domain.model.PlaidAccount
import com.travelbenefits.app.domain.model.PlaidItem
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendTransaction
import com.travelbenefits.app.domain.model.SpendingCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class PlaidSyncReport(val itemsSynced: Int, val added: Int, val modified: Int, val removed: Int, val errors: List<String>) {
    fun summary(): String = when {
        itemsSynced == 0 && errors.isEmpty() -> "No linked accounts yet."
        errors.isEmpty() -> "Synced $itemsSynced account link(s): $added new, $modified updated, $removed removed transaction(s)."
        else -> "Synced with ${errors.size} error(s): ${errors.first()}"
    }
}

/**
 * Talks to the self-hosted Plaid worker (plaid-backend/), keeps linked
 * items/accounts/transactions in Room, and maps accounts to wallet cards so
 * spend can be judged against the wallet. Plaid never sees the phone's
 * secrets and the phone never sees Plaid's.
 */
@Singleton
class PlaidRepository @Inject constructor(
    private val api: PlaidBackendApi,
    private val dao: PlaidDao,
    private val securePrefs: SecurePrefs,
    private val walletRepository: WalletRepository,
    private val spendAnalyzer: SpendAnalyzer,
) {
    val isConfigured: Boolean
        get() = !securePrefs.plaidBackendUrl.isNullOrBlank() && !securePrefs.plaidAppToken.isNullOrBlank()

    fun observeItems(): Flow<List<PlaidItem>> = combine(dao.observeItems(), dao.observeAccounts()) { items, accounts ->
        items.map { item -> item.toDomain(accounts.filter { it.itemId == item.itemId }.map { it.toDomain() }) }
    }

    fun observeAccounts(): Flow<List<PlaidAccount>> = dao.observeAccounts().map { list -> list.map { it.toDomain() } }

    fun observeTransactions(sinceEpochDay: Long = LocalDate.now().minusDays(120).toEpochDay()): Flow<List<SpendTransaction>> =
        combine(dao.observeTransactionsSince(sinceEpochDay), dao.observeAccounts()) { txns, accounts ->
            val cardByAccount = accounts.associate { it.accountId to it.walletCardId }
            txns.map { it.toDomain(cardByAccount[it.accountId]) }
        }

    fun observeTransactionCount(): Flow<Int> = dao.observeTransactionCount()

    suspend fun createLinkToken(): Result<String> = call { base, auth -> api.createLinkToken("$base/link-token", auth).linkToken }

    /** After Link succeeds: exchange the public token, store the item and its accounts, and pre-map accounts to wallet cards. */
    suspend fun completeLink(publicToken: String, institutionName: String?): Result<PlaidItem> = call { base, auth ->
        val response = api.exchange("$base/exchange", auth, ExchangeRequest(publicToken, institutionName))
        val now = System.currentTimeMillis()
        dao.upsertItem(PlaidItemEntity(response.itemId, response.institutionName ?: institutionName, null, now, null, null))
        val cards = walletRepository.getResolvedCards()
        val entities = response.accounts.map { it.toEntity(response.itemId, autoMap(it, response.institutionName ?: institutionName, cards)) }
        dao.upsertAccounts(entities)
        PlaidItemEntity(response.itemId, response.institutionName ?: institutionName, null, now, null, null).toDomain(entities.map { it.toDomain() })
    }

    suspend fun mapAccount(accountId: String, walletCardId: Long?) = dao.mapAccount(accountId, walletCardId)

    suspend fun removeItem(itemId: String): Result<Unit> = call { base, auth ->
        runCatching { api.removeItem("$base/items/${java.net.URLEncoder.encode(itemId, "UTF-8")}", auth) }
        dao.deleteTransactionsForItem(itemId)
        dao.deleteAccountsForItem(itemId)
        dao.deleteItem(itemId)
    }

    /** Pulls new/changed transactions for every item (cursor-based), then refreshes welcome-bonus progress on mapped cards. */
    suspend fun syncAll(): Result<PlaidSyncReport> {
        if (!isConfigured) return Result.failure(IllegalStateException("Set the Plaid backend URL and token in Settings first."))
        val base = securePrefs.plaidBackendUrl!!.trimEnd('/')
        val auth = "Bearer ${securePrefs.plaidAppToken}"
        var added = 0
        var modified = 0
        var removed = 0
        val errors = mutableListOf<String>()
        val items = dao.getItems()
        for (item in items) {
            var cursor = item.cursor
            try {
                var hasMore = true
                var guard = 0
                while (hasMore && guard++ < 20) {
                    val page = api.sync("$base/transactions/sync", auth, SyncRequest(item.itemId, cursor))
                    if (page.accounts.isNotEmpty()) {
                        val existing = dao.getAccounts().associateBy { it.accountId }
                        val cards = walletRepository.getResolvedCards()
                        dao.upsertAccounts(
                            page.accounts.map { a ->
                                a.toEntity(item.itemId, existing[a.accountId]?.walletCardId ?: autoMap(a, item.institutionName, cards))
                            },
                        )
                    }
                    val upserts = (page.added + page.modified).map { it.toEntity() }
                    if (upserts.isNotEmpty()) dao.upsertTransactions(upserts)
                    if (page.removed.isNotEmpty()) dao.deleteTransactions(page.removed)
                    added += page.added.size
                    modified += page.modified.size
                    removed += page.removed.size
                    cursor = page.nextCursor ?: cursor
                    hasMore = page.hasMore
                }
                dao.updateItem(item.copy(cursor = cursor, lastSyncAt = System.currentTimeMillis(), lastError = null))
            } catch (e: Exception) {
                errors += "${item.institutionName ?: item.itemId}: ${e.message ?: e.javaClass.simpleName}"
                dao.updateItem(item.copy(cursor = cursor, lastError = e.message ?: e.javaClass.simpleName))
            }
        }
        dao.pruneTransactions(LocalDate.now().minusDays(400).toEpochDay())
        refreshBonusProgress()
        return Result.success(PlaidSyncReport(items.size, added, modified, removed, errors))
    }

    /** Welcome-bonus spend-to-date from real transactions for cards mapped to a Plaid account and opened on a known date. */
    private suspend fun refreshBonusProgress() {
        val accounts = dao.getAccounts()
        val mappedCardIds = accounts.mapNotNull { it.walletCardId }.toSet()
        if (mappedCardIds.isEmpty()) return
        val cardByAccount = accounts.associate { it.accountId to it.walletCardId }
        val since = LocalDate.now().minusDays(400).toEpochDay()
        val txns = dao.getTransactionsSince(since).map { it.toDomain(cardByAccount[it.accountId]) }
        for (card in walletRepository.getResolvedCards()) {
            val id = card.walletCard.id
            if (id !in mappedCardIds) continue
            val required = card.walletCard.bonusSpendRequiredUsd ?: continue
            val opened = card.walletCard.dateOpenedEpochDay ?: continue
            val spent = spendAnalyzer.spendSince(txns, id, opened).toLong()
            walletRepository.setBonusSpendFromTransactions(id, spent, required)
        }
    }

    private suspend fun <T> call(block: suspend (base: String, auth: String) -> T): Result<T> {
        val base = securePrefs.plaidBackendUrl?.trimEnd('/')
        val token = securePrefs.plaidAppToken
        if (base.isNullOrBlank() || token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Set the Plaid backend URL and token in Settings first."))
        }
        return runCatching { block(base, "Bearer $token") }
    }

    /** Last-4 match first, then issuer name + card name words against the catalog entry. */
    private fun autoMap(account: BackendAccount, institution: String?, cards: List<ResolvedWalletCard>): Long? {
        val mask = account.mask?.filter { it.isDigit() }?.takeLast(4)
        if (mask != null) cards.firstOrNull { it.walletCard.last4 == mask }?.let { return it.walletCard.id }
        val text = listOfNotNull(institution, account.officialName, account.name).joinToString(" ").lowercase()
        val byName = cards.filter { card ->
            val entry = (card as? ResolvedWalletCard.Catalog)?.entry ?: CardCatalog.findById(card.walletCard.catalogCardId.orEmpty())
            val issuerOk = entry == null || text.contains(entry.issuer.lowercase().substringBefore(" "))
            val words = (entry?.displayName ?: card.walletCard.customCardName.orEmpty()).lowercase().split(' ').filter { it.length > 3 && it !in GENERIC }
            issuerOk && words.isNotEmpty() && words.all { text.contains(it) }
        }
        return if (byName.size == 1) byName.first().walletCard.id else null
    }

    private fun BackendAccount.toEntity(itemId: String, walletCardId: Long?) = PlaidAccountEntity(
        accountId = accountId, itemId = itemId, name = name ?: officialName ?: "Account", officialName = officialName, mask = mask, type = type, subtype = subtype, walletCardId = walletCardId,
    )

    private fun com.travelbenefits.app.data.remote.plaid.BackendTransaction.toEntity(): TransactionEntity = TransactionEntity(
        transactionId = transactionId,
        accountId = accountId,
        amount = amount,
        epochDay = date?.let { runCatching { LocalDate.parse(it.take(10)).toEpochDay() }.getOrNull() } ?: LocalDate.now().toEpochDay(),
        name = name ?: merchantName ?: "Transaction",
        merchantName = merchantName,
        pfcPrimary = pfcPrimary,
        pfcDetailed = pfcDetailed,
        spendingCategory = PlaidCategoryMapper.map(pfcPrimary, pfcDetailed, merchantName ?: name)?.name,
        pending = pending,
    )

    private fun PlaidItemEntity.toDomain(accounts: List<PlaidAccount>) = PlaidItem(itemId, institutionName, createdAt, lastSyncAt, lastError, accounts)
    private fun PlaidAccountEntity.toDomain() = PlaidAccount(accountId, itemId, name, officialName, mask, type, subtype, walletCardId)
    private fun TransactionEntity.toDomain(walletCardId: Long?) = SpendTransaction(
        transactionId = transactionId,
        accountId = accountId,
        walletCardId = walletCardId,
        amountUsd = amount,
        epochDay = epochDay,
        name = name,
        merchantName = merchantName,
        category = spendingCategory?.let { c -> SpendingCategory.entries.firstOrNull { it.name == c } },
        pending = pending,
    )

    private companion object {
        val GENERIC = setOf("card", "credit", "visa", "mastercard", "american", "express", "rewards", "preferred", "world", "elite", "signature", "infinite", "cash", "back", "from", "with", "bank")
    }
}
