package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.domain.model.CashVsPointsInput
import com.travelbenefits.app.domain.model.CashVsPointsResult
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TravelPerk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecommendationEngineTest {

    private val engine = RecommendationEngine()

    @Test
    fun `ranking is by value per dollar, best first`() {
        val low = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 1.0)))
        val high = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.DINING, 4.0)))
        val ranked = engine.rank(listOf(low, high), SpendingCategory.DINING)
        assertEquals(listOf(2L, 1L), ranked.map { it.walletCard.id })
    }

    @Test
    fun `a discontinued product sorts last and says why`() {
        // Negative infinity rather than a high score: you cannot spend on a closed product.
        val dead = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 10.0)), discontinued = true)
        val alive = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.DINING, 1.0)))
        val ranked = engine.rank(listOf(dead, alive), SpendingCategory.DINING)
        assertEquals(2L, ranked.first().walletCard.id)
        assertTrue(ranked.last().reason.contains("discontinued"))
    }

    @Test
    fun `an unactivated rotating category is flagged in the reason`() {
        val quarter = com.travelbenefits.app.domain.model.Quarters.keyFor(LocalDate.now())
        val card = Fixtures.card(
            1,
            base = 1.0,
            rotating = RotatingSelection(1, quarter, listOf(SpendingCategory.GROCERIES), activated = false),
        )
        val entry = engine.rank(listOf(card), SpendingCategory.GROCERIES).single()
        assertEquals(5.0, entry.multiplier, 0.0001)
        assertTrue(entry.reason.contains("NOT ACTIVATED"))
    }

    @Test
    fun `a card with no looked-up data is marked an estimate and scores zero`() {
        val custom = com.travelbenefits.app.domain.model.ResolvedWalletCard.Custom(
            com.travelbenefits.app.domain.model.WalletCard(9, null, null, "Mystery Card", 0, null),
            null,
        )
        val entry = engine.rank(listOf(custom), SpendingCategory.DINING).single()
        assertTrue(entry.isEstimate)
        assertEquals(0.0, entry.estimatedValueCentsPerDollar, 0.0001)
    }
}

class PointsOptimizerTest {

    private val optimizer = PointsOptimizer()
    private val program = LoyaltyProgram.MARRIOTT_BONVOY
    private val profile = LoyaltyProgramCatalog.profileFor(program)

    @Test
    fun `award taxes reduce the cash actually saved`() {
        val result = optimizer.cashVsPoints(CashVsPointsInput(program, cashPriceUsd = 500.0, pointsRequired = 50_000, awardTaxesFeesUsd = 100.0))
        // (500 - 100) / 50,000 = 0.8 cents per point.
        assertEquals(0.8, result.centsPerPoint, 0.0001)
    }

    @Test
    fun `a redemption far above the benchmark recommends points`() {
        // 100,000 points for a $4,000 stay is 4 cents each, way above any hotel benchmark.
        val result = optimizer.cashVsPoints(CashVsPointsInput(program, cashPriceUsd = 4_000.0, pointsRequired = 100_000))
        assertTrue("cpp should beat the benchmark", result.centsPerPoint > result.benchmarkCentsPerPoint)
        assertEquals(CashVsPointsResult.Recommendation.USE_POINTS, result.recommendation)
        assertTrue(result.netAdvantageOfPointsUsd > 0)
    }

    @Test
    fun `a poor redemption recommends cash and counts the earning given up`() {
        // 100,000 points for a $200 stay is 0.2 cents each.
        val result = optimizer.cashVsPoints(CashVsPointsInput(program, cashPriceUsd = 200.0, pointsRequired = 100_000))
        assertEquals(CashVsPointsResult.Recommendation.PAY_CASH, result.recommendation)
        assertTrue("paying cash would also earn points, which counts against redeeming", result.forgoneValueUsd > 0)
        assertTrue(result.explanation.contains("paying cash"))
    }

    @Test
    fun `elite tier increases the points a paid stay would have earned`() {
        val base = optimizer.cashVsPoints(CashVsPointsInput(program, 1_000.0, 50_000))
        val elite = optimizer.cashVsPoints(CashVsPointsInput(program, 1_000.0, 50_000, memberTier = profile.tiers.lastOrNull()?.name))
        assertTrue(
            "an elite bonus can only add points, never remove them",
            elite.programPointsForgone >= base.programPointsForgone,
        )
    }

    @Test
    fun `zero points required cannot divide by zero`() {
        val result = optimizer.cashVsPoints(CashVsPointsInput(program, 100.0, 0))
        assertTrue(result.centsPerPoint.isFinite())
    }

