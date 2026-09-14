package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.MerchantOffer
import com.travelbenefits.app.domain.model.PurchaseQuery
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RuleProvenance
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import com.travelbenefits.app.domain.model.WalletCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StageDTest {
    private val engine = PurchaseRecommender()
    private val card = ResolvedWalletCard.Catalog(
        WalletCard(1, null, "x", null, 0, null),
        CardCatalogEntry("c", "Card", "Test", "Visa", 0, RewardCurrency.CASH_BACK, 2.0, emptyList(), dataAsOf = "t", provenance = RuleProvenance(null, "2026-09-01"), foreignTransactionFeePct = 0.0),
    )

    @Test
    fun `enrolled card offer adds value, unenrolled and portal are notes only`() {
        val offers = listOf(
            MerchantOffer(1, 1, "Whole Foods", "\$10 back on \$50+", 10.0, null, 50.0, null, true, MerchantOffer.Kind.CARD_OFFER, "manual", null),
            MerchantOffer(2, 1, "Whole Foods", "5% back", null, 5.0, null, null, false, MerchantOffer.Kind.CARD_OFFER, "manual", null),
            MerchantOffer(3, null, "Whole Foods", "Rakuten 2%", null, 2.0, null, null, true, MerchantOffer.Kind.PORTAL, "manual", null),
        )
        val opt = engine.recommend(PurchaseQuery("Whole Foods", SpendingCategory.GROCERIES, 100.0), listOf(card), offers = offers).options.single()
        assertEquals(2.0 + 10.0, opt.netValueUsd, 0.0001)
        assertTrue(opt.thresholdNotes.any { it.contains("not enrolled") })
        assertTrue(opt.thresholdNotes.any { it.contains("Portal") })
    }

    @Test
    fun `offer below minimum spend is not counted`() {
        val offers = listOf(MerchantOffer(1, 1, "Target", "\$20 back on \$100+", 20.0, null, 100.0, null, true, MerchantOffer.Kind.CARD_OFFER, "manual", null))
        val opt = engine.recommend(PurchaseQuery("Target", SpendingCategory.OTHER, 60.0), listOf(card), offers = offers).options.single()
        assertEquals(1.2, opt.netValueUsd, 0.0001)
    }

    @Test
    fun `certificate matcher rejects trips after expiry and prefers same program`() {
        val today = LocalDate.of(2026, 9, 14)
        val cert = BenefitItem(1, BenefitKind.FREE_NIGHT, "Free night 35k", LoyaltyProgram.WORLD_OF_HYATT, null, 250.0, today.plusDays(60).toEpochDay(), null, null, LoyaltyAccountSource.MANUAL, null, 0)
        fun trip(id: Long, start: Long, program: LoyaltyProgram) = Trip(id, TripKind.HOTEL, "Hotel", null, "Stay $id", start, start + 2, null, null, program, false, null, null, null, TripSource.MANUAL, null, null, null, 0, loyaltyNumberState = LoyaltyNumberState.UNKNOWN)
        val late = trip(1, today.plusDays(90).toEpochDay(), LoyaltyProgram.WORLD_OF_HYATT)
        val other = trip(2, today.plusDays(10).toEpochDay(), LoyaltyProgram.MARRIOTT_BONVOY)
        val good = trip(3, today.plusDays(20).toEpochDay(), LoyaltyProgram.WORLD_OF_HYATT)
        val s = CertificateMatcher().suggest(listOf(cert), listOf(late, other, good), today).single()
        assertEquals(3L, s.trip?.id)
        assertEquals(CertificateMatcher.Fit.GOOD, s.fit)
        val onlyLate = CertificateMatcher().suggest(listOf(cert), listOf(late), today).single()
        assertEquals(CertificateMatcher.Fit.NONE, onlyLate.fit)
    }
}
