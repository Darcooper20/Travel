package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.BenefitDao
import com.travelbenefits.app.data.local.dao.BenefitLedgerDao
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.BenefitLedgerEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.local.toEntity
import com.travelbenefits.app.domain.BenefitLedgerEngine
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.CreditPeriod
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.LedgerEntry
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.LedgerSource
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificates and card credits. Card credits are derived from each wallet
 * card's catalog entry plus the amount-based benefit ledger; certificates
 * (free nights, companion passes, upgrades) are stored items.
 */
@Singleton
class BenefitsRepository @Inject constructor(
    private val dao: BenefitDao,
    private val ledgerDao: BenefitLedgerDao,
    private val walletRepository: WalletRepository,
) {
    fun observeItems(): Flow<List<BenefitItem>> = dao.observeItems().map { list -> list.map { it.toDomain() } }

    fun observeLedger(): Flow<List<LedgerEntry>> = ledgerDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeCreditStatuses(): Flow<List<CreditStatus>> =
        combine(walletRepository.observeResolvedCards(), ledgerDao.observeAll()) { cards, ledger ->
            creditStatuses(cards, ledger.map { it.toDomain() })
        }

    suspend fun getCreditStatuses(): List<CreditStatus> =
        creditStatuses(walletRepository.getResolvedCards(), ledgerDao.getAll().map { it.toDomain() })

    suspend fun getItems(): List<BenefitItem> = dao.getItems().map { it.toDomain() }

    suspend fun getLedger(): List<LedgerEntry> = ledgerDao.getAll().map { it.toDomain() }

    private fun creditStatuses(cards: List<ResolvedWalletCard>, ledger: List<LedgerEntry>, today: LocalDate = LocalDate.now()): List<CreditStatus> =
        cards.filterIsInstance<ResolvedWalletCard.Catalog>().flatMap { card ->
            val opened = card.walletCard.dateOpenedEpochDay?.let { LocalDate.ofEpochDay(it) }
            card.entry.credits.map { credit ->
                val window = BenefitLedgerEngine.periodWindow(credit, today, opened)
                val entries = ledger.filter { it.walletCardId == card.walletCard.id && it.creditLabel == credit.label }
                val totals = BenefitLedgerEngine.totals(credit, entries, window, today)
                CreditStatus(
                    walletCard = card,
                    credit = credit,
                    period = CreditPeriod.fromFrequency(credit.frequency),
                    window = window,
                    allowanceCents = totals.allowanceCents,
                    receivedCents = totals.receivedCents,
                    pendingCents = totals.pendingCents,
                    uncommittedCents = totals.uncommittedCents,
                    state = totals.state,
                    hasUnknownAmountUsage = totals.hasUnknownAmountUsage,
                    hasUnconfirmed = totals.hasUnconfirmed,
                    entries = entries.filter { it.epochDay in window.startEpochDay..window.endEpochDay }.sortedByDescending { it.epochDay },
                )
            }
        }

    /** Records a ledger movement. Amount in whole cents; [epochDay] defaults to today. */
    suspend fun recordLedgerEntry(
        walletCardId: Long,
        creditLabel: String,
        kind: LedgerEntryKind,
        amountCents: Long,
        epochDay: Long = LocalDate.now().toEpochDay(),
        note: String? = null,
        transactionId: String? = null,
        source: LedgerSource = LedgerSource.MANUAL,
        needsConfirmation: Boolean = false,
    ): Long {
        if (transactionId != null) ledgerDao.findByTransaction(transactionId)?.let { return it.id }
        return ledgerDao.insert(
            BenefitLedgerEntity(
                walletCardId = walletCardId, creditLabel = creditLabel, kind = kind, amountCents = amountCents.coerceAtLeast(0), epochDay = epochDay,
                transactionId = transactionId, note = note, source = source, needsConfirmation = needsConfirmation, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Confirms or rejects a machine-proposed match. Rejecting deletes the proposal; the transaction stays untouched. */
    suspend fun resolveProposal(id: Long, confirm: Boolean) {
        val existing = ledgerDao.findById(id) ?: return
        if (confirm) ledgerDao.update(existing.copy(needsConfirmation = false)) else ledgerDao.deleteById(id)
    }

    /** Upgrades a migrated "used, amount unknown" row into a real posted amount. */
    suspend fun setAmountForUnknownUsage(id: Long, amountCents: Long) {
        val existing = ledgerDao.findById(id) ?: return
        ledgerDao.update(existing.copy(kind = LedgerEntryKind.POSTED, amountCents = amountCents, note = "Amount added by user", source = LedgerSource.MANUAL))
    }

    suspend fun deleteLedgerEntry(id: Long) = ledgerDao.deleteById(id)

    suspend fun addItem(
        kind: BenefitKind,
        title: String,
        program: LoyaltyProgram?,
        walletCardId: Long?,
        valueUsd: Double?,
        expiresEpochDay: Long?,
        notes: String?,
        source: LoyaltyAccountSource = LoyaltyAccountSource.MANUAL,
        sourceEmailSubject: String? = null,
    ): Long = dao.insertItem(
        BenefitItemEntity(
            kind = kind, title = title, program = program, walletCardId = walletCardId, valueUsd = valueUsd, expiresEpochDay = expiresEpochDay,
            usedAt = null, notes = notes, source = source, sourceEmailSubject = sourceEmailSubject, createdAt = System.currentTimeMillis(),
        ),
    )

    /** Adds a certificate found in an email unless one with the same title/program is already tracked. Returns true when inserted. */
    suspend fun addFromEmailIfNew(kind: BenefitKind, title: String, program: LoyaltyProgram?, expiresEpochDay: Long?, subject: String?): Boolean {
        val existing = dao.findItemByTitle(title, program?.name)
        if (existing != null) {
            if (expiresEpochDay != null && existing.expiresEpochDay != expiresEpochDay) dao.updateItem(existing.copy(expiresEpochDay = expiresEpochDay))
            return false
        }
        addItem(kind, title, program, null, null, expiresEpochDay, null, LoyaltyAccountSource.GMAIL_SCAN, subject)
        return true
    }

    suspend fun update(item: BenefitItem) = dao.updateItem(item.toEntity())

    suspend fun setUsed(id: Long, used: Boolean) {
        val existing = dao.findItem(id) ?: return
        dao.updateItem(existing.copy(usedAt = if (used) System.currentTimeMillis() else null))
    }

    suspend fun delete(id: Long) = dao.deleteItem(id)
}
