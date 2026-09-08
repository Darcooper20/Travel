package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.RecommendationEntry
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.SpendingCategory
import javax.inject.Inject

/**
 * Ranks the user's wallet by estimated value-per-dollar for a spending
 * category. Catalog-backed cards get an exact-as-the-catalog-data-allows
 * ranking; cards resolved from a live lookup only have free-text benefit
 * descriptions, so their ranking is a best-effort estimate flagged as such
 * via [RecommendationEntry.isEstimate] rather than presented as precise.
 */
class RecommendationEngine @Inject constructor() {

    private val leadingMultiplierRegex = Regex("""(\d+(?:\.\d+)?)\s*[xX%]""")

    fun rank(cards: List<ResolvedWalletCard>, category: SpendingCategory): List<RecommendationEntry> =
        cards.map { toEntry(it, category) }.sortedByDescending { it.estimatedValueCentsPerDollar }

    private fun toEntry(card: ResolvedWalletCard, category: SpendingCategory): RecommendationEntry = when (card) {
        is ResolvedWalletCard.Catalog -> {
            val rate = card.entry.rateFor(category)
            RecommendationEntry(
                walletCard = card.walletCard,
                displayName = card.displayName,
                category = category,
                multiplier = rate.multiplier,
                rewardCurrency = card.entry.rewardCurrency,
                estimatedValueCentsPerDollar = card.entry.estimatedValueCentsPerDollar(category),
                reason = buildString {
                    append("Earns ${formatMultiplier(rate.multiplier, card.entry.rewardCurrency)} on ${category.label.lowercase()}")
                    rate.note?.let { append(" ($it)") }
                    append(".")
                },
                isEstimate = false,
            )
        }
        is ResolvedWalletCard.Custom -> {
            val highlight = card.lookup?.categoryHighlights?.firstOrNull {
                it.categoryLabel.contains(category.label, ignoreCase = true) ||
                    category.label.contains(it.categoryLabel, ignoreCase = true)
            }
            val extractedMultiplier = highlight?.let { leadingMultiplierRegex.find(it.multiplierDescription)?.groupValues?.get(1)?.toDoubleOrNull() }
            val multiplier = extractedMultiplier ?: 1.0
            RecommendationEntry(
                walletCard = card.walletCard,
                displayName = card.displayName,
                category = category,
                multiplier = multiplier,
                rewardCurrency = RewardCurrency.GENERIC_POINTS,
                // Conservative 1 cent/point assumption since we don't know this card's actual currency.
                estimatedValueCentsPerDollar = if (card.lookup == null) 0.0 else multiplier,
                reason = when {
                    card.lookup == null -> "No benefit data yet - open this card to look it up."
                    highlight != null -> "From a live lookup: ${highlight.multiplierDescription} for ${highlight.categoryLabel}."
                    else -> "From a live lookup: no specific rate found for ${category.label.lowercase()}; showing the card's base rate as a rough estimate."
                },
                isEstimate = true,
            )
        }
    }

    private fun formatMultiplier(multiplier: Double, currency: RewardCurrency): String {
        val trimmed = if (multiplier == multiplier.toLong().toDouble()) multiplier.toLong().toString() else multiplier.toString()
        return if (currency.displayAsPercent) "$trimmed%" else "${trimmed}x points"
    }
}
