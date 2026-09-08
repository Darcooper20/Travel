package com.travelbenefits.app.domain.model

/** A spending category used for both catalog reward rates and the "best card" picker. */
enum class SpendingCategory(val label: String) {
    DINING("Dining"),
    GROCERIES("Groceries"),
    GAS_EV("Gas & EV Charging"),
    TRAVEL_GENERAL("Travel (flights, hotels, rideshare)"),
    HOTELS("Hotels booked directly"),
    AIRFARE("Airfare"),
    ONLINE_SHOPPING("Online shopping"),
    STREAMING("Streaming & subscriptions"),
    DRUGSTORES("Drugstores"),
    TRANSIT("Transit & rideshare"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Everything else"),
}

/** The unit a card's rewards are earned in - needed to compare a cash-back card against a points card fairly. */
enum class RewardCurrency(val displayName: String, val estValueCentsPerPoint: Double) {
    CASH_BACK("Cash back", 1.0),
    CHASE_UR("Chase Ultimate Rewards", 1.7),
    AMEX_MR("Amex Membership Rewards", 1.8),
    CAPITAL_ONE_MILES("Capital One Miles", 1.7),
    CITI_TYP("Citi ThankYou Points", 1.6),
    WELLS_REWARDS("Wells Fargo Rewards", 1.0),
    BANK_OF_AMERICA_CASH("Bank of America cash rewards", 1.0),
    DISCOVER_CASHBACK("Discover cashback", 1.0),
    MARRIOTT_BONVOY_POINTS("Marriott Bonvoy points", 0.8),
    HILTON_HONORS_POINTS("Hilton Honors points", 0.5),
    WORLD_OF_HYATT_POINTS("World of Hyatt points", 1.7),
    IHG_ONE_REWARDS_POINTS("IHG One Rewards points", 0.5),
    GENERIC_POINTS("Points", 1.0),
    ;

    /**
     * These cents-per-point figures are widely-cited third-party estimates
     * (in the style of independent points-valuation trackers), NOT a
     * guaranteed redemption value - actual value depends heavily on how you
     * redeem. Treated as opinion/estimate input to ranking, not fact.
     */
}

data class RewardRate(
    val category: SpendingCategory,
    /** Points (or % for cash back) earned per dollar spent. */
    val multiplier: Double,
    val note: String? = null,
)

data class CardCredit(
    val label: String,
    val annualValueUsd: Int,
    val frequency: String,
    val description: String,
)

enum class HotelProgram(
    val displayName: String,
    /** Sender domains/keywords used to build a Gmail search query for this program. */
    val gmailSenderDomains: List<String>,
) {
    MARRIOTT_BONVOY("Marriott Bonvoy", listOf("email-marriott.com", "marriott.com", "bonvoy.com")),
    HILTON_HONORS("Hilton Honors", listOf("hiltonhonors.com", "hilton.com")),
    WORLD_OF_HYATT("World of Hyatt", listOf("e.hyatt.com", "hyatt.com")),
    IHG_ONE_REWARDS("IHG One Rewards", listOf("email.ihg.com", "ihg.com")),
    WYNDHAM_REWARDS("Wyndham Rewards", listOf("wyndhamrewards.com", "wyndham.com")),
    CHOICE_PRIVILEGES("Choice Privileges", listOf("choicehotels.com")),
    ACCOR_LIVE_LIMITLESS("Accor Live Limitless", listOf("accor.com", "all.accor.com")),
    BEST_WESTERN_REWARDS("Best Western Rewards", listOf("bestwestern.com")),
    RADISSON_REWARDS("Radisson Rewards", listOf("radissonhotels.com")),
}

data class HotelBenefit(
    val program: HotelProgram,
    /** Short label, e.g. "Gold elite status". */
    val benefit: String,
    val description: String,
)

/**
 * One entry in the curated, hand-maintained card catalog.
 *
 * Annual fees and benefit terms change frequently and this list is a manually
 * curated snapshot, not a live feed - [dataAsOf] and [notes] exist so the UI
 * can flag that it should be verified against the issuer's site, especially
 * before relying on it for a purchase decision.
 */
data class CardCatalogEntry(
    val id: String,
    val displayName: String,
    val issuer: String,
    val network: String,
    val annualFeeUsd: Int,
    val rewardCurrency: RewardCurrency,
    val baseMultiplier: Double,
    val categoryRates: List<RewardRate>,
    val credits: List<CardCredit> = emptyList(),
    val hotelBenefits: List<HotelBenefit> = emptyList(),
    val dataAsOf: String,
    val notes: String? = null,
) {
    fun rateFor(category: SpendingCategory): RewardRate =
        categoryRates.firstOrNull { it.category == category }
            ?: RewardRate(category, baseMultiplier)

    /** Estimated cents of value per dollar spent in [category], using [RewardCurrency]'s estimated point value. */
    fun estimatedValueCentsPerDollar(category: SpendingCategory): Double =
        rateFor(category).multiplier * rewardCurrency.estValueCentsPerPoint
}
