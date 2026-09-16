package com.travelbenefits.app.domain.model

/** What a program counts toward elite status - drives how tier progress is labelled and computed. */
enum class QualifyingMetric(val label: String, val unit: String) {
    NIGHTS("Elite nights", "nights"),
    STAYS("Stays", "stays"),
    QUALIFYING_DOLLARS("Qualifying spend", "USD"),
    QUALIFYING_POINTS("Qualifying points", "pts"),
    FLIGHTS("Qualifying flights", "flights"),
    STATUS_POINTS("Status points", "pts"),
    TILES("Tiles", "tiles"),
}

/**
 * One rung of a program's elite ladder. [threshold] is in units of the
 * program's [ProgramProfile.qualifyingMetric]; null means the tier exists
 * but its threshold hasn't been encoded (so no progress bar is drawn).
 */
data class TierLevel(
    val name: String,
    val threshold: Int?,
    /** Alternate way to qualify, purely informational (e.g. "or 20 stays"). */
    val altQualification: String? = null,
    /** Short list of headline perks - shown when a member is one tier away, so they know what they're chasing. */
    val perks: String? = null,
)

/**
 * When a program forfeits points. [inactivityMonths] null = points don't
 * expire on inactivity (they may still be lost if the account closes).
 */
data class ExpirationPolicy(
    val inactivityMonths: Int?,
    val summary: String,
)

/** Whether a program's balance is a count of points/stars/miles or a dollar amount (gift card, cash back, rewards dollars). */
enum class BalanceUnit { POINTS, DOLLARS }

/** A hotel award-night price band, for the "what can I book with these points" estimate. Rough, not a live chart. */
data class AwardPriceBand(
    val lowNightPoints: Int,
    val typicalNightPoints: Int,
    val highNightPoints: Int,
    val note: String,
)

/**
 * Everything the app knows about a loyalty program beyond the enum: elite
 * ladder, expiration rules, how many points a paid stay/flight earns, an
 * estimated point value, and where to search awards. Every number here is a
 * hand-curated snapshot ([dataAsOf]) that WILL drift - the UI must always
 * present it as "as understood on <date>, verify with the program".
 */
data class ProgramProfile(
    val program: LoyaltyProgram,
    val qualifyingMetric: QualifyingMetric,
    /** Ordered lowest to highest; the base (no-status) level is implicit and not listed. */
    val tiers: List<TierLevel>,
    val expiration: ExpirationPolicy,
    /** Estimated cents per point - a third-party style valuation, not a redemption guarantee. */
    val estValueCentsPerPoint: Double,
    /** Base points a no-status member earns per US dollar of eligible spend with the program (paid stays / flights). Null when it isn't dollar-based. */
    val basePointsPerDollar: Double?,
    /** Elite earning bonus by tier name (e.g. "Gold" -> 0.25 for +25%). Missing tiers get 0. */
    val eliteBonusByTier: Map<String, Double> = emptyMap(),
    val awardBand: AwardPriceBand? = null,
    /** Where a member checks/redeems - opened in the browser from the app. */
    val accountUrl: String,
    val awardSearchUrl: String,
    /** Lower-cased brand/provider keywords that identify a booking as belonging to this program (used for the trip "earning check"). */
    val brandKeywords: List<String>,
    val dataAsOf: String,
    val notes: String? = null,
    val balanceUnit: BalanceUnit = BalanceUnit.POINTS,
    /** Whether award (points) stays/flights count toward elite status. null = not verified - shown as unknown. */
    val awardStaysCountForStatus: Boolean? = null,
    /** How the qualification year runs, e.g. "calendar year"; null = not verified. */
    val qualificationYear: String? = null,
    /** Program's rule on pooling/transferring points between members; null = not verified. */
    val poolingRule: String? = null,
    /**
     * False for a program discovered in email that the catalog knows nothing
     * about. Its [estValueCentsPerPoint] is a placeholder, not an estimate, so
     * callers must report the value as unknown rather than compute one.
     */
    val valuationIsKnown: Boolean = true,
) {
    /** "42,500" for points programs, "$42" for dollar balances. */
    fun formatBalance(amount: Long): String = when (balanceUnit) {
        BalanceUnit.POINTS -> String.format("%,d", amount)
        BalanceUnit.DOLLARS -> String.format("$%,d", amount)
    }

    /** Dollar value of [amount] using the estimate (for dollar balances this is the amount itself). */
    fun estimatedValueUsd(amount: Long): Double = amount * estValueCentsPerPoint / 100.0

    /** Matches tier names loosely ("Platinum Elite" ~ "Platinum", "Medallion Gold" ~ "Gold"). */
    fun findTier(tierName: String?): TierLevel? {
        if (tierName.isNullOrBlank()) return null
        val needle = tierName.lowercase()
        // Prefer the longest tier name contained in the string so "Platinum Pro" doesn't collapse to "Platinum".
        return tiers
            .filter { needle.contains(it.name.lowercase()) }
            .maxByOrNull { it.name.length }
    }

    fun eliteBonusFor(tierName: String?): Double {
        val tier = findTier(tierName) ?: return 0.0
        return eliteBonusByTier[tier.name] ?: 0.0
    }
}

/** A transferable currency -> loyalty program transfer relationship. */
data class TransferPartner(
    val from: RewardCurrency,
    val to: LoyaltyProgram,
    /** Program points received per 1 point of [from]. 1.0 = 1:1, 2.0 = 1:2, 0.5 = 2:1. */
    val ratio: Double,
    val note: String? = null,
    val dataAsOf: String,
) {
    fun ratioLabel(): String = when {
        ratio == 1.0 -> "1:1"
        ratio > 1.0 -> "1:${trim(ratio)}"
        else -> "${trim(1 / ratio)}:1"
    }

    private fun trim(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else String.format("%.2f", d)
}
