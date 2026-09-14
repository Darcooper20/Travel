package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CardCredit
import com.travelbenefits.app.domain.model.CreditState
import com.travelbenefits.app.domain.model.LedgerEntry
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.LedgerSource
import com.travelbenefits.app.domain.model.PeriodBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BenefitLedgerEngineTest {

    private val today = LocalDate.of(2026, 9, 14)
    private val annual = CardCredit("Travel credit", 100, "annual", "test", periodBasis = PeriodBasis.CALENDAR)

    private fun entry(kind: LedgerEntryKind, cents: Long, day: LocalDate = today, unconfirmed: Boolean = false) =
        LedgerEntry(0, 1, annual.label, kind, cents, day.toEpochDay(), null, null, LedgerSource.MANUAL, unconfirmed, 0)

    @Test
    fun `allowance 100 with 35 received and 20 pending leaves 45 uncommitted`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.POSTED, 3_500), entry(LedgerEntryKind.PENDING, 2_000)), window, today)
        assertEquals(10_000, totals.allowanceCents)
        assertEquals(3_500, totals.receivedCents)
        assertEquals(2_000, totals.pendingCents)
        assertEquals(4_500, totals.uncommittedCents)
        assertEquals(CreditState.PARTIALLY_USED, totals.state)
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), window.endEpochDay)
    }

    @Test
    fun `pending alone is never received`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.PENDING, 10_000)), window, today)
        assertEquals(0, totals.receivedCents)
        assertEquals(CreditState.PENDING, totals.state)
    }

    @Test
    fun `reversal reduces received and uncommitted reopens`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.POSTED, 10_000), entry(LedgerEntryKind.REVERSED, 4_000)), window, today)
        assertEquals(6_000, totals.receivedCents)
        assertEquals(4_000, totals.uncommittedCents)
    }

    @Test
    fun `unconfirmed proposals are excluded from totals`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.POSTED, 5_000, unconfirmed = true)), window, today)
        assertEquals(0, totals.receivedCents)
        assertEquals(CreditState.NEEDS_CONFIRMATION, totals.state)
        assertTrue(totals.hasUnconfirmed)
    }

    @Test
    fun `migrated unknown-amount usage is reported as unknown not zero`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.USED_UNKNOWN_AMOUNT, 0)), window, today)
        assertEquals(CreditState.USED_AMOUNT_UNKNOWN, totals.state)
        assertTrue(totals.hasUnknownAmountUsage)
    }

    @Test
    fun `anniversary period uses date opened`() {
        val credit = annual.copy(periodBasis = PeriodBasis.ANNIVERSARY)
        val window = BenefitLedgerEngine.periodWindow(credit, today, LocalDate.of(2023, 11, 5))
        assertEquals(LocalDate.of(2025, 11, 5).toEpochDay(), window.startEpochDay)
        assertEquals(LocalDate.of(2026, 11, 4).toEpochDay(), window.endEpochDay)
        assertFalse(window.isAssumed)
    }

    @Test
    fun `anniversary without date opened falls back to calendar and is flagged assumed`() {
        val credit = annual.copy(periodBasis = PeriodBasis.ANNIVERSARY)
        val window = BenefitLedgerEngine.periodWindow(credit, today, null)
        assertTrue(window.isAssumed)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), window.startEpochDay)
    }

    @Test
    fun `monthly sublimit gives a monthly window and allowance`() {
        val credit = CardCredit("Uber Cash", 120, "monthly (\$10/mo)", "test", periodBasis = PeriodBasis.CALENDAR)
        val window = BenefitLedgerEngine.periodWindow(credit, today, null)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), window.startEpochDay)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), window.endEpochDay)
        assertEquals(1_000, BenefitLedgerEngine.allowanceCents(credit))
    }

    @Test
    fun `entries outside the window are ignored`() {
        val window = BenefitLedgerEngine.periodWindow(annual, today, null)
        val totals = BenefitLedgerEngine.totals(annual, listOf(entry(LedgerEntryKind.POSTED, 10_000, LocalDate.of(2025, 6, 1))), window, today)
        assertEquals(0, totals.receivedCents)
        assertEquals(CreditState.UNUSED, totals.state)
    }
}
