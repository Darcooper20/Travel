package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.BalanceUnit
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.TripKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * LoyaltyInsights drives every alert and the headline portfolio value, so a
 * regression here is visible on the first screen of the app. The recurring
 * theme in these tests: an unknown value must stay unknown, never become 0.
 */
class LoyaltyInsightsTest {

    private val insights = LoyaltyInsights()
    private val now = System.currentTimeMillis()
    private val today = LocalDate.now().toEpochDay()

    // ---- Values and unknowns ---------------------------------------------

    @Test
    fun `an unknown balance has no value, rather than a value of zero`() {
        assertNull(insights.estimatedValueUsd(Fixtures.account(pointsNumeric = null)))
        assertNull(insights.awardNightsEstimate(Fixtures.account(pointsNumeric = null)))
    }

    @Test
    fun `portfolio value adds the balances it knows and ignores the ones it does not`() {
        val known = Fixtures.account(id = 1, pointsNumeric = 100_000)
        val unknown = Fixtures.account(id = 2, pointsNumeric = null)
        val expected = insights.estimatedValueUsd(known)!!
        assertEquals(expected, insights.portfolioValueUsd(listOf(known, unknown)), 0.0001)
    }

    @Test
    fun `a dollar-denominated balance keeps the cents from the source text`() {
        val dollarProgram = LoyaltyProgram.entries.firstOrNull {
            LoyaltyProgramCatalog.profileFor(it).balanceUnit == BalanceUnit.DOLLARS
        }
        // Only meaningful if the catalog actually has a dollar-denominated program.
        if (dollarProgram != null) {
            val account = Fixtures.account(program = dollarProgram, pointsNumeric = 42, pointsBalance = "$42.57")
            assertEquals("$42.57", insights.balanceLabel(account))
        }
    }

    @Test
    fun `with no parsed number the raw text from the email is shown as-is`() {
        val account = Fixtures.account(pointsNumeric = null, pointsBalance = "about 42,500 points")
        assertEquals("about 42,500 points", insights.balanceLabel(account))
    }

    // ---- Tier progress ---------------------------------------------------

    @Test
    fun `tier progress is unknown when the program has not told us the progress`() {
        val progress = insights.tierProgress(Fixtures.account(qualifyingProgress = null))
        assertNull(progress.fraction)
        assertNull(progress.remaining)
    }

    @Test
    fun `tier progress reports what is left to the next tier and never exceeds full`() {
        val program = LoyaltyProgram.entries.first {
            val p = LoyaltyProgramCatalog.profileFor(it)
            p.tiers.size >= 2 && p.tiers[1].threshold != null
        }
        val threshold = LoyaltyProgramCatalog.profileFor(program).tiers[1].threshold!!

        val halfway = insights.tierProgress(Fixtures.account(program = program, qualifyingProgress = threshold / 2))
        assertNotNull(halfway.nextTier)
        assertEquals(threshold - threshold / 2, halfway.remaining)
        assertTrue(halfway.fraction!! in 0f..1f)

        // Past the threshold: clamped, and nothing remaining rather than a negative number.
        val overshot = insights.tierProgress(Fixtures.account(program = program, qualifyingProgress = threshold * 3))
        assertEquals(1f, overshot.fraction!!, 0.0001f)
        assertEquals(0, overshot.remaining)
    }

    // ---- Expiry ----------------------------------------------------------

    @Test
    fun `an expiry date stated in an email beats the inactivity estimate`() {
        val stated = now + TimeUnit.DAYS.toMillis(10)
        val account = Fixtures.account(pointsExpireAt = stated, lastActivityAt = now)
        assertEquals(stated, insights.expiryEstimate(account))
    }

    @Test
    fun `with no activity date and no stated date, expiry is unknown`() {
        assertNull(insights.expiryEstimate(Fixtures.account(pointsExpireAt = null, lastActivityAt = null)))
    }

    @Test
    fun `an inactivity rule projects forward from the last activity we saw`() {
        val program = LoyaltyProgram.entries.firstOrNull {
            LoyaltyProgramCatalog.profileFor(it).expiration.inactivityMonths != null
        }
        if (program != null) {
            val account = Fixtures.account(program = program, lastActivityAt = now)
            val estimate = insights.expiryEstimate(account)
            assertNotNull(estimate)
            assertTrue("expiry must be in the future when activity is today", estimate!! > now)
        }
    }

    // ---- Alerts ----------------------------------------------------------

    @Test
    fun `points about to expire raise an urgent alert with a notify key`() {
        val program = LoyaltyProgram.entries.first { LoyaltyProgramCatalog.profileFor(it).expiration.inactivityMonths != null }
        val account = Fixtures.account(program = program, pointsNumeric = 50_000, pointsExpireAt = now + TimeUnit.DAYS.toMillis(5))
        val alert = insights.alerts(listOf(account), emptyList(), now = now)
            .single { it.title.contains("expire") }
        assertEquals(Alert.Severity.URGENT, alert.severity)
        assertNotNull("must be notifiable so the daily worker can push it once", alert.notifyKey)
    }

