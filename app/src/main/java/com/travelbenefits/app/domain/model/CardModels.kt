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
    RENT("Rent"),
    OFFICE_SUPPLIES("Office supply stores"),
    PHONE_INTERNET_CABLE("Phone, internet & cable services"),
    SHIPPING("Shipping"),
    OTHER("Everything else"),
}

/**
 * The unit a card's rewards are earned in - needed to compare a cash-back
 * card against a points/miles card fairly. [estValueCentsPerPoint] figures
 * are widely-cited third-party estimates (in the style of independent
 * points-valuation trackers), NOT a guaranteed redemption value - actual
 * value depends heavily on how you redeem. Treated as opinion/estimate
 * input to ranking, not fact. [displayAsPercent] only controls how the UI
 * formats the rate (e.g. "3%" vs "3x points") - it doesn't affect ranking.
 */
enum class RewardCurrency(
    val displayName: String,
    val estValueCentsPerPoint: Double,
    val displayAsPercent: Boolean = false,
) {
    CASH_BACK("Cash back", 1.0, displayAsPercent = true),
    CHASE_UR("Chase Ultimate Rewards", 1.7),
    AMEX_MR("Amex Membership Rewards", 1.8),
    CAPITAL_ONE_MILES("Capital One Miles", 1.7),
    CITI_TYP("Citi ThankYou Points", 1.6),
    WELLS_REWARDS("Wells Fargo Rewards", 1.0),
    BANK_OF_AMERICA_CASH("Bank of America cash rewards", 1.0, displayAsPercent = true),
    DISCOVER_CASHBACK("Discover cashback", 1.0, displayAsPercent = true),
    MARRIOTT_BONVOY_POINTS("Marriott Bonvoy points", 0.8),
    HILTON_HONORS_POINTS("Hilton Honors points", 0.5),
    WORLD_OF_HYATT_POINTS("World of Hyatt points", 1.7),
    IHG_ONE_REWARDS_POINTS("IHG One Rewards points", 0.5),
    DELTA_SKYMILES("Delta SkyMiles", 1.2),
    UNITED_MILEAGEPLUS("United MileagePlus miles", 1.3),
    SOUTHWEST_RAPID_REWARDS("Southwest Rapid Rewards points", 1.3),
    AA_ADVANTAGE("American Airlines AAdvantage miles", 1.4),
    ATMOS_REWARDS("Atmos Rewards miles (Alaska/Hawaiian, formerly Mileage Plan)", 1.5),
    JETBLUE_TRUEBLUE("JetBlue TrueBlue points", 1.3),
    BILT_POINTS("Bilt Rewards points", 1.8),
    STORE_REWARDS("Store credit/rewards", 1.0, displayAsPercent = true),
    GENERIC_POINTS("Points", 1.0),
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

enum class LoyaltyProgramKind { HOTEL, AIRLINE }

/**
 * Every hotel and airline loyalty program this app knows about - used for
 * three things: manually-tracked loyalty accounts (Hotels/Loyalty screen),
 * the Gmail scan (via [gmailSenderDomains]), and elite-status perks a
 * credit card automatically grants (via [CardCatalogEntry.loyaltyBenefits]).
 * Hotel and airline programs share this one enum (rather than two) because
 * every other piece of code that touches "a loyalty program" - the account
 * tracker, the Gmail scanner, the card-benefit model - treats them
 * identically; [kind] exists only for UI grouping.
 */
enum class LoyaltyProgram(
    val displayName: String,
    val kind: LoyaltyProgramKind,
    /** Sender domains/keywords used to build a Gmail search query for this program. */
    val gmailSenderDomains: List<String>,
) {
    MARRIOTT_BONVOY("Marriott Bonvoy", LoyaltyProgramKind.HOTEL, listOf("email-marriott.com", "marriott.com", "bonvoy.com")),
    HILTON_HONORS("Hilton Honors", LoyaltyProgramKind.HOTEL, listOf("hiltonhonors.com", "hilton.com")),
    WORLD_OF_HYATT("World of Hyatt", LoyaltyProgramKind.HOTEL, listOf("e.hyatt.com", "hyatt.com")),
    IHG_ONE_REWARDS("IHG One Rewards", LoyaltyProgramKind.HOTEL, listOf("email.ihg.com", "ihg.com")),
    WYNDHAM_REWARDS("Wyndham Rewards", LoyaltyProgramKind.HOTEL, listOf("wyndhamrewards.com", "wyndham.com")),
    CHOICE_PRIVILEGES("Choice Privileges", LoyaltyProgramKind.HOTEL, listOf("choicehotels.com")),
    ACCOR_LIVE_LIMITLESS("Accor Live Limitless", LoyaltyProgramKind.HOTEL, listOf("accor.com", "all.accor.com")),
    BEST_WESTERN_REWARDS("Best Western Rewards", LoyaltyProgramKind.HOTEL, listOf("bestwestern.com")),
    RADISSON_REWARDS("Radisson Rewards", LoyaltyProgramKind.HOTEL, listOf("radissonhotels.com")),
    DELTA_SKYMILES("Delta SkyMiles", LoyaltyProgramKind.AIRLINE, listOf("delta.com", "email.delta.com")),
    UNITED_MILEAGEPLUS("United MileagePlus", LoyaltyProgramKind.AIRLINE, listOf("united.com", "email.united.com")),
    SOUTHWEST_RAPID_REWARDS("Southwest Rapid Rewards", LoyaltyProgramKind.AIRLINE, listOf("southwest.com", "luv.southwest.com")),
    AMERICAN_AADVANTAGE("American Airlines AAdvantage", LoyaltyProgramKind.AIRLINE, listOf("aa.com", "email.aa.com")),
    ATMOS_REWARDS_AIRLINE("Atmos Rewards (Alaska/Hawaiian)", LoyaltyProgramKind.AIRLINE, listOf("alaskaair.com", "atmosrewards.com", "hawaiianairlines.com")),
    JETBLUE_TRUEBLUE("JetBlue TrueBlue", LoyaltyProgramKind.AIRLINE, listOf("jetblue.com")),
}

data class LoyaltyBenefit(
    val program: LoyaltyProgram,
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
    val loyaltyBenefits: List<LoyaltyBenefit> = emptyList(),
    val dataAsOf: String,
    val notes: String? = null,
    /** True for a product that's been shut down or closed to new applicants - kept in the catalog for people who already hold it, but never a sensible "use this card" recommendation. */
    val isDiscontinued: Boolean = false,
) {
    fun rateFor(category: SpendingCategory): RewardRate =
        categoryRates.firstOrNull { it.category == category }
            ?: RewardRate(category, baseMultiplier)

    /** Estimated cents of value per dollar spent in [category], using [RewardCurrency]'s estimated point value. */
    fun estimatedValueCentsPerDollar(category: SpendingCategory): Double =
        rateFor(category).multiplier * rewardCurrency.estValueCentsPerPoint
}
