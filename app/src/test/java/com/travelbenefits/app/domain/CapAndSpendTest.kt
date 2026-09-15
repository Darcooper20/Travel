package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CapPeriod
import com.travelbenefits.app.domain.model.PlaidAccount
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.SpendPeriod
import com.travelbenefits.app.domain.model.SpendingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CapUsageCalculatorTest {

    private val calc = CapUsageCalculator()
    private val today = LocalDate.of(2026, 5, 20)
    private val quarterKey = com.travelbenefits.app.domain.model.Quarters.keyFor(today)

    private fun rotatingCard(id: Long = 1) = Fixtures.card(
        id,
        rates = listOf(RewardRate(SpendingCategory.DINING, 5.0, capUsd = 1_500, capPeriod = CapPeriod.QUARTERLY)),
        rotating = RotatingSelection(id, quarterKey, listOf(SpendingCategory.GROCERIES), activated = true),
        rotatingCapUsd = 1_500,
    )

    @Test
    fun `a manual override replaces the transaction figure and is not recorded twice`() {
        // Regression: `manual[key]?.let { ...; return@let }` bound the label to the INNER let,
        // so an override was recorded and then the transaction figure was recorded too.
        val card = rotatingCard()
        val txns = listOf(Fixtures.txn("t1", 1, 400.0, today.toEpochDay(), SpendingCategory.GROCERIES))
        val usage = calc.compute(
            cards = listOf(card),
            transactions = txns,
            overrides = mapOf(1L to mapOf("rotating:$quarterKey" to 900.0)),
            today = today,
        )
        val rotatingRows = usage.filter { it.capKey == "rotating:$quarterKey" }
        assertEquals("exactly one row per cap key", 1, rotatingRows.size)
        assertEquals(900.0, rotatingRows.single().spentUsd, 0.0001)
        assertEquals("your entry", rotatingRows.single().source)
    }

    @Test
    fun `a category override also wins outright`() {
        val card = rotatingCard()
        val txns = listOf(Fixtures.txn("t1", 1, 250.0, today.toEpochDay(), SpendingCategory.DINING))
        val usage = calc.compute(listOf(card), txns, mapOf(1L to mapOf(SpendingCategory.DINING.name to 1_000.0)), today)
        val row = usage.single { it.capKey == SpendingCategory.DINING.name }
        assertEquals(1_000.0, row.spentUsd, 0.0001)
        assertEquals("your entry", row.source)
    }

    @Test
    fun `without an override the spend is summed from transactions in the period`() {
        val card = rotatingCard()
        val txns = listOf(
            Fixtures.txn("in", 1, 100.0, today.toEpochDay(), SpendingCategory.DINING),
            // Before the quarter started, so out of the window.
            Fixtures.txn("old", 1, 500.0, LocalDate.of(2026, 1, 5).toEpochDay(), SpendingCategory.DINING),
            // Another card's spend must not count against this card's cap.
            Fixtures.txn("other", 2, 300.0, today.toEpochDay(), SpendingCategory.DINING),
            // Pending is not yet posted.
            Fixtures.txn("pending", 1, 70.0, today.toEpochDay(), SpendingCategory.DINING, pending = true),
        )
        val row = calc.compute(listOf(card), txns, emptyMap(), today).single { it.capKey == SpendingCategory.DINING.name }
        assertEquals(100.0, row.spentUsd, 0.0001)
    }

    @Test
    fun `account-year periods anchor on the date the card was opened`() {
        val openedMarch = LocalDate.of(2020, 3, 10).toEpochDay()
        val start = calc.periodStart(CapPeriod.ACCOUNT_YEAR, today, openedMarch)
        assertEquals(LocalDate.of(2026, 3, 10).toEpochDay(), start)

        // Opened later in the year than today: the current account year began last year.
        val openedNovember = LocalDate.of(2020, 11, 2).toEpochDay()
        assertEquals(LocalDate.of(2025, 11, 2).toEpochDay(), calc.periodStart(CapPeriod.ACCOUNT_YEAR, today, openedNovember))

        // Unknown open date falls back to the calendar year rather than inventing one.
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), calc.periodStart(CapPeriod.ACCOUNT_YEAR, today, null))
    }

    @Test
    fun `statement cycle is approximated by the calendar month and monthly matches it`() {
        val first = LocalDate.of(2026, 5, 1).toEpochDay()
        assertEquals(first, calc.periodStart(CapPeriod.STATEMENT_CYCLE, today, null))
        assertEquals(first, calc.periodStart(CapPeriod.MONTHLY, today, null))
    }
}

class SpendAnalyzerTest {

    private val analyzer = SpendAnalyzer(RecommendationEngine())

    private fun account(id: String, cardId: Long?) = PlaidAccount(id, "item", "Account", null, "1234", "credit", "credit card", cardId)

