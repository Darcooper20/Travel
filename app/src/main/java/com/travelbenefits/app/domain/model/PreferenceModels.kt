package com.travelbenefits.app.domain.model

/** Editable travel preferences (stored as user overrides in the global scope). */
data class UserPreferences(
    val homeAirports: List<String> = emptyList(),
    val preferredAirlines: List<String> = emptyList(),
    val preferredHotels: List<String> = emptyList(),
    val cabin: Cabin = Cabin.ECONOMY,
    val budgetUsdPerTrip: Double? = null,
    val flexibilityDays: Int = 0,
    val maxConnections: Int = 1,
    /** 0 = always prefer cash/earning, 100 = always prefer using points. Shifts the cash-vs-points toss-up band. */
    val pointsPreference: Int = 50,
    val convenienceOverValue: Boolean = false,
) {
    companion object {
        const val KEY_HOME_AIRPORTS = "pref:homeAirports"
        const val KEY_AIRLINES = "pref:airlines"
        const val KEY_HOTELS = "pref:hotels"
        const val KEY_CABIN = "pref:cabin"
        const val KEY_BUDGET = "pref:budget"
        const val KEY_FLEX = "pref:flexDays"
        const val KEY_CONNECTIONS = "pref:maxConnections"
        const val KEY_POINTS_PREF = "pref:pointsPreference"
        const val KEY_CONVENIENCE = "pref:convenience"
        val ALL_KEYS = listOf(KEY_HOME_AIRPORTS, KEY_AIRLINES, KEY_HOTELS, KEY_CABIN, KEY_BUDGET, KEY_FLEX, KEY_CONNECTIONS, KEY_POINTS_PREF, KEY_CONVENIENCE)

        fun from(overrides: Map<Pair<String, String>, String>): UserPreferences {
            fun g(k: String) = overrides["global" to k]
            return UserPreferences(
                homeAirports = g(KEY_HOME_AIRPORTS)?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotBlank() } ?: emptyList(),
                preferredAirlines = g(KEY_AIRLINES)?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                preferredHotels = g(KEY_HOTELS)?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList(),
                cabin = g(KEY_CABIN)?.let { c -> Cabin.entries.firstOrNull { it.name == c } } ?: Cabin.ECONOMY,
                budgetUsdPerTrip = g(KEY_BUDGET)?.toDoubleOrNull(),
                flexibilityDays = g(KEY_FLEX)?.toIntOrNull() ?: 0,
                maxConnections = g(KEY_CONNECTIONS)?.toIntOrNull() ?: 1,
                pointsPreference = g(KEY_POINTS_PREF)?.toIntOrNull()?.coerceIn(0, 100) ?: 50,
                convenienceOverValue = g(KEY_CONVENIENCE) == "true",
            )
        }
    }
}

/** A merchant offer or portal rate the user enrolled in (Amex Offers, Chase Offers, Rakuten...). */
data class MerchantOffer(
    val id: Long,
    val walletCardId: Long?,
    val merchant: String,
    val description: String,
    /** Fixed dollar value when known, e.g. "$20 back on $100+". */
    val valueUsd: Double?,
    /** Percent back when the offer is percentage based. */
    val percentBack: Double?,
    val minSpendUsd: Double?,
    val expiresEpochDay: Long?,
    val enrolled: Boolean,
    val kind: Kind,
    val source: String,
    val notes: String?,
) {
    enum class Kind(val label: String) { CARD_OFFER("Card-linked offer"), PORTAL("Shopping portal"), COUPON("Coupon / promo") }
}
