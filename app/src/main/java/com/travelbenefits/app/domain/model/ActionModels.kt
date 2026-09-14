package com.travelbenefits.app.domain.model

enum class ActionKind(val label: String) {
    EXPIRING_CREDIT("Credit expiring"), EXPIRING_CERTIFICATE("Certificate expiring"), EXPIRING_POINTS("Points expiring"),
    ACTIVATION("Activate category/benefit"), BONUS_DEADLINE("Welcome bonus deadline"), MISSING_REIMBURSEMENT("Reimbursement not seen"),
    TRIP("Upcoming trip"), TRIP_LOYALTY("Trip loyalty number"), RENEWAL("Card renewal"), STALE_BALANCE("Stale balance"),
    BROKEN_CONNECTION("Connection needs attention"), STATUS("Status progress"), SPEND("Spend routing"), OTHER("Other"),
}

enum class ActionEffort(val label: String, val minutes: Int) { TRIVIAL("1 min", 1), SHORT("5 min", 5), MEDIUM("15 min", 15), LONG("30+ min", 30) }

enum class ActionState { OPEN, SNOOZED, DISMISSED, COMPLETED }

/** One prioritised thing to do. [key] is stable across recomputation so states stick. */
data class ActionItem(
    val key: String,
    val kind: ActionKind,
    val title: String,
    val reason: String,
    val amountUsd: Double?,
    val deadlineEpochDay: Long?,
    val confidence: Confidence,
    val effort: ActionEffort,
    val source: String,
    val nextStep: String,
    val destination: Alert.Destination,
    val program: LoyaltyProgram? = null,
    val tripId: Long? = null,
    val walletCardId: Long? = null,
    /** 0-100, higher first. */
    val priority: Int,
    val state: ActionState = ActionState.OPEN,
    val snoozedUntilEpochDay: Long? = null,
)
