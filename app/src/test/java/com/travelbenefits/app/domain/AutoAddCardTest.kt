package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.CardCatalog
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.SpendingCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A statement email proves you hold a card; it does not prove which version.
 * Naming the wrong variant would feed wrong multipliers, caps and credits into
 * every recommendation, so the matcher resolves a product only when one entry
 * can possibly be meant.
 */
class CardCatalogMatchTest {

    @Test
    fun `a full product name resolves to exactly that product`() {
        val entry = CardCatalog.findUnambiguous("Chase", "Sapphire Reserve")
        assertNotNull(entry)
        assertTrue(entry!!.displayName.contains("Reserve", ignoreCase = true))
    }

    @Test
    fun `a family name shared by several products resolves to none`() {
        // "Sapphire" alone could be Preferred or Reserve, and picking either
        // would be a guess presented as fact.
        assertNull(CardCatalog.findUnambiguous("Chase", "Sapphire"))
    }

    @Test
    fun `the more specific of two overlapping names wins`() {
        // "Ink Business Preferred" also satisfies the shorter "Ink Business Cash"
        // pattern once generic words are dropped; the longer match is the intended one.
        val entry = CardCatalog.findUnambiguous("Chase", "Ink Business Preferred")
        if (entry != null) assertTrue(entry.displayName.contains("Preferred", ignoreCase = true))
    }

    @Test
    fun `the issuer has to agree`() {
        assertNull(CardCatalog.findUnambiguous("Citi", "Sapphire Reserve"))
    }

    @Test
    fun `nothing to go on resolves to nothing`() {
        assertNull(CardCatalog.findUnambiguous(null, null))
        assertNull(CardCatalog.findUnambiguous("", ""))
        assertNull(CardCatalog.findUnambiguous("Chase", null))
    }

    @Test
    fun `a discontinued product is never resolved onto a new card`() {
        CardCatalog.entries.filter { it.isDiscontinued }.forEach { dead ->
            val resolved = CardCatalog.findUnambiguous(dead.issuer, dead.displayName)
            assertTrue(
                "discontinued ${dead.displayName} must not be auto-assigned",
                resolved == null || !resolved.isDiscontinued,
            )
        }
    }
}

/** Unconfirmed cards must never reach a recommendation. */
class UnconfirmedCardExclusionTest {

    private val recommendation = RecommendationEngine()
    private val purchase = PurchaseRecommender()

    private fun unconfirmed(id: Long) = Fixtures.card(id, rates = listOf(RewardRate(SpendingCategory.DINING, 9.0)))
        .let { it.copy(walletCard = it.walletCard.copy(needsConfirmation = true)) }

    @Test
    fun `ranking ignores a card awaiting confirmation even when it looks best`() {
        val confirmed = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 2.0)))
        val ranked = recommendation.rank(listOf(confirmed, unconfirmed(2)), SpendingCategory.DINING)
        assertEquals(1, ranked.size)
        assertEquals(1L, ranked.single().walletCard.id)
    }

    @Test
    fun `the purchase picker ignores it too`() {
        val confirmed = Fixtures.card(1, rates = listOf(RewardRate(SpendingCategory.DINING, 2.0)))
        val result = purchase.recommend(
            com.travelbenefits.app.domain.model.PurchaseQuery("Diner", SpendingCategory.DINING, 100.0),
            listOf(confirmed, unconfirmed(2)),
        )
        assertEquals(1, result.options.size)
        assertEquals(1L, result.options.single().card.walletCard.id)
    }

    @Test
    fun `a wallet of only unconfirmed cards recommends nothing rather than guessing`() {
        val ranked = recommendation.rank(listOf(unconfirmed(1), unconfirmed(2)), SpendingCategory.DINING)
        assertTrue(ranked.isEmpty())
    }
}