    @Test
    fun `transfer options are one per currency and only for real partners`() {
        val currency = TransferPartnerCatalog.partners.first { it.to == program }.from
        val a = Fixtures.card(1, currency = currency)
        val b = Fixtures.card(2, currency = currency) // same currency, must not be listed twice
        val cash = Fixtures.card(3, currency = RewardCurrency.CASH_BACK) // cash back transfers nowhere
        val options = optimizer.transferOptions(program, listOf(a, b, cash))
        assertEquals(1, options.size)
        assertEquals(currency, options.single().partner.from)
    }

    @Test
    fun `earn plan puts cards that cannot reach the program last and explains why`() {
        val currency = TransferPartnerCatalog.partners.first { it.to == program }.from
        val transferable = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.HOTELS, 3.0)), currency = currency)
        val deadEnd = Fixtures.card(2, rates = listOf(RewardRate(SpendingCategory.HOTELS, 9.0)), currency = RewardCurrency.CASH_BACK)
        val plan = optimizer.earnPlan(program, SpendingCategory.HOTELS, listOf(deadEnd, transferable))
        assertEquals(1L, plan.first().walletCard.walletCard.id)
        assertNotNull(plan.first().programPointsPerDollar)
        assertNull(plan.last().programPointsPerDollar)
        assertTrue(plan.last().via.contains("can't be moved"))
    }
}

class CardValueAnalyzerTest {

    private val analyzer = CardValueAnalyzer()
    private val today = LocalDate.of(2026, 5, 20)

    private fun inputs(
        card: com.travelbenefits.app.domain.model.ResolvedWalletCard,
        transactions: List<com.travelbenefits.app.domain.model.SpendTransaction> = emptyList(),
        perkValuesUsd: Map<TravelPerk.Kind, Double> = emptyMap(),
        authorizedUsers: Int = 0,
        otherCardsEarningSameCurrency: Int = 1,
        upcomingTripsPaidWithCard: Int = 0,
    ) = CardValueAnalyzer.Inputs(
        card = card,
        transactions = transactions,
        ledger = emptyList(),
        credits = emptyList(),
        certificates = emptyList(),
        perkValuesUsd = perkValuesUsd,
        authorizedUsers = authorizedUsers,
        valuations = emptyMap(),
        bonusPoints = null,
        today = today,
        otherCardsEarningSameCurrency = otherCardsEarningSameCurrency,
        upcomingTripsPaidWithCard = upcomingTripsPaidWithCard,
    )

    @Test
    fun `the annual fee is subtracted, so an unused fee card shows a loss`() {
        val card = Fixtures.card(1, annualFeeUsd = 550)
        val analysis = analyzer.analyze(inputs(card))!!
        assertEquals(550.0, analysis.annualFeeUsd.toDouble(), 0.0001)
        assertEquals(-550.0, analysis.realizedNetUsd, 0.0001)
        assertEquals(-550.0, analysis.forecastNetUsd, 0.0001)
    }

