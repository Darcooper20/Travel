package com.travelbenefits.app.domain.model

enum class TripKind(val label: String) {
    FLIGHT("Flight"),
    HOTEL("Hotel"),
    CAR("Car rental"),
    RAIL("Rail"),
    OTHER("Other"),
}

enum class TripSource { GMAIL_SCAN, MANUAL }

/**
 * A booking pulled out of a confirmation email (or typed in). Dates are
 * epoch-day values (java.time.LocalDate.toEpochDay) so they sort and compare
 * without timezone games; null when the email didn't state them.
 */
data class Trip(
    val id: Long,
    val kind: TripKind,
    val provider: String,
    val confirmationNumber: String?,
    val title: String,
    val startEpochDay: Long?,
    val endEpochDay: Long?,
    val origin: String?,
    val destination: String?,
    /** Program the booking is (or should be) crediting to, when the email named one or the provider maps to one. */
    val loyaltyProgram: LoyaltyProgram?,
    /** True when the confirmation shows a membership number attached to the booking. */
    val loyaltyNumberOnBooking: Boolean,
    val totalCost: String?,
    val pointsUsed: Long?,
    val pointsEarnedEstimate: Long?,
    val source: TripSource,
    val sourceEmailSubject: String?,
    val sourceMessageId: String?,
    val notes: String?,
    val createdAt: Long,
    /** Wallet card the booking was paid with, when known. */
    val paymentCardId: Long? = null,
) {
    fun isUpcoming(todayEpochDay: Long): Boolean {
        val end = endEpochDay ?: startEpochDay ?: return false
        return end >= todayEpochDay
    }
}
