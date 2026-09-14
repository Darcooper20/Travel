package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.ActionEffort
import com.travelbenefits.app.domain.model.ActionItem
import com.travelbenefits.app.domain.model.ActionKind
import com.travelbenefits.app.domain.model.ActionState
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionEngineTest {
    private val engine = ActionEngine()

    private fun item(key: String, days: Long?, amount: Double?, effort: ActionEffort = ActionEffort.SHORT) = ActionItem(
        key, ActionKind.OTHER, key, "", amount, days?.let { 1_000 + it }, Confidence.HIGH, effort, "", "", Alert.Destination.LOYALTY, priority = 0,
    )

    @Test
    fun `sooner deadlines and larger amounts rank higher, effort lowers`() {
        val today = 1_000L
        val soonBig = engine.score(item("a", 2, 300.0), today)
        val laterSmall = engine.score(item("b", 60, 10.0), today)
        val noDeadline = engine.score(item("c", null, null), today)
        val soonBigLong = engine.score(item("d", 2, 300.0, ActionEffort.LONG), today)
        assertTrue(soonBig > laterSmall)
        assertTrue(laterSmall > noDeadline)
        assertTrue(soonBig > soonBigLong)
    }

    @Test
    fun `snoozed items reopen after the snooze date and states apply by key`() {
        val alert = Alert(Alert.Severity.WARNING, "Marriott points expire in 5 day(s)", "detail", notifyKey = "expiry:1:100:7")
        val base = ActionEngine.Inputs(listOf(alert), emptyList(), emptyList(), emptyList(), emptyList(), true, false, 0, emptyMap(), today = 1_000, now = 0)
        val snoozedFuture = engine.build(base.copy(states = mapOf("expiry:1:100:7" to (ActionState.SNOOZED to 1_010L)))).single()
        assertEquals(ActionState.SNOOZED, snoozedFuture.state)
        val snoozedPast = engine.build(base.copy(states = mapOf("expiry:1:100:7" to (ActionState.SNOOZED to 999L)))).single()
        assertEquals(ActionState.OPEN, snoozedPast.state)
        val dismissed = engine.build(base.copy(states = mapOf("expiry:1:100:7" to (ActionState.DISMISSED to null)))).single()
        assertEquals(ActionState.DISMISSED, dismissed.state)
        assertEquals(ActionKind.EXPIRING_POINTS, dismissed.kind)
        assertEquals(1_005L, dismissed.deadlineEpochDay)
    }
}
