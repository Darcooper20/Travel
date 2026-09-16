package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.local.SyncSettings
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.ProgramRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog covers five markets, not just the United States. These tests
 * guard two separate things.
 *
 * First, coverage: each named market has to keep at least one airline and one
 * retail programme, so a refactor can't quietly drop a country.
 *
 * Second - and this matters more - honesty. Tier ladders and point valuations
 * for the non-US programmes were NOT verified when they were added, so they
 * are deliberately absent. A valuation is priced in US cents and there is no
 * exchange rate in the app; a tier ladder would be a guess. The rule the app
 * has followed everywhere else applies here too: unknown stays unknown rather
 * than becoming zero. These tests pin that down, so the day someone adds real
 * figures they have to flip valuationIsKnown deliberately rather than leave a
 * placeholder 0.0 on screen reading as "worthless".
 */
class NonUsProgramsTest {

    private val insights = LoyaltyInsights()
    private val nonUsRegions = listOf(ProgramRegion.AU, ProgramRegion.UK, ProgramRegion.CA, ProgramRegion.ZA)

    @Test
    fun `every named market has both an airline and a retail programme`() {
        nonUsRegions.forEach { region ->
            val inRegion = LoyaltyProgram.entries.filter { it.region == region }
            assertTrue(
                "${region.label} has no airline programme",
                inRegion.any { it.kind == LoyaltyProgramKind.AIRLINE },
            )
            assertTrue(
                "${region.label} has no shop programme",
                inRegion.any { it.kind == LoyaltyProgramKind.SHOP },
            )
        }
    }

    @Test
    fun `every programme except the catch-all has a curated profile`() {
        LoyaltyProgram.entries.filter { it != LoyaltyProgram.OTHER_REWARDS }.forEach {
            assertNotNull("${it.name} is missing from the catalog", LoyaltyProgramCatalog.profiles[it])
        }
        // The catch-all must NOT be in the map - it falls through to the
        // explicitly-unknown profile, which is what marks it as uncurated.
        assertNull(LoyaltyProgramCatalog.profiles[LoyaltyProgram.OTHER_REWARDS])
    }

    @Test
    fun `every profile links somewhere real and states an expiry rule`() {
        LoyaltyProgram.entries.filter { it != LoyaltyProgram.OTHER_REWARDS }.forEach {
            val profile = LoyaltyProgramCatalog.profileFor(it)
            assertTrue("${it.name} account link is not https", profile.accountUrl.startsWith("https://"))
            assertTrue("${it.name} award link is not https", profile.awardSearchUrl.startsWith("https://"))
            assertTrue("${it.name} says nothing about expiry", profile.expiration.summary.isNotBlank())
        }
    }

    @Test
    fun `a programme with no valuation reports none, instead of a value of zero`() {
        val unvalued = LoyaltyProgram.entries.filter { !LoyaltyProgramCatalog.profileFor(it).valuationIsKnown }
        assertTrue("nothing is testing the unvalued path", unvalued.isNotEmpty())
        unvalued.forEach {
            val profile = LoyaltyProgramCatalog.profileFor(it)
            assertNull("${it.name} offers a valuation it does not have", profile.estValueCentsPerPointOrNull)
            assertEquals("${it.name} holds a half-real valuation", 0.0, profile.estValueCentsPerPoint, 0.0)
            val account = Fixtures.account(program = it, pointsNumeric = 100_000)
            assertNull("${it.name} priced a balance it cannot price", insights.estimatedValueUsd(account))
        }
    }

    @Test
    fun `a programme with a valuation still reports one`() {
        val profile = LoyaltyProgramCatalog.profileFor(LoyaltyProgram.MARRIOTT_BONVOY)
        assertEquals(profile.estValueCentsPerPoint, profile.estValueCentsPerPointOrNull!!, 0.0)
    }

    @Test
    fun `no non-US programme carries a tier ladder or earn rate that was never checked`() {
        LoyaltyProgram.entries.filter { it.region in nonUsRegions }.forEach {
            val profile = LoyaltyProgramCatalog.profileFor(it)
            assertTrue("${it.name} has an unverified tier ladder", profile.tiers.isEmpty())
            assertNull("${it.name} has an unverified earn rate", profile.basePointsPerDollar)
            assertTrue("${it.name} should not claim a valuation yet", !profile.valuationIsKnown)
        }
    }

