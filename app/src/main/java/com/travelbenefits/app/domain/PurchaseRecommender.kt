package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.CapPeriod
import com.travelbenefits.app.domain.model.CapUsage
import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.PurchaseOption
import com.travelbenefits.app.domain.model.PurchaseQuery
import com.travelbenefits.app.domain.model.PurchaseRecommendation
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.RewardRate
import com.travelbenefits.app.domain.model.SpendingCategory
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * "Which card for this purchase?" with the conditions that actually change
 * the answer: remaining caps (a $100 purchase with $50 left under a 5%
 * cap earns 5% on $50 and the fallback on $50), channel requirements,
 * activation, foreign transaction fees and the user's point valuations.
 *
 * Deterministic and side-effect free. Welcome-bonus and status thresholds
 * are reported as notes, never added to the value, so no purchase is
 * credited with a whole bonus and nothing nudges the user to overspend.
 */
class PurchaseRecommender @Inject constructor() {

    fun recommend(
        query: PurchaseQuery,
        cards: List<ResolvedWalletCard>,
        capUsage: List<CapUsage> = emptyList(),
        valuations: Map<RewardCurrency, Double> = emptyMap(),
    ): PurchaseRecommendation {
        val category = query.category ?: SpendingCategory.OTHER
        val options = cards.mapNotNull { card -> option(card, query, category, capUsage, valuations) }
            .sortedWith(compareByDescending<PurchaseOption> { it.netValueUsd }.thenByDescending { it.confidence.ordinal * -1 })
        val warning = when {
            query.category == null -> "Merchant category unknown - showing 'everything else' rates. Pick the category to refine."
            query.categoryConfidence == Confidence.LOW -> "Category guessed (${query.categorySource ?: "keyword"}) - issuers code merchants differently; confirm before relying on it."
            else -> null
        }
        return PurchaseRecommendation(query, options, warning)
    }

