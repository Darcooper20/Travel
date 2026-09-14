package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.Expectation
import com.travelbenefits.app.domain.model.ExpectationKind
import com.travelbenefits.app.domain.model.ExpectationStatus
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyAccountSource
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.PointsSnapshot
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSource
import com.travelbenefits.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatusAndReconciliationTest {

    private val today = LocalDate.of(2026, 9, 14)
    private fun trip(id: Long, program: LoyaltyProgram, nights: Int, state: LoyaltyNumberState, pointsUsed: Long? = null, status: TripStatus = TripStatus.CONFIRMED) = Trip(
        id, TripKind.HOTEL, "Hotel", "C$id", "Stay $id", today.plusDays(10).toEpochDay(), today.plusDays(10 + nights.toLong()).toEpochDay(), null, "City", program,
        state == LoyaltyNumberState.CONFIRMED, null, pointsUsed, null, TripSource.MANUAL, null, null, null, 0, status = status, loyaltyNumberState = state,
    )

    private fun account(program: LoyaltyProgram, tier: String?, posted: Int?) = LoyaltyAccount(1, program, "123", tier, null, LoyaltyAccountSource.MANUAL, null, 0, qualifyingProgress = posted)

    @Test
    fun `booked nights count only with a confirmed number and verified award rule`() {
        val f = StatusForecaster()
        val trips = listOf(
            trip(1, LoyaltyProgram.WORLD_OF_HYATT, 3, LoyaltyNumberState.CONFIRMED),
            trip(2, LoyaltyProgram.WORLD_OF_HYATT, 2, LoyaltyNumberState.UNKNOWN),
            trip(3, LoyaltyProgram.WORLD_OF_HYATT, 4, LoyaltyNumberState.CONFIRMED, pointsUsed = 20_000), // Hyatt: award stays count (verified true)
            trip(4, LoyaltyProgram.WORLD_OF_HYATT, 5, LoyaltyNumberState.CONFIRMED, status = TripStatus.CANCELLED),
        )
        val fc = f.forecast(account(LoyaltyProgram.WORLD_OF_HYATT, "Explorist", 20), trips, hypothetical = 0, typicalUnitCostUsd = 200.0, today = today)
        assertEquals(7, fc.booked)
        assertEquals(27, fc.projected)
        assertEquals("Globalist", fc.nextTier?.name)
        assertEquals(33, fc.remainingAfterBooked)
        assertEquals(33 * 200.0, fc.estimatedCostToTierUsd!!, 0.01)
        assertTrue(fc.notes.any { it.contains("not counted") })
    }

    @Test
    fun `award nights are excluded when the program rule is unverified`() {
        val f = StatusForecaster()
        val trips = listOf(trip(1, LoyaltyProgram.SOUTHWEST_RAPID_REWARDS, 2, LoyaltyNumberState.CONFIRMED, pointsUsed = 10_000))
        val fc = f.forecast(account(LoyaltyProgram.SOUTHWEST_RAPID_REWARDS, null, null), trips, today = today)
        assertEquals(0, fc.booked)
        assertTrue(fc.awardStaysRule.contains("not verified"))
    }

    @Test
    fun `program points expectation matches when the balance moved by at least the expected amount`() {
        val engine = ReconciliationEngine()
        val created = today.minusDays(30)
        val e = Expectation(1, ExpectationKind.PROGRAM_POINTS, "TRIP", "1", null, LoyaltyProgram.MARRIOTT_BONVOY, 5_000.0, "points", today.minusDays(1).toEpochDay(), ExpectationStatus.OPEN, null, null, null, created.toEpochDay() * 86_400_000L, null)
        val snaps = listOf(
            PointsSnapshot(1, LoyaltyProgram.MARRIOTT_BONVOY, 40_000, null, created.minusDays(1).toEpochDay() * 86_400_000L, LoyaltyAccountSource.GMAIL_SCAN),
            PointsSnapshot(2, LoyaltyProgram.MARRIOTT_BONVOY, 46_000, null, today.toEpochDay() * 86_400_000L, LoyaltyAccountSource.GMAIL_SCAN),
        )
        val outcome = engine.evaluate(e, snaps, emptyList(), emptyList(), today)
        assertTrue(outcome is ReconciliationEngine.Outcome.Matched)
        assertEquals(6_000.0, (outcome as ReconciliationEngine.Outcome.Matched).received, 0.0)
    }

    @Test
    fun `nothing is a discrepancy before the due date`() {
        val engine = ReconciliationEngine()
        val e = Expectation(1, ExpectationKind.PROGRAM_POINTS, "TRIP", "1", null, LoyaltyProgram.MARRIOTT_BONVOY, 5_000.0, "points", today.plusDays(10).toEpochDay(), ExpectationStatus.OPEN, null, null, null, today.minusDays(5).toEpochDay() * 86_400_000L, null)
        val outcome = engine.evaluate(e, emptyList(), emptyList(), emptyList(), today)
        assertEquals(ReconciliationEngine.Outcome.Waiting, outcome)
    }

    @Test
    fun `claim draft contains the essentials and is never marked sent`() {
        val engine = ReconciliationEngine()
        val e = Expectation(1, ExpectationKind.CREDIT, "CREDIT", "credit:1:Travel credit", 1, null, 300.0, "USD", today.toEpochDay(), ExpectationStatus.DISCREPANCY, 0.0, "none", "Hotel charge 2026-08-02", 0, null)
        val draft = engine.claimDraft(e, "Sapphire Reserve", null)
        assertTrue(draft.body.contains("300"))
        assertTrue(draft.subject.contains("statement credit"))
        assertTrue(draft.checklist.any { it.contains("Statement") })
    }
}
