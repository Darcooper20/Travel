package com.travelbenefits.app.domain.model

/** How sure the engine is that a result reflects the issuer's actual treatment. */
enum class Confidence(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

/** A purchase the user is about to make. Unknowns stay null. */
data class PurchaseQuery(
    val merchant: String?,
    val category: SpendingCategory?,
    val amountUsd: Double,
    val currency: String = "USD",
    /** True when the charge will be in a foreign currency or abroad (foreign transaction fees apply). */
    val isForeign: Boolean = false,
    val channel: BookingChannel = BookingChannel.ANY,
    val epochDay: Long = java.time.LocalDate.now().toEpochDay(),
    /** Where the category came from, for the UI to flag uncertainty. */
    val categoryConfidence: Confidence = Confidence.MEDIUM,
    val categorySource: String? = null,
)

/** Spend already counted against a cap in the current cap period, per card and cap key (category name or cap group). */
data class CapUsage(val walletCardId: Long, val capKey: String, val spentUsd: Double, val source: String)

/** One ranked option for paying for a purchase. Money in dollars, rewards in the card's own units. */
data class PurchaseOption(
    val card: ResolvedWalletCard,
    val categoryUsed: SpendingCategory,
    /** Rewards earned in the card's currency (points or cash-back dollars) for this purchase. */
    val rewardsEarned: Double,
    val rewardCurrency: RewardCurrency,
    /** Estimated dollar value of [rewardsEarned] at the valuation used. */
    val rewardsValueUsd: Double,
    /** Foreign transaction fee, when the query is foreign and the fee is known; null = fee unknown. */
    val foreignFeeUsd: Double?,
    /** rewardsValueUsd minus known fees. */
    val netValueUsd: Double,
    val effectiveRateLabel: String,
    val confidence: Confidence,
    val conditions: List<String>,
    val reasons: List<String>,
    val warnings: List<String>,
    /** Threshold effects reported separately and never added to [netValueUsd]. */
    val thresholdNotes: List<String>,
    /** Portion of the amount that earned the headline rate before a cap. */
    val amountAtHeadlineRate: Double,
    val isEstimate: Boolean,
)

data class PurchaseRecommendation(
    val query: PurchaseQuery,
    val options: List<PurchaseOption>,
    /** Non-null when the category is uncertain and the user should confirm it. */
    val categoryWarning: String?,
)
