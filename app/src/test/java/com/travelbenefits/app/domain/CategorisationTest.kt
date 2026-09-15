package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.SpendingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Categorisation decides which card the spend analysis says you "should"
 * have used, so a wrong bucket turns into wrong money advice.
 */
class PlaidCategoryMapperTest {

    @Test
    fun `a specific Plaid category wins over the merchant name`() {
        // Plaid saw the merchant's own coding; a keyword guess must not override it.
        assertEquals(
            SpendingCategory.GROCERIES,
            PlaidCategoryMapper.map("FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES", "Starbucks Market"),
        )
    }

    @Test
    fun `the merchant name rescues spend Plaid dumps in a vague bucket`() {
        // Regression: map() used to ignore merchantOrName entirely, so all of these
        // became OTHER and never counted toward dining, travel or transit.
        assertEquals(
            SpendingCategory.DINING,
            PlaidCategoryMapper.map("GENERAL_MERCHANDISE", "GENERAL_MERCHANDISE_OTHER", "Joe's Pizza"),
        )
        assertEquals(SpendingCategory.HOTELS, PlaidCategoryMapper.map(null, null, "Hyatt Regency Austin"))
        assertEquals(SpendingCategory.AIRFARE, PlaidCategoryMapper.map(null, null, "DELTA AIR LINES"))
    }

    @Test
    fun `explicit non-spend is never turned into spend by its name`() {
        // "Transfer to Marriott Bonvoy savings" must not become a hotel purchase.
        assertNull(PlaidCategoryMapper.map("TRANSFER_OUT", null, "Transfer to Marriott savings"))
        assertNull(PlaidCategoryMapper.map("LOAN_PAYMENTS", null, "Delta Amex payment"))
        assertNull(PlaidCategoryMapper.map("INCOME", null, "Hilton Worldwide payroll"))
    }

    @Test
    fun `unknown input falls back to OTHER rather than null`() {
        // null means "not spend"; an unrecognised merchant is still spend.
        assertEquals(SpendingCategory.OTHER, PlaidCategoryMapper.map(null, null, "ZZQ Trading Ltd"))
        assertEquals(SpendingCategory.OTHER, PlaidCategoryMapper.map("SOMETHING_NEW", null, null))
    }
}

class MerchantClassifierTest {

    private val classifier = MerchantClassifier()

    @Test
    fun `your own history outranks the keyword table`() {
        // The user's card actually codes "Sunoco" as groceries; trust that over the keyword.
        val history = listOf(
            Fixtures.txn("1", 1, 20.0, 0, SpendingCategory.GROCERIES, merchant = "Sunoco Food Mart"),
            Fixtures.txn("2", 1, 25.0, 0, SpendingCategory.GROCERIES, merchant = "Sunoco Food Mart"),
        )
        val result = classifier.classify("Sunoco", history)
        assertEquals(SpendingCategory.GROCERIES, result.category)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertTrue(result.source.contains("history"))
    }

    @Test
    fun `keywords match at the end of a name, not only mid-string`() {
        // "bar " and "max " are written with trailing spaces in the table.
        assertEquals(SpendingCategory.DINING, classifier.classify("The Corner Bar").category)
        assertEquals(SpendingCategory.STREAMING, classifier.classify("Max").category)
    }

    @Test
    fun `an unknown merchant is admitted as unknown, never guessed`() {
        val result = classifier.classify("Qxr Holdings")
        assertNull(result.category)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun `blank input is not a category`() {
        assertNull(classifier.classify("   ").category)
        assertNull(MerchantKeywords.categoryFor(null))
    }

    @Test
    fun `the shared table is the one both paths use`() {
        // Guards against the two classifiers drifting apart again.
        assertEquals(MerchantKeywords.categoryFor("Starbucks"), classifier.classify("Starbucks").category)
        assertEquals(MerchantKeywords.categoryFor("Starbucks"), PlaidCategoryMapper.map(null, null, "Starbucks"))
    }
}
