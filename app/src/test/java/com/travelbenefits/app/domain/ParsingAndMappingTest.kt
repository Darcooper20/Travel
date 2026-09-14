package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CardCredit
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.RuleProvenance
import com.travelbenefits.app.domain.model.SpendingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ParsingAndMappingTest {

    @Test
    fun `parsePoints handles thousands separators, k suffix and plain numbers`() {
        assertEquals(42_500L, LoyaltyAccount.parsePoints("42,500 points"))
        assertEquals(18_200L, LoyaltyAccount.parsePoints("18.2k miles"))
        assertEquals(1_200L, LoyaltyAccount.parsePoints("1200"))
        assertNull(LoyaltyAccount.parsePoints("no numbers here"))
        assertNull(LoyaltyAccount.parsePoints(null))
    }

    @Test
    fun `plaid categories map to app categories and non-spend maps to null`() {
        assertEquals(SpendingCategory.DINING, PlaidCategoryMapper.map("FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT", null))
        assertEquals(SpendingCategory.GROCERIES, PlaidCategoryMapper.map("FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES", null))
        assertEquals(SpendingCategory.HOTELS, PlaidCategoryMapper.map("TRAVEL", "TRAVEL_LODGING", null))
        assertNull(PlaidCategoryMapper.map("TRANSFER_OUT", "TRANSFER_OUT_ACCOUNT_TRANSFER", null))
        assertNull(PlaidCategoryMapper.map("LOAN_PAYMENTS", "LOAN_PAYMENTS_CREDIT_CARD_PAYMENT", null))
        assertEquals(SpendingCategory.OTHER, PlaidCategoryMapper.map(null, null, "Unknown"))
    }

    @Test
    fun `monthly sublimit parses from frequency text`() {
        assertEquals(10, CardCredit("x", 120, "monthly (\$10/mo)", "").monthlySublimitUsd)
        assertEquals(15, CardCredit("x", 200, "annual (\$15-20/mo)", "").monthlySublimitUsd)
        assertNull(CardCredit("x", 300, "annual", "").monthlySublimitUsd)
    }

    @Test
    fun `provenance freshness ages out`() {
        val today = LocalDate.of(2026, 9, 14).toEpochDay()
        assertEquals(RuleProvenance.Freshness.VERIFIED, RuleProvenance(null, "2026-08-01").freshness(today))
        assertEquals(RuleProvenance.Freshness.AGING, RuleProvenance(null, "2025-12-01").freshness(today))
        assertEquals(RuleProvenance.Freshness.STALE, RuleProvenance(null, "2025-03-01").freshness(today))
        assertEquals(RuleProvenance.Freshness.STALE, RuleProvenance(null, "not-a-date").freshness(today))
    }
}