    private fun option(
        card: ResolvedWalletCard,
        query: PurchaseQuery,
        category: SpendingCategory,
        capUsage: List<CapUsage>,
        valuations: Map<RewardCurrency, Double>,
    ): PurchaseOption? {
        val amount = query.amountUsd
        val conditions = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val thresholds = mutableListOf<String>()
        var confidence = Confidence.HIGH

        val entry = (card as? ResolvedWalletCard.Catalog)?.entry
        if (entry == null) {
            // Looked-up card: no structured rules. Assume the base 1x and say so.
            val currency = RewardCurrency.GENERIC_POINTS
            val value = amount * 0.01
            return PurchaseOption(
                card = card, categoryUsed = category, rewardsEarned = amount, rewardCurrency = currency, rewardsValueUsd = value,
                foreignFeeUsd = null, netValueUsd = value, effectiveRateLabel = "~1x (assumed)", confidence = Confidence.LOW,
                conditions = listOf("No structured rules for this card"), reasons = listOf("Card came from a live lookup; rates are not modelled."),
                warnings = listOf("Foreign fee unknown"), thresholdNotes = emptyList(), amountAtHeadlineRate = 0.0, isEstimate = true,
            )
        }
        if (entry.isDiscontinued) return null
        val versioned = entry.versionFor(query.epochDay)
        val currency = versioned.rewardCurrency
        val centsPerPoint = valuations[currency] ?: currency.estValueCentsPerPoint

        // 1. Pick the applicable rate: rotating (if it covers this category), else the category rate whose channel matches, else base.
        val rotating = card.rotating?.takeIf { category in it.categories }
        val categoryRate = versioned.categoryRates.filter { it.category == category }
            .firstOrNull { it.channel == BookingChannel.ANY || it.channel == query.channel }
        val channelMissed = versioned.categoryRates.filter { it.category == category }.any { it.channel != BookingChannel.ANY && it.channel != query.channel }
        if (channelMissed && categoryRate == null) {
            conditions += "Higher rate only when ${versioned.categoryRates.first { it.category == category }.channel.label}"
        }
        val base = versioned.baseMultiplier

        var headline: Double
        var fallback: Double
        var capUsd: Int? = null
        var capKey: String? = null
        var capPeriod = CapPeriod.NONE
        var requiresActivation = false
        var rateNote: String? = null
        if (rotating != null && versioned.rotatingMultiplier > (categoryRate?.multiplier ?: base)) {
            headline = versioned.rotatingMultiplier
            fallback = base
            capUsd = versioned.rotatingCapUsd
            capKey = "rotating:${rotating.quarterKey}"
            capPeriod = CapPeriod.QUARTERLY
            requiresActivation = true
            rateNote = "${rotating.quarterKey.replace("-", " ")} rotating category"
            if (!rotating.activated) {
                warnings += "Rotating category not marked activated - 5% only applies after activation."
                confidence = Confidence.LOW
            }
        } else if (categoryRate != null) {
            headline = categoryRate.multiplier
            fallback = categoryRate.fallbackMultiplier ?: base
            capUsd = categoryRate.capUsd
            capKey = categoryRate.capGroup ?: categoryRate.category.name
            capPeriod = categoryRate.capPeriod
            requiresActivation = categoryRate.requiresActivation
            rateNote = categoryRate.note
            if (categoryRate.channel != BookingChannel.ANY) conditions += "Applies when ${categoryRate.channel.label}"
            if (requiresActivation) {
                conditions += "Requires activation/enrollment"
                confidence = minOf(confidence, Confidence.MEDIUM)
            }
        } else {
            headline = base
            fallback = base
            reasons += "No bonus category for ${category.label.lowercase()} - base rate."
        }
        rateNote?.let { conditions += it }

        // 2. Cap-aware split.
        var amountAtHeadline = amount
        var amountAtFallback = 0.0
        if (capUsd != null && capPeriod != CapPeriod.NONE && capKey != null) {
            val usage = capUsage.firstOrNull { it.walletCardId == card.walletCard.id && it.capKey == capKey }
            val spent = usage?.spentUsd
            val remaining = max(0.0, capUsd - (spent ?: 0.0))
            amountAtHeadline = min(amount, remaining)
            amountAtFallback = amount - amountAtHeadline
            if (spent == null) {
                conditions += "Cap $${fmt(capUsd.toDouble())} ${capPeriod.label}; spend to date unknown - assumed unused"
                confidence = minOf(confidence, Confidence.MEDIUM)
            } else {
                conditions += "Cap $${fmt(capUsd.toDouble())} ${capPeriod.label}; $${fmt(remaining)} remaining (${usage.source})"
                if (amountAtFallback > 0) reasons += "$${fmt(amountAtHeadline)} earns ${rate(headline, currency)}, the remaining $${fmt(amountAtFallback)} earns ${rate(fallback, currency)} above the cap."
            }
        }

        // 3. Rewards and value.
        val rewards = amountAtHeadline * headline + amountAtFallback * fallback
        val rewardsValue = rewards * centsPerPoint / 100.0
        val fee: Double? = if (query.isForeign) {
            when (val pct = versioned.foreignTransactionFeePct) {
                null -> { warnings += "Foreign transaction fee not verified for this card"; confidence = minOf(confidence, Confidence.MEDIUM); null }
                0.0 -> { reasons += "No foreign transaction fee."; 0.0 }
                else -> { conditions += "Foreign transaction fee ${pct}%"; amount * pct / 100.0 }
            }
        } else {
            null
        }
        val net = rewardsValue - (fee ?: 0.0)
        if (amountAtFallback == 0.0) reasons += "Earns ${rate(headline, currency)} on ${category.label.lowercase()}" + (if (rotating != null) " (rotating)" else "") + "."
        if (valuations.containsKey(currency)) reasons += "Valued at your ${centsPerPoint}¢ per point." else reasons += "Valued at the app's ${centsPerPoint}¢ per point estimate."
        val freshness = versioned.effectiveProvenance.freshness(query.epochDay)
        if (freshness != com.travelbenefits.app.domain.model.RuleProvenance.Freshness.VERIFIED) {
            warnings += "Card terms ${freshness.label} (checked ${versioned.effectiveProvenance.verifiedOn})"
            confidence = minOf(confidence, Confidence.MEDIUM)
        }

        // 4. Threshold notes (never added to value).
        val wc = card.walletCard
        if (wc.bonusSpendRequiredUsd != null && wc.bonusEarnedAt == null) {
            val remaining = (wc.bonusSpendRequiredUsd - (wc.bonusSpendToDateUsd ?: 0L)).coerceAtLeast(0)
            if (remaining > 0) thresholds += "Counts toward the welcome bonus ($${"%,d".format(remaining)} still needed) - only relevant if you'd spend this anyway."
        }
        if (query.category == null) confidence = minOf(confidence, Confidence.MEDIUM)

        return PurchaseOption(
            card = card, categoryUsed = category, rewardsEarned = rewards, rewardCurrency = currency, rewardsValueUsd = rewardsValue,
            foreignFeeUsd = fee, netValueUsd = net,
            effectiveRateLabel = if (amountAtFallback > 0) "${rate(headline, currency)} → ${rate(fallback, currency)}" else rate(headline, currency),
            confidence = confidence, conditions = conditions, reasons = reasons, warnings = warnings, thresholdNotes = thresholds,
            amountAtHeadlineRate = amountAtHeadline, isEstimate = true,
        )
    }

    private fun rate(m: Double, c: RewardCurrency): String {
        val t = if (m == m.toLong().toDouble()) m.toLong().toString() else m.toString()
        return if (c.displayAsPercent) "$t%" else "${t}x"
    }

    private fun fmt(d: Double): String = String.format("%,.0f", d)

    private fun minOf(a: Confidence, b: Confidence): Confidence = if (a.ordinal >= b.ordinal) a else b
}
