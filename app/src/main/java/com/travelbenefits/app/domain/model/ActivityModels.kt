package com.travelbenefits.app.domain.model

enum class ActivityKind(val label: String) {
    BALANCE_CHANGED("Balance changed"),
    TIER_CHANGED("Status changed"),
    ACCOUNT_FOUND("Membership found"),
    TRIP_FOUND("Trip found"),
    POINTS_EXPIRING("Points expiring"),
    SYNC_COMPLETED("Sync finished"),
    SYNC_FAILED("Sync failed"),
    INFO("Info"),
}

/** One line in the home-screen activity feed - the change log of what the email monitor found. */
data class ActivityEvent(
    val id: Long,
    val kind: ActivityKind,
    val program: LoyaltyProgram?,
    val title: String,
    val detail: String?,
    val occurredAt: Long,
    val isRead: Boolean,
)

/** A stored balance reading so the app can show history and detect changes between scans. */
data class PointsSnapshot(
    val id: Long,
    val program: LoyaltyProgram,
    val points: Long,
    val tier: String?,
    val recordedAt: Long,
    val source: LoyaltyAccountSource,
)

/** Something that needs the user's attention now, computed (not stored) from the current state. */
data class Alert(
    val severity: Severity,
    val title: String,
    val detail: String,
    val program: LoyaltyProgram? = null,
    val tripId: Long? = null,
) {
    enum class Severity { INFO, WARNING, URGENT }
}
