package com.travelbenefits.app.domain.model

enum class TripKind(val label: String) {
    FLIGHT("Flight"),
    HOTEL("Hotel"),
    CAR("Car rental"),
    RAIL("Rail"),
    OTHER("Other"),
}

enum class TripSource { GMAIL_SCAN, MANUAL }

enum class TripStatus(val label: String) { CONFIRMED("Confirmed"), CHANGED("Changed"), CANCELLED("Cancelled"), PARTIALLY_CANCELLED("Partially cancelled") }

/** Whether the loyalty number is on the reservation. Absence from an email is not proof it is absent - that is UNKNOWN. */
enum class LoyaltyNumberState(val label: String) { CONFIRMED("On the reservation"), MISSING("Not on the reservation"), UNKNOWN("Unknown") }

data class TripSegment(
    val id: Long,
    val tripId: Long,
    val sequence: Int,
    val carrier: String?,
    val flightNumber: String?,
    val origin: String?,
    val destination: String?,
    /** ISO local date-time at the origin ("2026-10-03T14:35"), when known. */
    val departLocal: String?,
    val arriveLocal: String?,
    /** IANA zone of the origin, when known (e.g. "America/New_York"). */
    val timeZoneId: String?,
    val cabin: String?,
    val status: TripStatus,
)

enum class TripEventKind(val label: String) { CREATED("Booked"), CHANGED("Changed"), CANCELLED("Cancelled"), REBOOKED("Rebooked"), NOTE("Note"), PAYMENT("Payment recorded"), BENEFIT("Benefit applied") }

data class TripEvent(val id: Long, val tripId: Long, val kind: TripEventKind, val detail: String, val occurredAt: Long, val source: String)

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
    /** Legacy flag kept in sync with [loyaltyNumberState] == CONFIRMED; prefer the state. */
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
    val status: TripStatus = TripStatus.CONFIRMED,
    val loyaltyNumberState: LoyaltyNumberState = LoyaltyNumberState.UNKNOWN,
    val bookingChannel: BookingChannel? = null,
    val cancellationTerms: String? = null,
    /** Local departure/check-in time "HH:mm" and IANA zone, when known - drives check-in reminders. */
    val departureTimeLocal: String? = null,
    val timeZoneId: String? = null,
    val travelers: String? = null,
    /** Certificate (benefit item) applied to this booking, if any. */
    val certificateId: Long? = null,
    /** Cash price recorded for comparisons, in USD. */
    val cashPriceUsd: Double? = null,
    val pointsProgram: LoyaltyProgram? = null,
) {
    val isCancelled: Boolean get() = status == TripStatus.CANCELLED

    fun isUpcoming(todayEpochDay: Long): Boolean {
        if (isCancelled) return false
        val end = endEpochDay ?: startEpochDay ?: return false
        return end >= todayEpochDay
    }

    /** Nights for hotel bookings, from the dates. */
    val nights: Int? get() = if (kind == TripKind.HOTEL && startEpochDay != null && endEpochDay != null) (endEpochDay - startEpochDay).toInt().coerceAtLeast(1) else null
}