    @Test
    fun `points already past their estimated expiry say so instead of going quiet`() {
        val program = LoyaltyProgram.entries.first { LoyaltyProgramCatalog.profileFor(it).expiration.inactivityMonths != null }
        val account = Fixtures.account(program = program, pointsNumeric = 50_000, pointsExpireAt = now - TimeUnit.DAYS.toMillis(3))
        val alert = insights.alerts(listOf(account), emptyList(), now = now).single { it.title.contains("may have expired") }
        assertEquals(Alert.Severity.URGENT, alert.severity)
        assertTrue(alert.notifyKey!!.endsWith("passed"))
    }

    @Test
    fun `a zero balance does not generate an expiry alert`() {
        val program = LoyaltyProgram.entries.first { LoyaltyProgramCatalog.profileFor(it).expiration.inactivityMonths != null }
        val account = Fixtures.account(program = program, pointsNumeric = 0, pointsExpireAt = now + TimeUnit.DAYS.toMillis(5))
        assertTrue(insights.alerts(listOf(account), emptyList(), now = now).none { it.title.contains("expire") })
    }

    @Test
    fun `a missing member number is flagged, a present one is not`() {
        val without = insights.alerts(listOf(Fixtures.account(membershipNumber = null)), emptyList(), now = now)
        assertTrue(without.any { it.title.contains("No ") && it.title.contains("member number") })
        val with = insights.alerts(listOf(Fixtures.account(membershipNumber = "998877")), emptyList(), now = now)
        assertTrue(with.none { it.title.contains("member number") })
    }

    @Test
    fun `an unknown loyalty number on a booking is worded as unconfirmed, not as missing`() {
        // The distinction matters: an email not showing a number never proved it was absent.
        val trip = Fixtures.trip(startEpochDay = today + 3, loyaltyNumberState = LoyaltyNumberState.UNKNOWN)
        val alert = insights.alerts(listOf(Fixtures.account()), listOf(trip), now = now)
            .single { it.tripId == trip.id && it.title.contains("number") }
        assertTrue(alert.title.contains("confirm"))
        assertTrue(alert.detail.contains("didn't show a loyalty number"))
        assertEquals(Alert.Severity.INFO, alert.severity)
    }

    @Test
    fun `a number the booking really lacks is escalated close to departure`() {
        val trip = Fixtures.trip(startEpochDay = today + 3, loyaltyNumberState = LoyaltyNumberState.MISSING)
        val alert = insights.alerts(listOf(Fixtures.account()), listOf(trip), now = now)
            .single { it.tripId == trip.id && it.title.contains("add your") }
        assertEquals(Alert.Severity.WARNING, alert.severity)
        assertEquals(Alert.Destination.TRIPS, alert.destination)
    }

    @Test
    fun `a confirmed number raises no alert at all`() {
        val trip = Fixtures.trip(startEpochDay = today + 3, loyaltyNumberState = LoyaltyNumberState.CONFIRMED)
        assertTrue(
            insights.alerts(listOf(Fixtures.account()), listOf(trip), now = now)
                .none { it.tripId == trip.id && it.title.contains("number") },
        )
    }

    @Test
    fun `a booking for a program you do not hold suggests joining`() {
        val trip = Fixtures.trip(startEpochDay = today + 5, program = LoyaltyProgram.MARRIOTT_BONVOY)
        val alert = insights.alerts(emptyList(), listOf(trip), now = now).single { it.tripId == trip.id && it.title.contains("number") }
        assertTrue(alert.detail.contains("isn't in your wallet"))
    }

    @Test
    fun `a flight tomorrow prompts check-in and is notifiable exactly once`() {
        val trip = Fixtures.trip(id = 7, kind = TripKind.FLIGHT, startEpochDay = today + 1, title = "Delta SFO-JFK")
        val alert = insights.alerts(emptyList(), listOf(trip), now = now).single { it.title.contains("tomorrow") }
        assertTrue(alert.detail.contains("check-in"))
        assertEquals("trip:7:tomorrow", alert.notifyKey)
    }

    @Test
    fun `a trip far in the future is not nagged about yet`() {
        val trip = Fixtures.trip(startEpochDay = today + 300, loyaltyNumberState = LoyaltyNumberState.CONFIRMED)
        assertTrue(insights.alerts(emptyList(), listOf(trip), now = now).none { it.title.contains("day(s)") })
    }

    @Test
    fun `alerts name the household member who holds the account`() {
        val program = LoyaltyProgram.entries.first { LoyaltyProgramCatalog.profileFor(it).expiration.inactivityMonths != null }
        val account = Fixtures.account(program = program, pointsNumeric = 10_000, pointsExpireAt = now + TimeUnit.DAYS.toMillis(5), memberName = "Sam")
        val alert = insights.alerts(listOf(account), emptyList(), now = now).single { it.title.contains("expire") }
        assertTrue(alert.title.startsWith("Sam's"))
    }

    @Test
    fun `a reset tip is offered for every kind of program`() {
        LoyaltyProgram.entries.forEach { assertTrue("no tip for $it", insights.resetTip(it).isNotBlank()) }
    }
}
