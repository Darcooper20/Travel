package com.travelbenefits.app.domain.model

/** Where an award result came from. Shown on every result so leads are never mistaken for bookable inventory. */
enum class ResultKind(val label: String) {
    RESEARCH_LEAD("Research lead (AI web search)"),
    CACHED_INVENTORY("Cached inventory (provider snapshot)"),
    LIVE_SEARCH("Live search result"),
    USER_CONFIRMED("User-confirmed booking"),
    USER_ENTERED("Entered by you"),
}

enum class Cabin(val label: String, val seatsAeroCode: String) { ECONOMY("Economy", "economy"), PREMIUM("Premium economy", "premium"), BUSINESS("Business", "business"), FIRST("First", "first") }

data class AwardSearchRequest(
    val origin: String,
    val destination: String,
    val dateFrom: String,
    val dateTo: String,
    val cabin: Cabin,
    val passengers: Int = 1,
    /** ±days around the range the user is willing to move. */
    val flexibleDays: Int = 0,
    val nearbyOrigins: List<String> = emptyList(),
    val nearbyDestinations: List<String> = emptyList(),
    val maxConnections: Int? = null,
    val program: LoyaltyProgram? = null,
)

data class AwardResult(
    val kind: ResultKind,
    val provider: String,
    val program: String?,
    val origin: String,
    val destination: String,
    val date: String,
    val cabin: Cabin,
    val pointsPerPassenger: Long?,
    val taxesFeesUsd: Double?,
    val seatsRemaining: Int?,
    val isDirect: Boolean?,
    val mixedCabin: Boolean?,
    val airlines: String?,
    val bookingUrl: String?,
    /** When the provider last updated this row, ISO or epoch millis string. */
    val asOf: String?,
    val note: String?,
) {
    fun enoughSeats(passengers: Int): Boolean? = seatsRemaining?.let { it >= passengers }
}

data class AwardSearchResponse(
    val request: AwardSearchRequest,
    val results: List<AwardResult>,
    val provider: String,
    val coverage: String,
    val caveats: List<String>,
    val searchedAt: Long,
)
