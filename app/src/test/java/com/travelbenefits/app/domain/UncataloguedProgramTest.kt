package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.LoyaltyProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A rewards programme found in email that the catalog has never heard of must
 * be trackable without the app inventing anything about it. Previously such a
 * programme could not exist at all: the scan only searched known senders, and
 * anything outside the 32-entry enum was discarded on the way in.
 */
class UncataloguedProgramTest {

    private val insights = LoyaltyInsights()

    private fun found(name: String, points: Long? = null) = Fixtures.account(
        program = LoyaltyProgram.OTHER_REWARDS,
        pointsNumeric = points,
    ).copy(customProgramName = name)

    @Test
    fun `an unknown programme resolves to a profile instead of throwing`() {
        val profile = LoyaltyProgramCatalog.profileFor(LoyaltyProgram.OTHER_REWARDS)
        assertTrue(profile.tiers.isEmpty())
        assertNull(profile.expiration.inactivityMonths)
        assertNull(profile.basePointsPerDollar)
        assertNull(profile.awardBand)
    }

    @Test
    fun `its balance has no value rather than a value of zero`() {
        // Pricing it at the placeholder rate would quietly say the points are worthless.
        val account = found("Panera MyPanera Rewards", points = 5_000)
        assertNull(insights.estimatedValueUsd(account))
        assertNull(insights.awardNightsEstimate(account))
    }

    @Test
    fun `it contributes nothing to the portfolio total but does not corrupt it`() {
        val known = Fixtures.account(id = 1, program = LoyaltyProgram.MARRIOTT_BONVOY, pointsNumeric = 100_000)
        val unknown = found("IKEA Family", points = 900)
        val expected = insights.estimatedValueUsd(known)!!
        assertEquals(expected, insights.portfolioValueUsd(listOf(known, unknown)), 0.0001)
    }

    @Test
    fun `no tier ladder means no progress and no next tier to chase`() {
        val progress = insights.tierProgress(found("Odeon Limitless"))
        assertNull(progress.currentTier)
        assertNull(progress.nextTier)
        assertNull(progress.fraction)
    }

    @Test
    fun `no expiry rule means expiry is unknown, not imminent`() {
        val account = found("Boots Advantage").copy(lastActivityAt = System.currentTimeMillis())
        assertNull(insights.expiryEstimate(account))
    }

    @Test
    fun `it is shown under its own name, not the placeholder`() {
        assertEquals("Panera MyPanera Rewards", found("Panera MyPanera Rewards").programDisplayName)
        assertTrue(found("Panera MyPanera Rewards").isUncatalogued)
    }

    @Test
    fun `a catalogued programme is unaffected`() {
        val known = Fixtures.account(program = LoyaltyProgram.MARRIOTT_BONVOY, pointsNumeric = 1_000)
        assertEquals(LoyaltyProgram.MARRIOTT_BONVOY.displayName, known.programDisplayName)
        assertTrue(!known.isUncatalogued)
        assertTrue(insights.estimatedValueUsd(known)!! > 0.0)
    }

    @Test
    fun `the catch-all carries no sender domains, so it is never searched for directly`() {
        assertTrue(LoyaltyProgram.OTHER_REWARDS.gmailSenderDomains.isEmpty())
        // Every other programme must have senders, or its Gmail query would be malformed.
        LoyaltyProgram.entries.filter { it != LoyaltyProgram.OTHER_REWARDS }.forEach {
            assertTrue("${it.name} has no sender domains", it.gmailSenderDomains.isNotEmpty())
        }
    }
}
