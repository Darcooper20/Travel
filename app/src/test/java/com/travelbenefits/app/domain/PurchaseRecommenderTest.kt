package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.CapPeriod
import com.travelbenefits.app.domain.model.CapUsage
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.PurchaseQuery
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.RotatingSelection
import com.travelbenefits.app.domain.model.RuleProvenance
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.WalletCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseRecommenderTest {

    private val engine = PurchaseRecommender()
    private val fresh = RuleProvenance("https://example.test", "2026-09-01")

    private fun wallet(id: Long) = WalletCard(id, null, "x", null, 0, null)

    private fun cashCard(id: Long, rates: List<RewardRate>, base: Double = 1.0, fee: Double? = 3.0, rotatingCap: Int? = null) = ResolvedWalletCard.Catalog(
        wallet(id),
        CardCatalogEntry(
            id = "card$id", displayName = "Card $id", issuer = "Test", network = "Visa", annualFeeUsd = 0, rewardCurrency = RewardCurrency.CASH_BACK,
            baseMultiplier = base, categoryRates = rates, dataAsOf = "test", provenance = fresh, foreignTransactionFeePct = fee, rotatingCapUsd = rotatingCap,
        ),
    )

    @Test
    fun `cap reached partway through a purchase splits the rate`() {
        // 5% dining capped at $1,500/quarter with $1,450 already spent: $50 at 5% + $50 at 1% = $3, not $5.
        val card = cashCard(1, listOf(RewardRate(SpendingCategory.DINING, 5.0, capUsd = 1_500, capPeriod = CapPeriod.QUARTERLY, fallbackMultiplier = 1.0)))
        val rec = engine.recommend(
            PurchaseQuery(merchant = "Diner", category = SpendingCategory.DINING, amountUsd = 100.0),
            listOf(card),
            capUsage = listOf(CapUsage(1, SpendingCategory.DINING.name, 1_450.0, "test")),
        )
        val option = rec.options.single()
        assertEquals(3.0, option.rewardsEarned, 0.0001)
        assertEquals(3.0, option.rewardsValueUsd, 0.0001)
        assertEquals(50.0, option.amountAtHeadlineRate, 0.0001)
        assertEquals("5% → 1%", option.effectiveRateLabel)
    }

    @Test
    fun `shared cap group counts spend from sibling categories`() {
        val card = cashCard(
            1,
            listOf(
                RewardRate(SpendingCategory.OFFICE_SUPPLIES, 5.0, capUsd = 25_000, capPeriod = CapPeriod.ACCOUNT_YEAR, capGroup = "ink5", fallbackMultiplier = 1.0),
                RewardRate(SpendingCategory.PHONE_INTERNET_CABLE, 5.0, capUsd = 25_000, capPeriod = CapPeriod.ACCOUNT_YEAR, capGroup = "ink5", fallbackMultiplier = 1.0),
            ),
        )
        val rec = engine.recommend(
            PurchaseQuery(null, SpendingCategory.PHONE_INTERNET_CABLE, 1_000.0),
            listOf(card),
            capUsage = listOf(CapUsage(1, "ink5", 25_000.0, "test")),
        )
        assertEquals(10.0, rec.options.single().rewardsEarned, 0.0001) // all at the 1% fallback
    }

    @Test
    fun `unknown cap usage assumes unused but lowers confidence`() {
        val card = cashCard(1, listOf(RewardRate(SpendingCategory.GROCERIES, 6.0, capUsd = 6_000, capPeriod = CapPeriod.ANNUAL, fallbackMultiplier = 1.0)))
        val option = engine.recommend(PurchaseQuery(null, SpendingCategory.GROCERIES, 100.0), listOf(card)).options.single()
        assertEquals(6.0, option.rewardsEarned, 0.0001)
        assertEquals(Confidence.MEDIUM, option.confidence)
        assertTrue(option.conditions.any { it.contains("unknown") })
    }

    @Test
    fun `portal-only rate does not apply to a direct booking`() {
        val card = cashCard(1, listOf(RewardRate(SpendingCategory.HOTELS, 10.0, channel = BookingChannel.ISSUER_PORTAL)), base = 2.0)
        val direct = engine.recommend(PurchaseQuery(null, SpendingCategory.HOTELS, 100.0, channel = BookingChannel.DIRECT), listOf(card)).options.single()
        val portal = engine.recommend(PurchaseQuery(null, SpendingCategory.HOTELS, 100.0, channel = BookingChannel.ISSUER_PORTAL), listOf(card)).options.single()
        assertEquals(2.0, direct.rewardsEarned, 0.0001)
        assertEquals(10.0, portal.rewardsEarned, 0.0001)
    }

    @Test
    fun `foreign fee reduces net value and unknown fee is flagged`() {
        val withFee = cashCard(1, emptyList(), base = 2.0, fee = 3.0)
        val unknownFee = cashCard(2, emptyList(), base = 2.0, fee = null)
        val rec = engine.recommend(PurchaseQuery(null, SpendingCategory.OTHER, 100.0, isForeign = true), listOf(withFee, unknownFee))
        val a = rec.options.first { it.card.walletCard.id == 1L }
        val b = rec.options.first { it.card.walletCard.id == 2L }
        assertEquals(3.0, a.foreignFeeUsd!!, 0.0001)
        assertEquals(-1.0, a.netValueUsd, 0.0001)
        assertNull(b.foreignFeeUsd)
        assertTrue(b.warnings.any { it.contains("not verified") })
    }

    @Test
    fun `inactive rotating category is low confidence and warned`() {
        val card = cashCard(1, emptyList(), rotatingCap = 1_500).copy(rotating = RotatingSelection(1, "2026-Q3", listOf(SpendingCategory.GAS_EV), activated = false))
        val option = engine.recommend(PurchaseQuery(null, SpendingCategory.GAS_EV, 100.0), listOf(card)).options.single()
        assertEquals(Confidence.LOW, option.confidence)
        assertTrue(option.warnings.any { it.contains("activation") })
    }

    @Test
    fun `welcome bonus is a note not value`() {
        val card = cashCard(1, emptyList()).let { it.copy(walletCard = it.walletCard.copy(bonusSpendRequiredUsd = 4_000, bonusSpendToDateUsd = 1_000)) }
        val option = engine.recommend(PurchaseQuery(null, SpendingCategory.OTHER, 100.0), listOf(card)).options.single()
        assertEquals(1.0, option.netValueUsd, 0.0001)
        assertNotNull(option.thresholdNotes.firstOrNull { it.contains("3,000") })
    }

    @Test
    fun `unknown category yields a warning and base rates`() {
        val card = cashCard(1, listOf(RewardRate(SpendingCategory.DINING, 3.0)))
        val rec = engine.recommend(PurchaseQuery("Mystery Shop", null, 50.0), listOf(card))
        assertNotNull(rec.categoryWarning)
        assertEquals(0.5, rec.options.single().rewardsEarned, 0.0001)
    }
}
