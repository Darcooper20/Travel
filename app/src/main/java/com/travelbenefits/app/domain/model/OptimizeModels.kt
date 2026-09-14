package com.travelbenefits.app.domain.model

/** Progress toward the next elite tier in one program, for progress bars and "X nights to Gold" copy. */
data class TierProgress(
    val program: LoyaltyProgram,
    val currentTier: TierLevel?,
    val nextTier: TierLevel?,
    val metric: QualifyingMetric,
    /** Whatever the user (or an email) reported as their year-to-date qualifying activity; null = unknown. */
    val currentValue: Int?,
    val fraction: Float?,
    val remaining: Int?,
)

/** Input to the cash-vs-points calculator. */
data class CashVsPointsInput(
    val program: LoyaltyProgram,
    val cashPriceUsd: Double,
    val pointsRequired: Long,
    val awardTaxesFeesUsd: Double = 0.0,
    /** Card the cash booking would go on, so the points it would earn count as a cost of redeeming. */
    val payWithCard: ResolvedWalletCard? = null,
    val memberTier: String? = null,
)

data class CashVsPointsResult(
    val input: CashVsPointsInput,
    /** Cents of cash saved per point redeemed. */
    val centsPerPoint: Double,
    /** The program's estimated value benchmark. */
    val benchmarkCentsPerPoint: Double,
    /** Program points a paid stay would have earned (base + elite bonus) - forgone when redeeming. */
    val programPointsForgone: Long,
    /** Card rewards a paid stay would have earned, in estimated dollars. */
    val cardRewardsForgoneUsd: Double,
    /** Estimated value of everything forgone by redeeming, in dollars. */
    val forgoneValueUsd: Double,
    /** Net cash-equivalent advantage of redeeming (positive = redeem, negative = pay cash). */
    val netAdvantageOfPointsUsd: Double,
    val recommendation: Recommendation,
    val explanation: String,
) {
    enum class Recommendation { USE_POINTS, PAY_CASH, TOSS_UP }
}

/** One way to get more points into a target program from the user's wallet. */
data class TransferOption(
    val walletCard: ResolvedWalletCard,
    val partner: TransferPartner,
    /** How many program points 1,000 points of the card's currency become. */
    val programPointsPer1000: Int,
    /** Estimated cents of value per card point when transferred here vs. the card currency's own estimate. */
    val valueUpliftCents: Double,
)

/** Best card to put spend on when the goal is more points in one specific program. */
data class EarnPlanEntry(
    val walletCard: ResolvedWalletCard,
    val category: SpendingCategory,
    val cardMultiplier: Double,
    val cardCurrency: RewardCurrency,
    /** Program points per dollar after any transfer ratio; null when the card can't feed this program at all. */
    val programPointsPerDollar: Double?,
    val via: String,
    val isEstimate: Boolean,
)
