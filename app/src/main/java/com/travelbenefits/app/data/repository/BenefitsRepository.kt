package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.BenefitDao
import com.travelbenefits.app.data.local.entity.BenefitItemEntity
import com.travelbenefits.app.data.local.entity.CreditUsageEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.local.toEntity
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.CreditPeriod
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.Quarters
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificates and card credits. Card credits are derived from the catalog
 * entry of each wallet card plus a "last used" ledger; certificates (free
 * nights, companion passes, upgrades) are stored items, added by hand or by
 * the email monitor when a program emails one.
 */
@Singleton
class BenefitsRepository @Inject constructor(
    private val dao: BenefitDao,
    private val walletRepository: WalletRepository,
) {
    fun observeItems(): Flow<List<BenefitItem>> = dao.observeItems().map { list -> list.map { it.toDomain() } }

    fun observeCreditStatuses(): Flow<List<CreditStatus>> =
        combine(walletRepository.observeResolvedCards(), dao.observeCreditUsage()) { cards, usage ->
            creditStatuses(cards, usage.associate { (it.walletCardId to it.creditLabel) to it.lastUsedAt })
        }

    suspend fun getCreditStatuses(): List<CreditStatus> =
        creditStatuses(walletRepository.getResolvedCards(), dao.getCreditUsage().associate { (it.walletCardId to it.creditLabel) to it.lastUsedAt })

    suspend fun getItems(): List<BenefitItem> = dao.getItems().map { it.toDomain() }

    private fun creditStatuses(cards: List<ResolvedWalletCard>, usage: Map<Pair<Long, String>, Long>, today: LocalDate = LocalDate.now()): List<CreditStatus> =
        cards.filterIsInstance<ResolvedWalletCard.Catalog>().flatMap { card ->
            card.entry.credits.map { credit ->
                val period = CreditPeriod.fromFrequency(credit.frequency)
                val lastUsed = usage[card.walletCard.id to credit.label]
                val periodStart = when (period) {
                    CreditPeriod.MONTHLY -> today.withDayOfMonth(1)
                    CreditPeriod.ANNUAL -> today.withDayOfYear(1)
                    CreditPeriod.EVERY_4_YEARS -> today.minusYears(4)
                }
                val periodEnd = when (period) {
                    CreditPeriod.MONTHLY -> Quarters.endOfMonth(today)
                    CreditPeriod.ANNUAL -> LocalDate.of(today.year, 12, 31)
                    CreditPeriod.EVERY_4_YEARS -> (lastUsed?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().plusYears(4) } ?: today.plusYears(4))
                }
                val usedThisPeriod = lastUsed != null &&
                    !Instant.ofEpochMilli(lastUsed).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(periodStart)
                CreditStatus(
                    walletCard = card,
                    credit = credit,
                    period = period,
                    lastUsedAt = lastUsed,
                    isAvailable = !usedThisPeriod,
                    periodEndsEpochDay = periodEnd.toEpochDay(),
                    periodValueUsd = if (period == CreditPeriod.MONTHLY) credit.annualValueUsd / 12.0 else credit.annualValueUsd.toDouble(),
                )
            }
        }

    suspend fun markCreditUsed(walletCardId: Long, creditLabel: String, used: Boolean) {
        if (used) dao.upsertCreditUsage(CreditUsageEntity(walletCardId, creditLabel, System.currentTimeMillis())) else dao.clearCreditUsage(walletCardId, creditLabel)
    }

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
            kind = kind,
            title = title,
            program = program,
            walletCardId = walletCardId,
            valueUsd = valueUsd,
            expiresEpochDay = expiresEpochDay,
            usedAt = null,
            notes = notes,
            source = source,
            sourceEmailSubject = sourceEmailSubject,
            createdAt = System.currentTimeMillis(),
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