    @Test
    fun `rewards are counted from mapped transactions only, and coverage says when there are none`() {
        val card = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 4.0)), currency = RewardCurrency.CASH_BACK)
        val bare = analyzer.analyze(inputs(card))!!
        assertTrue(bare.dataCoverage.contains("No transactions"))

        val withSpend = analyzer.analyze(
            inputs(card, transactions = listOf(Fixtures.txn("t", 1, 1_000.0, today.toEpochDay(), SpendingCategory.DINING))),
        )!!
        // 4% of $1,000 valued at 1 cent per "point" = $40.
        val rewardLine = withSpend.realizedLines.first { it.label.startsWith("Rewards") }
        assertEquals(40.0, rewardLine.amountUsd, 0.0001)
        assertTrue("catalog rates are estimates, not the issuer's posting", rewardLine.isEstimate)
        assertTrue(withSpend.dataCoverage.contains("1 transactions"))
    }

    @Test
    fun `transactions outside the last 12 months and other cards are excluded`() {
        val card = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 4.0)))
        val analysis = analyzer.analyze(
            inputs(
                card,
                transactions = listOf(
                    Fixtures.txn("old", 1, 1_000.0, today.minusYears(2).toEpochDay(), SpendingCategory.DINING),
                    Fixtures.txn("other", 2, 1_000.0, today.toEpochDay(), SpendingCategory.DINING),
                ),
            ),
        )!!
        assertEquals(0.0, analysis.realizedLines.first { it.label.startsWith("Rewards") }.amountUsd, 0.0001)
    }

    @Test
    fun `perks count only once the user has put a number on them`() {
        val card = Fixtures.card(1, perks = listOf(TravelPerk(TravelPerk.Kind.LOUNGE, "Lounge access", null, null)))
        val unvalued = analyzer.analyze(inputs(card))!!
        assertEquals(0.0, unvalued.realizedLines.first { it.label.startsWith("Perks") }.amountUsd, 0.0001)

        val valued = analyzer.analyze(inputs(card, perkValuesUsd = mapOf(TravelPerk.Kind.LOUNGE to 200.0)))!!
        assertEquals(200.0, valued.realizedLines.first { it.label.startsWith("Perks") }.amountUsd, 0.0001)
    }

    @Test
    fun `an unverified authorized-user fee is recorded as unknown, never as zero cost`() {
        val card = Fixtures.card(1, authorizedUserFeeUsd = null)
        val analysis = analyzer.analyze(inputs(card, authorizedUsers = 2))!!
        assertEquals(0.0, analysis.authorizedUserFeesUsd, 0.0001)
        assertTrue(analysis.assumptions.any { it.contains("Authorized-user fee not verified") })
    }

    @Test
    fun `closing your only card in a points currency carries a forfeiture warning`() {
        val card = Fixtures.card(1, currency = RewardCurrency.AMEX_MR)
        val analysis = analyzer.analyze(inputs(card, otherCardsEarningSameCurrency = 0))!!
        val cancel = analysis.scenarios.single { it.name == "Cancel" }
        assertTrue(cancel.warnings.any { it.contains("only card earning") })
    }

    @Test
    fun `cancel is always worth zero and keep uses the forecast`() {
        val card = Fixtures.card(1, annualFeeUsd = 95)
        val analysis = analyzer.analyze(inputs(card))!!
        assertEquals(0.0, analysis.scenarios.single { it.name == "Cancel" }.netUsd, 0.0001)
        assertEquals(analysis.forecastNetUsd, analysis.scenarios.single { it.name == "Keep" }.netUsd, 0.0001)
        assertTrue(analysis.scenarios.all { it.name == "Keep" || it.warnings.any { w -> w.contains("Product-change") } })
    }

    @Test
    fun `the renewal date is the next anniversary, never one in the past`() {
        val card = Fixtures.card(1, dateOpenedEpochDay = LocalDate.of(2020, 3, 10).toEpochDay())
        val analysis = analyzer.analyze(inputs(card))!!
        assertEquals(LocalDate.of(2027, 3, 10).toEpochDay(), analysis.renewalEpochDay)
    }

    @Test
    fun `duplicate perks and credits across the household are listed with who holds them`() {
        val perk = TravelPerk(TravelPerk.Kind.LOUNGE, "Lounge access", null, null)
        val a = Fixtures.card(1, perks = listOf(perk))
        val b = Fixtures.card(2, perks = listOf(perk), memberName = "Sam")
        val duplicates = analyzer.duplicates(listOf(a, b))
        assertTrue(duplicates.isNotEmpty())
        assertTrue(duplicates.first().cards.any { it.contains("Sam") })
    }

    @Test
    fun `a card held once is not a duplicate`() {
        val a = Fixtures.card(1, perks = listOf(TravelPerk(TravelPerk.Kind.LOUNGE, "Lounge", null, null)))
        assertTrue(analyzer.duplicates(listOf(a)).isEmpty())
    }
}

class ProtectionGuideTest {

    @Test
    fun `each protection kind the app offers has a checklist with documents and steps`() {
        val covered = listOf(
            TravelPerk.Kind.TRIP_DELAY,
            TravelPerk.Kind.TRIP_CANCELLATION,
            TravelPerk.Kind.BAGGAGE_DELAY,
            TravelPerk.Kind.RENTAL_CDW,
            TravelPerk.Kind.TRAVEL_ACCIDENT,
        )
        covered.forEach { kind ->
            val guide = ProtectionGuide.guide(kind)
            assertNotNull("no guide for $kind", guide)
            assertTrue("$kind has no documents", guide!!.documents.isNotEmpty())
            assertTrue("$kind has no steps", guide.steps.isNotEmpty())
            assertTrue("$kind has no deadline wording", guide.typicalDeadline.isNotBlank())
        }
    }

    @Test
    fun `kinds that are not protections return nothing rather than generic advice`() {
        assertNull(ProtectionGuide.guide(TravelPerk.Kind.LOUNGE))
        assertNull(ProtectionGuide.guide(TravelPerk.Kind.OTHER))
    }

    @Test
    fun `deadlines are described as typical, so nobody reads them as the policy`() {
        val guide = ProtectionGuide.guide(TravelPerk.Kind.RENTAL_CDW)!!
        assertTrue(guide.typicalDeadline.contains("typical"))
    }
}
