package com.travelbenefits.app.domain.model

/** One ranked entry in a "best card for this category" result. */
data class RecommendationEntry(
    val walletCard: WalletCard,
    val displayName: String,
    val category: SpendingCategory,
    val multiplier: Double,
    val rewardCurrency: RewardCurrency,
    val estimatedValueCentsPerDollar: Double,
    val reason: String,
    /** True when this ranking is only a rough estimate (e.g. wallet card came from a live lookup, not the catalog). */
    val isEstimate: Boolean,
)
