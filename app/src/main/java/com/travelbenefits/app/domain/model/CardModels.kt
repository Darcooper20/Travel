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

/** How long a spending cap lasts before it resets. */
enum class CapPeriod(val label: String) { NONE("no cap"), MONTHLY("per month"), STATEMENT_CYCLE("per statement cycle"), QUARTERLY("per quarter"), ANNUAL("per calendar year"), ACCOUNT_YEAR("per account year") }

/** Whether a rate needs the purchase to go through a specific channel. */
enum class BookingChannel(val label: String) { ANY("any"), DIRECT("booked directly with the airline/hotel"), ISSUER_PORTAL("booked through the issuer's travel portal") }

/**
 * A structured earning rule. Unknown conditions stay null (unknown), never
 * zero or "no cap". [capUsd] is the spend eligible for [multiplier] per
 * [capPeriod]; spend beyond it earns [fallbackMultiplier] (or the card's
 * base rate when null). Rates sharing a [capGroup] share one cap.
 */
data class RewardRate(
    val category: SpendingCategory,
    /** Points (or % for cash back) earned per dollar spent. */
    val multiplier: Double,
    val note: String? = null,
    val capUsd: Int? = null,
    val capPeriod: CapPeriod = CapPeriod.NONE,
    val capGroup: String? = null,
    val fallbackMultiplier: Double? = null,
    val channel: BookingChannel = BookingChannel.ANY,
    /** True when the rate only applies after activation/enrollment (rotating categories, some 5% offers). */
    val requiresActivation: Boolean = false,
    /** Official source for this rule, when it differs from the card's. */
    val sourceUrl: String? = null,
)

/** When a credit's allowance resets. UNKNOWN is shown as unknown, never assumed. */
enum class PeriodBasis(val label: String) { CALENDAR("calendar period"), ANNIVERSARY("card anniversary"), STATEMENT("statement period"), UNKNOWN("reset basis not verified") }

data class CardCredit(
    val label: String,
    val annualValueUsd: Int,
    val frequency: String,
    val description: String,
    val periodBasis: PeriodBasis = PeriodBasis.UNKNOWN,
    /** Lower-cased merchant/description keywords that identify eligible charges and the reimbursement line on a statement. */
    val merchantKeywords: List<String> = emptyList(),
    val requiresEnrollment: Boolean = false,
    val sourceUrl: String? = null,
) {
    /** Monthly sub-limit parsed from frequencies like "monthly ($10/mo)" or "annual ($15-20/mo)". Null when the credit isn't monthly-limited. */
    val monthlySublimitUsd: Int?
        get() = Regex("""\$(\d+)(?:-\d+)?/mo""").find(frequency)?.groupValues?.get(1)?.toIntOrNull()
}

/** Where a card's terms came from and how fresh they are. */
data class RuleProvenance(
    /** Issuer product/terms page. Null when the entry was compiled from secondary research only. */
    val sourceUrl: String?,
    /** ISO date the terms were last checked against the source. */
    val verifiedOn: String,
    val note: String? = null,
) {
    enum class Freshness(val label: String) { VERIFIED("verified recently"), AGING("verify before relying on it"), STALE("stale - may have changed") }

    fun freshness(todayEpochDay: Long = java.time.LocalDate.now().toEpochDay()): Freshness {
        val verified = runCatching { java.time.LocalDate.parse(verifiedOn).toEpochDay() }.getOrNull() ?: return Freshness.STALE
        val age = todayEpochDay - verified
        return when {
            age <= 180 -> Freshness.VERIFIED
            age <= 400 -> Freshness.AGING
            else -> Freshness.STALE
        }
    }
}

/** A non-monetary card perk relevant to trips - lounge, bags, protections - with the condition under which it applies. */
data class TravelPerk(
    val kind: Kind,
    val description: String,
    /** e.g. "when the full fare is charged to this card"; unknown conditions are stated as unknown. */
    val condition: String?,
    val sourceUrl: String? = null,
) {
    enum class Kind(val label: String) {
        LOUNGE("Lounge access"), CHECKED_BAG("Checked bag"), PRIORITY_BOARDING("Priority boarding"), TRIP_DELAY("Trip delay reimbursement"),
        TRIP_CANCELLATION("Trip cancellation/interruption"), BAGGAGE_DELAY("Baggage delay"), RENTAL_CDW("Rental car collision coverage"),
        HOTEL_STATUS("Hotel elite status"), TRAVEL_ACCIDENT("Travel accident insurance"), NO_FOREIGN_FEE("No foreign transaction fees"), OTHER("Other"),
    }
}

