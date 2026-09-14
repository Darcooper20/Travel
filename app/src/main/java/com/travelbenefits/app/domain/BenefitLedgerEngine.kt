package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CardCredit
import com.travelbenefits.app.domain.model.CreditPeriod
import com.travelbenefits.app.domain.model.CreditPeriodWindow
import com.travelbenefits.app.domain.model.CreditState
import com.travelbenefits.app.domain.model.LedgerEntry
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.PeriodBasis
import java.time.LocalDate
import java.time.YearMonth

/**
 * Deterministic, integer-cents arithmetic over a credit's ledger for one
 * period. Pending never counts as received; unknown-amount usage is
 * reported as unknown rather than as a number.
 *
 * Acceptance: allowance $100, $35 posted, $20 pending -> uncommitted $45.
 */
object BenefitLedgerEngine {

    data class PeriodTotals(
        val allowanceCents: Long,
        val receivedCents: Long,
        val pendingCents: Long,
        val reversedCents: Long,
        /** allowance - received - pending, floored at 0. */
        val uncommittedCents: Long,
        val state: CreditState,
        val hasUnknownAmountUsage: Boolean,
        val hasUnconfirmed: Boolean,
        val window: CreditPeriodWindow,
    )

    /** The period containing [today] for this credit, given the card's date opened (needed for anniversary periods). */
    fun periodWindow(credit: CardCredit, today: LocalDate, dateOpened: LocalDate?): CreditPeriodWindow {
        val period = CreditPeriod.fromFrequency(credit.frequency)
        // Monthly sub-limited credits reset monthly whatever the annual basis says.
        val monthly = period == CreditPeriod.MONTHLY || credit.monthlySublimitUsd != null
        if (monthly) {
            val ym = YearMonth.from(today)
            return CreditPeriodWindow(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay(), PeriodBasis.CALENDAR, isAssumed = credit.periodBasis == PeriodBasis.UNKNOWN)
        }
        return when (credit.periodBasis) {
            PeriodBasis.ANNIVERSARY -> {
                if (dateOpened == null) {
                    calendar(period, today, assumed = true, basis = PeriodBasis.ANNIVERSARY)
                } else {
                    val years = if (period == CreditPeriod.EVERY_4_YEARS) 4L else 1L
                    var start = dateOpened.withYear(today.year)
                    if (start.isAfter(today)) start = start.minusYears(1)
                    if (period == CreditPeriod.EVERY_4_YEARS) {
                        // Anchor 4-year windows to the opening date.
                        var s: LocalDate = dateOpened
                        while (!s.plusYears(4).isAfter(today)) s = s.plusYears(4)
                        start = s
                    }
                    CreditPeriodWindow(start.toEpochDay(), start.plusYears(years).minusDays(1).toEpochDay(), PeriodBasis.ANNIVERSARY, isAssumed = false)
                }
            }
            PeriodBasis.CALENDAR -> calendar(period, today, assumed = false, basis = PeriodBasis.CALENDAR)
            PeriodBasis.STATEMENT, PeriodBasis.UNKNOWN -> calendar(period, today, assumed = true, basis = credit.periodBasis)
        }
    }

    private fun calendar(period: CreditPeriod, today: LocalDate, assumed: Boolean, basis: PeriodBasis): CreditPeriodWindow = when (period) {
        CreditPeriod.MONTHLY -> YearMonth.from(today).let { CreditPeriodWindow(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay(), basis, assumed) }
        CreditPeriod.ANNUAL -> CreditPeriodWindow(LocalDate.of(today.year, 1, 1).toEpochDay(), LocalDate.of(today.year, 12, 31).toEpochDay(), basis, assumed)
        CreditPeriod.EVERY_4_YEARS -> CreditPeriodWindow(today.minusYears(4).plusDays(1).toEpochDay(), today.toEpochDay(), basis, true)
    }

    /** Allowance for the window in cents: the monthly sub-limit when one exists, else the annual value (4-year credits are one-shot). */
    fun allowanceCents(credit: CardCredit): Long {
        credit.monthlySublimitUsd?.let { return it * 100L }
        return credit.annualValueUsd * 100L
    }

    fun totals(credit: CardCredit, entries: List<LedgerEntry>, window: CreditPeriodWindow, today: LocalDate): PeriodTotals {
        val inWindow = entries.filter { it.epochDay in window.startEpochDay..window.endEpochDay }
        val confirmed = inWindow.filter { !it.needsConfirmation }
        val received = confirmed.filter { it.kind == LedgerEntryKind.POSTED || it.kind == LedgerEntryKind.ADJUSTMENT }.sumOf { it.amountCents }
        val reversed = confirmed.filter { it.kind == LedgerEntryKind.REVERSED }.sumOf { it.amountCents }
        val pending = confirmed.filter { it.kind == LedgerEntryKind.PENDING || it.kind == LedgerEntryKind.EXPECTED }.sumOf { it.amountCents }
        val unknownUsage = confirmed.any { it.kind == LedgerEntryKind.USED_UNKNOWN_AMOUNT }
        val allowance = allowanceCents(credit)
        val netReceived = (received - reversed).coerceAtLeast(0)
        val uncommitted = (allowance - netReceived - pending).coerceAtLeast(0)
        val expired = today.toEpochDay() > window.endEpochDay
        val state = when {
            inWindow.any { it.needsConfirmation } && netReceived == 0L && pending == 0L -> CreditState.NEEDS_CONFIRMATION
            unknownUsage && netReceived == 0L && pending == 0L -> CreditState.USED_AMOUNT_UNKNOWN
            reversed > 0 && netReceived == 0L && pending == 0L -> CreditState.REVERSED
            netReceived >= allowance && allowance > 0 -> CreditState.RECEIVED
            pending > 0 && netReceived + pending >= allowance -> CreditState.PENDING
            netReceived > 0 || pending > 0 -> CreditState.PARTIALLY_USED
            expired -> CreditState.EXPIRED
            else -> CreditState.UNUSED
        }
        return PeriodTotals(
            allowanceCents = allowance,
            receivedCents = netReceived,
            pendingCents = pending,
            reversedCents = reversed,
            uncommittedCents = if (unknownUsage && netReceived == 0L && pending == 0L) 0 else uncommitted,
            state = state,
            hasUnknownAmountUsage = unknownUsage,
            hasUnconfirmed = inWindow.any { it.needsConfirmation },
            window = window,
        )
    }
}