    /** A day that is inside SpendPeriod.THIS_MONTH regardless of when the test runs. */
    private val today = LocalDate.now()

    @Test
    fun `spend on a weaker card is reported as missed value against the best card`() {
        val weak = Fixtures.card(1, base = 1.0) // 1% on everything
        val strong = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.DINING, 4.0)), base = 1.0)
        val txns = listOf(Fixtures.txn("t1", 1, 1_000.0, today.toEpochDay(), SpendingCategory.DINING))

        val report = analyzer.analyze(txns, listOf(account("acct", 1)), listOf(weak, strong), SpendPeriod.THIS_MONTH)
        val dining = report.categories.single { it.category == SpendingCategory.DINING }

        assertEquals(1_000.0, dining.spendUsd, 0.0001)
        assertEquals(strong.walletCard.id, dining.bestCard?.walletCard?.id)
        assertEquals("$1,000 went on a card that is not the best one", 1_000.0, dining.misroutedUsd, 0.0001)
        // 1% earned versus 4% available on $1,000 = $30 left on the table.
        assertEquals(10.0, dining.valueEarnedUsd, 0.0001)
        assertEquals(40.0, dining.valueIfBestUsd, 0.0001)
        assertEquals(30.0, dining.missedUsd, 0.0001)
    }

    @Test
    fun `spend already on the best card is not counted as misrouted`() {
        val weak = Fixtures.card(1, base = 1.0)
        val strong = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.DINING, 4.0)), base = 1.0)
        val txns = listOf(Fixtures.txn("t1", 2, 500.0, today.toEpochDay(), SpendingCategory.DINING))
        val dining = analyzer.analyze(txns, listOf(account("acct", 2)), listOf(weak, strong), SpendPeriod.THIS_MONTH)
            .categories.single { it.category == SpendingCategory.DINING }
        assertEquals(0.0, dining.misroutedUsd, 0.0001)
        assertEquals(0.0, dining.missedUsd, 0.0001)
    }

    @Test
    fun `pending, refunds and uncategorised transactions are excluded from the totals`() {
        val card = Fixtures.card(1, base = 1.0)
        val txns = listOf(
            Fixtures.txn("ok", 1, 100.0, today.toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("pending", 1, 999.0, today.toEpochDay(), SpendingCategory.DINING, pending = true),
            Fixtures.txn("refund", 1, -50.0, today.toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("uncategorised", 1, 400.0, today.toEpochDay(), null),
        )
        val report = analyzer.analyze(txns, listOf(account("acct", 1)), listOf(card), SpendPeriod.THIS_MONTH)
        assertEquals(1, report.transactionCount)
        assertEquals(100.0, report.totalSpendUsd, 0.0001)
    }

    @Test
    fun `spend on an unmapped account is separated out instead of silently attributed`() {
        val card = Fixtures.card(1, base = 1.0)
        val txns = listOf(
            Fixtures.txn("mapped", 1, 100.0, today.toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("unmapped", null, 250.0, today.toEpochDay(), SpendingCategory.DINING, accountId = "other"),
        )
        val report = analyzer.analyze(txns, listOf(account("acct", 1), account("other", null)), listOf(card), SpendPeriod.THIS_MONTH)
        assertEquals(350.0, report.totalSpendUsd, 0.0001)
        assertEquals(100.0, report.mappedSpendUsd, 0.0001)
        assertEquals(250.0, report.unmappedSpendUsd, 0.0001)
        val labels = report.categories.single().byCard.map { it.accountLabel }
        assertTrue("the unmapped row is labelled, not dropped", labels.any { it.contains("Unmapped") || it.contains("Account") })
    }

    @Test
    fun `welcome-bonus progress counts only posted spend on that card`() {
        val from = today.minusDays(30).toEpochDay()
        val txns = listOf(
            Fixtures.txn("a", 1, 500.0, today.toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("b", 1, 300.0, today.minusDays(60).toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("c", 2, 900.0, today.toEpochDay(), SpendingCategory.DINING),
            Fixtures.txn("d", 1, 100.0, today.toEpochDay(), SpendingCategory.DINING, pending = true),
        )
        assertEquals(500.0, analyzer.spendSince(txns, walletCardId = 1, fromEpochDay = from), 0.0001)
    }

    @Test
    fun `a discontinued card is never named as the best card`() {
        val dead = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 10.0)), discontinued = true)
        val alive = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.DINING, 2.0)))
        val txns = listOf(Fixtures.txn("t1", 2, 100.0, today.toEpochDay(), SpendingCategory.DINING))
        val dining = analyzer.analyze(txns, listOf(account("acct", 2)), listOf(dead, alive), SpendPeriod.THIS_MONTH)
            .categories.single()
        assertNotNull(dining.bestCard)
        assertEquals(alive.walletCard.id, dining.bestCard?.walletCard?.id)
    }
}