    /**
     * Expiry windows that were checked against the programmes' own terms.
     * Pinned so a later edit to the catalog can't silently shorten or lengthen
     * one: a wrong number here turns into a wrong expiry warning on the
     * dashboard. Programmes deliberately left with no window are listed in
     * the next test.
     */
    @Test
    fun `verified expiry windows are the ones the programmes actually publish`() {
        val expected = mapOf(
            LoyaltyProgram.QANTAS_FREQUENT_FLYER to 18,
            LoyaltyProgram.VELOCITY_FREQUENT_FLYER to 24,
            LoyaltyProgram.FLYBUYS to 12,
            LoyaltyProgram.BA_EXECUTIVE_CLUB to 36,
            LoyaltyProgram.NECTAR to 12,
            LoyaltyProgram.BOOTS_ADVANTAGE to 12,
            LoyaltyProgram.AIR_CANADA_AEROPLAN to 18,
            LoyaltyProgram.PC_OPTIMUM to 12,
            LoyaltyProgram.SCENE_PLUS to 24,
            LoyaltyProgram.AIR_MILES_CA to 24,
        )
        expected.forEach { (program, months) ->
            assertEquals(
                "${program.name} expiry window changed",
                months,
                LoyaltyProgramCatalog.profileFor(program).expiration.inactivityMonths,
            )
        }
    }

    @Test
    fun `programmes whose rules could not be pinned down show no countdown at all`() {
        // Each of these either has no inactivity rule (Virgin Points, eBucks)
        // or runs on something the app cannot model as a rolling clock (a
        // calendar-anchored date, a per-voucher deadline, a programme
        // mid-transition). Guessing a number would produce a confident,
        // wrong warning, so there is none - the summary carries the detail.
        val noClock = listOf(
            LoyaltyProgram.EVERYDAY_REWARDS,
            LoyaltyProgram.VIRGIN_ATLANTIC_FLYING_CLUB,
            LoyaltyProgram.TESCO_CLUBCARD,
            LoyaltyProgram.WESTJET_REWARDS,
            LoyaltyProgram.SAA_VOYAGER,
            LoyaltyProgram.FNB_EBUCKS,
            LoyaltyProgram.CLICKS_CLUBCARD,
            LoyaltyProgram.PICK_N_PAY_SMART_SHOPPER,
            LoyaltyProgram.DISCOVERY_VITALITY,
        )
        noClock.forEach {
            val profile = LoyaltyProgramCatalog.profileFor(it)
            assertNull("${it.name} invented an expiry window", profile.expiration.inactivityMonths)
            assertTrue("${it.name} explains nothing instead", profile.expiration.summary.length > 30)
            // With no rule and no stated date, there is nothing to project.
            assertNull(
                "${it.name} projected an expiry date out of nothing",
                insights.expiryEstimate(
                    Fixtures.account(program = it, pointsExpireAt = null, lastActivityAt = System.currentTimeMillis()),
                ),
            )
        }
    }

    @Test
    fun `a verified window does project an expiry date from the last activity`() {
        val now = System.currentTimeMillis()
        val account = Fixtures.account(program = LoyaltyProgram.QANTAS_FREQUENT_FLYER, lastActivityAt = now)
        val estimate = insights.expiryEstimate(account)
        assertNotNull(estimate)
        // 18 months out, give or take the length of the months involved.
        val days = (estimate!! - now) / 86_400_000L
        assertTrue("projected $days days, expected roughly 18 months", days in 540..560)
    }

    @Test
    fun `the scan covers every market until someone narrows it`() {
        assertEquals(ProgramRegion.entries.toSet(), SyncSettings().scanRegions)
    }

    @Test
    fun `narrowing the scan to one market drops the others but keeps that one whole`() {
        val settings = SyncSettings(scanRegions = setOf(ProgramRegion.AU, ProgramRegion.GLOBAL))
        val searched = LoyaltyProgram.entries.filter { it.region in settings.scanRegions && it.gmailSenderDomains.isNotEmpty() }
        assertTrue(searched.any { it == LoyaltyProgram.QANTAS_FREQUENT_FLYER })
        assertTrue("global hotel chains belong to no single market", searched.any { it == LoyaltyProgram.MARRIOTT_BONVOY })
        assertTrue("US senders should be skipped", searched.none { it.region == ProgramRegion.US })
        assertTrue("UK senders should be skipped", searched.none { it.region == ProgramRegion.UK })
    }

    @Test
    fun `sender domains are distinct enough not to pull one market's mail into another`() {
        // Two programmes sharing a sender domain would attribute balances to
        // whichever was searched first, which is exactly the kind of silent
        // mix-up the region split is meant to avoid.
        val seen = mutableMapOf<String, LoyaltyProgram>()
        LoyaltyProgram.entries.forEach { program ->
            program.gmailSenderDomains.forEach { domain ->
                val other = seen.put(domain, program)
                assertNull("$domain is claimed by both ${other?.name} and ${program.name}", other)
            }
        }
    }
}