enum class LoyaltyProgramKind(val label: String) {
    HOTEL("Hotels"),
    AIRLINE("Airlines"),
    /** Retail, dining, grocery, rideshare and shopping-portal rewards - points, stars, cash-back and gift-card balances. */
    SHOP("Shops & dining"),
}

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
    // Shops, dining and shopping rewards. Sender domains are the transactional/account domains, not ad networks.
    AMAZON("Amazon (gift card & rewards balance)", LoyaltyProgramKind.SHOP, listOf("amazon.com")),
    STARBUCKS_REWARDS("Starbucks Rewards", LoyaltyProgramKind.SHOP, listOf("starbucks.com", "e.starbucks.com")),
    TARGET_CIRCLE("Target Circle", LoyaltyProgramKind.SHOP, listOf("target.com", "e.target.com")),
    WALMART_REWARDS("Walmart Rewards / Walmart Cash", LoyaltyProgramKind.SHOP, listOf("walmart.com", "email.walmart.com")),
    COSTCO_REWARDS("Costco Executive / Citi Costco rewards", LoyaltyProgramKind.SHOP, listOf("costco.com", "online.costco.com")),
    UBER_REWARDS("Uber Cash / Uber One", LoyaltyProgramKind.SHOP, listOf("uber.com")),
    DOORDASH("DoorDash credits & DashPass", LoyaltyProgramKind.SHOP, listOf("doordash.com")),
    SEPHORA_BEAUTY_INSIDER("Sephora Beauty Insider", LoyaltyProgramKind.SHOP, listOf("sephora.com")),
    ULTA_ULTAMATE("Ulta Ultamate Rewards", LoyaltyProgramKind.SHOP, listOf("ulta.com", "e.ulta.com")),
    CVS_EXTRACARE("CVS ExtraCare", LoyaltyProgramKind.SHOP, listOf("cvs.com")),
    WALGREENS_MYWALGREENS("myWalgreens", LoyaltyProgramKind.SHOP, listOf("walgreens.com")),
    KROGER_PLUS("Kroger Plus / fuel points", LoyaltyProgramKind.SHOP, listOf("kroger.com", "ralphs.com", "fredmeyer.com", "kingsoopers.com", "frysfood.com", "smithsfoodanddrug.com")),
    BEST_BUY("My Best Buy", LoyaltyProgramKind.SHOP, listOf("bestbuy.com", "emailinfo.bestbuy.com")),
    CHIPOTLE_REWARDS("Chipotle Rewards", LoyaltyProgramKind.SHOP, listOf("chipotle.com")),
    DUNKIN_REWARDS("Dunkin' Rewards", LoyaltyProgramKind.SHOP, listOf("dunkindonuts.com", "dunkin.com")),
    PANERA_MYPANERA("MyPanera", LoyaltyProgramKind.SHOP, listOf("panerabread.com")),
    RAKUTEN("Rakuten cash back", LoyaltyProgramKind.SHOP, listOf("rakuten.com", "mail.rakuten.com")),
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
    /** Set for cards whose bonus categories rotate or must be chosen each quarter; the user records the current quarter's categories in the app. */
    val rotatingKind: RotatingKind? = null,
    /** Multiplier the rotating categories earn (typically 5). */
    val rotatingMultiplier: Double = 5.0,
    /** Combined spend cap on the rotating categories per quarter, when known. */
    val rotatingCapUsd: Int? = null,
    /** Foreign transaction fee as a percentage; null = not verified (treated as unknown, not zero). */
    val foreignTransactionFeePct: Double? = null,
    val country: String = "US",
    /** Terms source and verification date. Defaults are derived from [dataAsOf] and marked approximate. */
    val provenance: RuleProvenance? = null,
    /** Date these terms took effect (ISO); null = unknown/original. */
    val effectiveFrom: String? = null,
    /** Earlier versions of this card's terms, newest first, each with its own effectiveFrom/effectiveTo window. */
    val previousVersions: List<CardCatalogEntry> = emptyList(),
    val effectiveTo: String? = null,
    val travelPerks: List<TravelPerk> = emptyList(),
    /** Annual fee for each authorized user, when known; null = unknown. */
    val authorizedUserFeeUsd: Int? = null,
) {
    /** Effective provenance: explicit, or an approximate date derived from the catalog's dataAsOf wording. */
    val effectiveProvenance: RuleProvenance
        get() = provenance ?: RuleProvenance(
            sourceUrl = null,
            verifiedOn = if (dataAsOf.contains("2026")) "2026-09-01" else "2025-03-01",
            note = "Approximate: derived from catalog research pass (\"$dataAsOf\"), not an issuer page check.",
        )

    /** The version of this entry in force on [epochDay]. Falls back to the current entry when no dated version covers it. */
    fun versionFor(epochDay: Long): CardCatalogEntry {
        val from = effectiveFrom?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() }
        if (from == null || epochDay >= from) return this
        return previousVersions.firstOrNull { v ->
            val vFrom = v.effectiveFrom?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: Long.MIN_VALUE
            val vTo = v.effectiveTo?.let { runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: Long.MAX_VALUE
            epochDay in vFrom..vTo
        } ?: this
    }

    fun rateFor(category: SpendingCategory): RewardRate =
        categoryRates.firstOrNull { it.category == category }
            ?: RewardRate(category, baseMultiplier)

    /** Estimated cents of value per dollar spent in [category], using [RewardCurrency]'s estimated point value. */
    fun estimatedValueCentsPerDollar(category: SpendingCategory): Double =
        rateFor(category).multiplier * rewardCurrency.estValueCentsPerPoint
}
