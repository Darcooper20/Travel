package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.CapPeriod
import com.travelbenefits.app.domain.model.CapUsage
import com.travelbenefits.app.domain.model.MerchantOffer
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
        offers: List<MerchantOffer> = emptyList(),
    ): PurchaseRecommendation {
        val category = query.category ?: SpendingCategory.OTHER
        // Same rule as RecommendationEngine: an unconfirmed card has no trustworthy
        // rate data, so it is left out rather than quoted with invented numbers.
        val options = cards.filterNot { it.walletCard.needsConfirmation }
            .mapNotNull { card -> option(card, query, category, capUsage, valuations) }
            .map { opt -> applyOffers(opt, query, offers) }
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
            // Outstanding only when the user has not ticked "activated" for this quarter.
            requiresActivation = !rotating.activated
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
        } else {
            headline = base
            fallback = base
            reasons += "No bonus category for ${category.label.lowercase()} - base rate."
        }
        rateNote?.let { conditions += it }
        // Applies to every branch above. It used to sit inside the category branch only,
        // so a rotating 5% was quoted at full confidence with no activation caveat.
        if (requiresActivation) {
            conditions += "Requires activation/enrollment"
            confidence = minOf(confidence, Confidence.MEDIUM)
        }

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

        // 3. Rewards and value. Cash-back "multipliers" are percentages, so rewards are dollars; points cards earn points.
        val rewardUnits = amountAtHeadline * headline + amountAtFallback * fallback
        val rewards = if (currency.displayAsPercent) rewardUnits / 100.0 else rewardUnits
        val rewardsValue = rewardUnits * centsPerPoint / 100.0
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

    /**
     * Stacks enrolled card-linked offers for this merchant on top of the card's
     * rewards; unenrolled offers and portals become notes. Offer credits are
     * clawed back on refunds, so the note says so.
     */
    private fun applyOffers(option: PurchaseOption, query: PurchaseQuery, offers: List<MerchantOffer>): PurchaseOption {
        val merchant = query.merchant?.lowercase()?.trim() ?: return option
        if (merchant.isBlank()) return option
        val today = query.epochDay
        val matching = offers.filter { o -> merchant.contains(o.merchant.lowercase()) || o.merchant.lowercase().contains(merchant) }
            .filter { it.expiresEpochDay == null || it.expiresEpochDay >= today }
        if (matching.isEmpty()) return option
        var extra = 0.0
        val reasons = option.reasons.toMutableList()
        val conditions = option.conditions.toMutableList()
        val notes = option.thresholdNotes.toMutableList()
        matching.forEach { o ->
            val forThisCard = o.walletCardId == null || o.walletCardId == option.card.walletCard.id
            val meetsMin = o.minSpendUsd == null || query.amountUsd >= o.minSpendUsd
            val value = when {
                o.valueUsd != null -> o.valueUsd
                o.percentBack != null -> query.amountUsd * o.percentBack / 100.0
                else -> 0.0
            }
            when {
                o.kind == MerchantOffer.Kind.CARD_OFFER && forThisCard && o.enrolled && meetsMin -> { extra += value; reasons += "Enrolled offer: ${o.description} (+$${"%,.2f".format(value)}; clawed back if refunded)." }
                o.kind == MerchantOffer.Kind.CARD_OFFER && forThisCard && !o.enrolled -> notes += "Offer available but not enrolled: ${o.description} - enroll first; not counted."
                o.kind == MerchantOffer.Kind.CARD_OFFER && forThisCard && !meetsMin -> notes += "Offer needs $${"%,.0f".format(o.minSpendUsd ?: 0.0)}+ spend: ${o.description} - not counted."
                o.kind == MerchantOffer.Kind.PORTAL -> notes += "Portal: ${o.description} - stacks on any card if you start at the portal (not counted; portal payouts can be reversed on returns)."
                o.kind == MerchantOffer.Kind.COUPON -> notes += "Coupon: ${o.description} - check it combines with card offers."
                else -> Unit
            }
        }
        if (extra == 0.0 && reasons.size == option.reasons.size) return option.copy(thresholdNotes = notes)
        return option.copy(rewardsValueUsd = option.rewardsValueUsd + extra, netValueUsd = option.netValueUsd + extra, reasons = reasons, conditions = conditions, thresholdNotes = notes)
    }

    private fun rate(m: Double, c: RewardCurrency): String {
        val t = if (m == m.toLong().toDouble()) m.toLong().toString() else m.toString()
        return if (c.displayAsPercent) "$t%" else "${t}x"
    }

    private fun fmt(d: Double): String = String.format("%,.0f", d)

    private fun minOf(a: Confidence, b: Confidence): Confidence = if (a.ordinal >= b.ordinal) a else b
}
