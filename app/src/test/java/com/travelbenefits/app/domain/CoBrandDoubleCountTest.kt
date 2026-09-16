package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RewardCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A co-brand card earns straight into the programme, so the "balance" on a
 * United MileagePlus card and the MileagePlus account are one pot of miles.
 * The portfolio total used to add both, roughly doubling that currency.
 */
class CoBrandDoubleCountTest {

    private val insights = LoyaltyInsights()

    private fun unitedCard(id: Long = 1, miles: Long = 60_000) =
        Fixtures.card(id, currency = RewardCurrency.UNITED_MILEAGEPLUS)
            .let { it.copy(walletCard = it.walletCard.copy(rewardsBalance = miles)) }

    private fun unitedAccount(miles: Long = 60_000) =
        Fixtures.account(program = LoyaltyProgram.UNITED_MILEAGEPLUS, pointsNumeric = miles)

    @Test
    fun `the same miles are counted once, not twice`() {
        val card = unitedCard()
        val account = unitedAccount()
        val accountValue = insights.estimatedValueUsd(account)!!

        // What the total used to be: programme plus card, for one pot of miles.
        val doubled = insights.portfolioValueUsd(listOf(account)) + insights.cardRewardsValueUsd(listOf(card))
        val correct = insights.portfolioValueUsd(listOf(account)) + insights.cardRewardsValueUsd(listOf(card), listOf(account))

        assertEquals(accountValue, correct, 0.0001)
        assertTrue("the old total really was about double", doubled > correct * 1.9)
    }

    @Test
    fun `a co-brand card still counts when no account is tracked`() {
        // Nothing is lost by preferring the account: with no account, the card is the only record.
        val card = unitedCard(miles = 25_000)
        val value = insights.cardRewardsValueUsd(listOf(card), emptyList())
        assertEquals(card.rewardsValueUsd!!, value, 0.0001)
    }

    @Test
    fun `an account with no balance does not suppress the card`() {
        val account = Fixtures.account(program = LoyaltyProgram.UNITED_MILEAGEPLUS, pointsNumeric = null)
        val card = unitedCard()
        assertNull(insights.isCoBrandDuplicateOf(card, listOf(account)))
        assertEquals(card.rewardsValueUsd!!, insights.cardRewardsValueUsd(listOf(card), listOf(account)), 0.0001)
    }

    @Test
    fun `bank currencies are a genuinely separate pot and keep counting`() {
        // Ultimate Rewards are not any programme's balance, so both must count.
        val bankCard = Fixtures.card(2, currency = RewardCurrency.CHASE_UR)
            .let { it.copy(walletCard = it.walletCard.copy(rewardsBalance = 80_000)) }
        val account = unitedAccount()
        assertNull(insights.isCoBrandDuplicateOf(bankCard, listOf(account)))
        assertEquals(bankCard.rewardsValueUsd!!, insights.cardRewardsValueUsd(listOf(bankCard), listOf(account)), 0.0001)
    }

    @Test
    fun `the duplicate is reported against the programme that owns it`() {
        val duplicate = insights.isCoBrandDuplicateOf(unitedCard(), listOf(unitedAccount()))
        assertNotNull(duplicate)
        assertEquals(LoyaltyProgram.UNITED_MILEAGEPLUS, duplicate!!.program)
    }

    @Test
    fun `a card with no balance is never a duplicate`() {
        val empty = Fixtures.card(3, currency = RewardCurrency.UNITED_MILEAGEPLUS)
        assertNull(insights.isCoBrandDuplicateOf(empty, listOf(unitedAccount())))
    }

    @Test
    fun `every co-brand currency in the catalog is covered, not just United`() {
        com.travelbenefits.app.data.catalog.TransferPartnerCatalog.nativeCurrency.forEach { (program, currency) ->
            val card = Fixtures.card(9, currency = currency)
                .let { it.copy(walletCard = it.walletCard.copy(rewardsBalance = 1_000)) }
            val account = Fixtures.account(program = program, pointsNumeric = 1_000)
            assertNotNull(
                "$program and its own currency must be recognised as one pot",
                insights.isCoBrandDuplicateOf(card, listOf(account)),
            )
        }
    }
}
