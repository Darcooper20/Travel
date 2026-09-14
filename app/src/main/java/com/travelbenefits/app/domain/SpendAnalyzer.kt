package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CardSpend
import com.travelbenefits.app.domain.model.CategorySpend
import com.travelbenefits.app.domain.model.PlaidAccount
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendPeriod
import com.travelbenefits.app.domain.model.SpendReport
import com.travelbenefits.app.domain.model.SpendTransaction
import javax.inject.Inject

/**
 * The "wrong card" analysis. For each category in the period: what was
 * spent on which card, the estimated value that earned (using the same
 * per-dollar estimates as the best-card picker), and what the best wallet
 * card would have earned. Estimates only: they use the catalog's rates and
 * the app's per-point valuations, not the issuer's actual posting.
 */
class SpendAnalyzer @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
) {
    fun analyze(
        transactions: List<SpendTransaction>,
        accounts: List<PlaidAccount>,
        cards: List<ResolvedWalletCard>,
        period: SpendPeriod,
    ): SpendReport {
        val range = period.range()
        val inPeriod = transactions.filter { it.epochDay in range && !it.pending && it.amountUsd > 0 && it.category != null }
        val accountsById = accounts.associateBy { it.accountId }
        val cardsById = cards.associateBy { it.walletCard.id }
        val usable = cards.filter { (it as? ResolvedWalletCard.Catalog)?.entry?.isDiscontinued != true }

        val categories = inPeriod.groupBy { it.category!! }.map { (category, txns) ->
            val ranking = recommendationEngine.rank(usable, category)
            val best = ranking.firstOrNull()?.takeIf { it.estimatedValueCentsPerDollar > 0 }
            val bestCard = best?.let { cardsById[it.walletCard.id] }
            val byCard = txns.groupBy { it.walletCardId }.map { (cardId, group) ->
                val card = cardId?.let { cardsById[it] }
                val spend = group.sumOf { it.amountUsd }
                val entry = card?.let { c -> ranking.firstOrNull { it.walletCard.id == c.walletCard.id } }
                val centsPerDollar = entry?.estimatedValueCentsPerDollar?.takeIf { it.isFinite() && it > 0 } ?: 0.0
                CardSpend(
                    card = card,
                    accountLabel = card?.displayName ?: group.firstOrNull()?.let { accountsById[it.accountId]?.label } ?: "Unmapped account",
                    spendUsd = spend,
                    valueEarnedUsd = spend * centsPerDollar / 100.0,
                    multiplierLabel = entry?.let { formatRate(it.multiplier, it.rewardCurrency.displayAsPercent) } ?: "?",
                )
            }.sortedByDescending { it.spendUsd }
            val spend = byCard.sumOf { it.spendUsd }
            val earned = byCard.sumOf { it.valueEarnedUsd }
            val bestCents = best?.estimatedValueCentsPerDollar ?: 0.0
            CategorySpend(
                category = category,
                spendUsd = spend,
                byCard = byCard,
                bestCard = bestCard,
                bestMultiplierLabel = best?.let { formatRate(it.multiplier, it.rewardCurrency.displayAsPercent) } ?: "-",
                valueEarnedUsd = earned,
                valueIfBestUsd = spend * bestCents / 100.0,
            )
        }.sortedByDescending { it.missedUsd }

        val total = inPeriod.sumOf { it.amountUsd }
        val mapped = inPeriod.filter { it.walletCardId != null }.sumOf { it.amountUsd }
        return SpendReport(
            period = period,
            totalSpendUsd = total,
            mappedSpendUsd = mapped,
            unmappedSpendUsd = total - mapped,
            categories = categories,
            transactionCount = inPeriod.size,
        )
    }

    /** Spend on one wallet card since a date, for welcome-bonus progress. */
    fun spendSince(transactions: List<SpendTransaction>, walletCardId: Long, fromEpochDay: Long): Double =
        transactions.filter { it.walletCardId == walletCardId && it.epochDay >= fromEpochDay && !it.pending && it.amountUsd > 0 }.sumOf { it.amountUsd }

    private fun formatRate(multiplier: Double, asPercent: Boolean): String {
        val trimmed = if (multiplier == multiplier.toLong().toDouble()) multiplier.toLong().toString() else multiplier.toString()
        return if (asPercent) "$trimmed%" else "${trimmed}x"
    }
}
