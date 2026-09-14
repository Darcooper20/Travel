package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.CardValueAnalysis
import com.travelbenefits.app.domain.model.CardValueScenario
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.DuplicateBenefit
import com.travelbenefits.app.domain.model.LedgerEntry
import com.travelbenefits.app.domain.model.LedgerEntryKind
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.SpendTransaction
import com.travelbenefits.app.domain.model.TravelPerk
import com.travelbenefits.app.domain.model.ValueLine
import java.time.LocalDate
import javax.inject.Inject

/**
 * Keep / downgrade / cancel maths. Realized value comes only from records
 * (transactions, posted credits, used certificates, user-valued perks);
 * forecast value uses run-rates and says so. Nothing is counted twice:
 * points are valued from spend on this card only, credits from the ledger
 * only, perks only when the user has put a number on them.
 */
class CardValueAnalyzer @Inject constructor() {

    data class Inputs(
        val card: ResolvedWalletCard,
        val transactions: List<SpendTransaction>,
        val ledger: List<LedgerEntry>,
        val credits: List<CreditStatus>,
        val certificates: List<BenefitItem>,
        /** User's annual dollar value for each perk kind on this card (e.g. lounge = 200). Absent = not counted. */
        val perkValuesUsd: Map<TravelPerk.Kind, Double>,
        val authorizedUsers: Int,
        val valuations: Map<RewardCurrency, Double>,
        /** Welcome bonus points the user recorded, if any. */
        val bonusPoints: Long?,
        val today: LocalDate = LocalDate.now(),
        val otherCardsEarningSameCurrency: Int = 0,
        val upcomingTripsPaidWithCard: Int = 0,
    )

    fun analyze(input: Inputs): CardValueAnalysis? {
        val card = input.card as? ResolvedWalletCard.Catalog ?: return null
        val entry = card.entry
        val id = card.walletCard.id
        val yearAgo = input.today.minusYears(1).toEpochDay()
        val cents = input.valuations[entry.rewardCurrency] ?: entry.rewardCurrency.estValueCentsPerPoint
        val assumptions = mutableListOf<String>()
        assumptions += "Points valued at ${cents}¢ each${if (input.valuations.containsKey(entry.rewardCurrency)) " (your setting)" else " (app estimate)"}."

        // Realized rewards from this card's transactions (needs Plaid mapping).
        val txns = input.transactions.filter { it.walletCardId == id && it.epochDay >= yearAgo && !it.pending && it.amountUsd > 0 && it.category != null }
        val realizedRewards = txns.sumOf { t ->
            val rate = card.rotating?.takeIf { t.category in it.categories }?.let { entry.rotatingMultiplier } ?: entry.rateFor(t.category!!).multiplier
            t.amountUsd * rate
        }
        val realizedRewardsUsd = realizedRewards * cents / 100.0
        val coverage = if (txns.isEmpty()) "No transactions mapped to this card - rewards value unknown (link the account or enter spend)." else "${txns.size} transactions over the last 12 months."

        val creditsReceived = input.ledger.filter { it.walletCardId == id && it.epochDay >= yearAgo && !it.needsConfirmation }
            .sumOf { e -> when (e.kind) { LedgerEntryKind.POSTED, LedgerEntryKind.ADJUSTMENT -> e.amountCents; LedgerEntryKind.REVERSED -> -e.amountCents; else -> 0L } } / 100.0
        val unknownUsage = input.ledger.any { it.walletCardId == id && it.kind == LedgerEntryKind.USED_UNKNOWN_AMOUNT && it.epochDay >= yearAgo }
        val certificatesUsed = input.certificates.filter { it.walletCardId == id && it.usedAt != null && it.usedAt / 86_400_000L >= yearAgo }.sumOf { it.valueUsd ?: 0.0 }
        val perkValue = entry.travelPerks.sumOf { input.perkValuesUsd[it.kind] ?: 0.0 }
        val auFees = (entry.authorizedUserFeeUsd ?: 0) * input.authorizedUsers.toDouble()
        if (entry.authorizedUserFeeUsd == null && input.authorizedUsers > 0) assumptions += "Authorized-user fee not verified for this card - not counted."

        val realizedLines = listOf(
            ValueLine("Rewards earned on spend", realizedRewardsUsd, isEstimate = true, note = if (txns.isEmpty()) "no mapped transactions" else null),
            ValueLine("Statement credits received", creditsReceived, isEstimate = false, note = if (unknownUsage) "plus usage with unrecorded amounts" else null),
            ValueLine("Certificates used", certificatesUsed, isEstimate = certificatesUsed > 0),
            ValueLine("Perks you valued", perkValue, isEstimate = true, note = if (perkValue == 0.0) "set values per perk to count them" else null),
            ValueLine("Annual fee", -entry.annualFeeUsd.toDouble(), isEstimate = false),
            ValueLine("Authorized user fees", -auFees, isEstimate = false),
        )
        val realizedNet = realizedLines.sumOf { it.amountUsd }

        // Forecast: run-rate of the same lines.
        val creditUtilization = if (input.credits.any { it.walletCard.walletCard.id == id }) {
            val total = input.credits.filter { it.walletCard.walletCard.id == id }.sumOf { it.allowanceCents }
            val used = input.credits.filter { it.walletCard.walletCard.id == id }.sumOf { it.receivedCents }
            if (total > 0) used.toDouble() / total else 0.0
        } else 0.0
        val annualCreditAllowance = entry.credits.sumOf { it.annualValueUsd }.toDouble()
        val forecastCredits = annualCreditAllowance * creditUtilization
        assumptions += "Forecast credits assume you keep using ${(creditUtilization * 100).toInt()}% of the allowance (current period usage)."
        val forecastLines = listOf(
            ValueLine("Rewards at current spend run-rate", realizedRewardsUsd, isEstimate = true),
            ValueLine("Credits at current usage rate", forecastCredits, isEstimate = true),
            ValueLine("Perks you valued", perkValue, isEstimate = true),
            ValueLine("Annual fee", -entry.annualFeeUsd.toDouble(), isEstimate = false),
            ValueLine("Authorized user fees", -auFees, isEstimate = false),
        )
        val forecastNet = forecastLines.sumOf { it.amountUsd }

        val firstYearBonus = input.bonusPoints?.takeIf { card.walletCard.bonusEarnedAt != null }?.let { it * cents / 100.0 }
        if (firstYearBonus != null) assumptions += "Welcome bonus is first-year only and excluded from the ongoing forecast."

        val warnings = mutableListOf<String>()
        if (input.otherCardsEarningSameCurrency == 0 && !entry.rewardCurrency.displayAsPercent) {
            warnings += "This is your only card earning ${entry.rewardCurrency.displayName}: closing it may forfeit the balance or remove transfer ability - move or use the points first and check the issuer's rules."
        }
        if (input.authorizedUsers > 0) warnings += "Authorized users lose their access and any shared benefits."
        if (input.upcomingTripsPaidWithCard > 0) warnings += "${input.upcomingTripsPaidWithCard} upcoming trip(s) were paid with this card - protections tied to the card may need it to stay open."
        warnings += "Product-change (downgrade) options vary by issuer and account age - confirm with the issuer before deciding."

        val scenarios = listOf(
            CardValueScenario("Keep", forecastNet, listOf("Continue earning and using credits as now."), emptyList()),
            CardValueScenario("Downgrade to a no-fee version", (forecastLines.filter { it.label.startsWith("Rewards") }.sumOf { it.amountUsd } * 0.6), listOf("Fee removed; credits and perks lost; earning typically drops (assumed 60% of current)."), warnings),
            CardValueScenario("Cancel", 0.0, listOf("No fee, no rewards, no credits or perks from this card."), warnings),
        )

        val renewal = card.walletCard.dateOpenedEpochDay?.let {
            var a = LocalDate.ofEpochDay(it).withYear(input.today.year)
            if (a.isBefore(input.today)) a = a.plusYears(1)
            a.toEpochDay()
        }
        return CardValueAnalysis(
            card = card, annualFeeUsd = entry.annualFeeUsd, authorizedUserFeesUsd = auFees, realizedLines = realizedLines, realizedNetUsd = realizedNet,
            forecastLines = forecastLines, forecastNetUsd = forecastNet, firstYearBonusUsd = firstYearBonus, assumptions = assumptions, scenarios = scenarios,
            renewalEpochDay = renewal, dataCoverage = coverage,
        )
    }

    /** Benefits (perk kinds and credit labels) present on more than one card across the household. */
    fun duplicates(cards: List<ResolvedWalletCard>): List<DuplicateBenefit> {
        val catalog = cards.filterIsInstance<ResolvedWalletCard.Catalog>()
        val byPerk = catalog.flatMap { c -> c.entry.travelPerks.map { it.kind to c } }.groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 && it.key != TravelPerk.Kind.NO_FOREIGN_FEE && it.key != TravelPerk.Kind.OTHER }
            .map { (kind, list) -> DuplicateBenefit(kind.label, list.map { it.displayName + (it.walletCard.memberName?.let { m -> " ($m)" } ?: "") }, list.sumOf { it.entry.annualFeeUsd.toDouble() }) }
        val byCredit = catalog.flatMap { c -> c.entry.credits.map { it.label to c } }.groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .map { (label, list) -> DuplicateBenefit(label, list.map { it.displayName + (it.walletCard.memberName?.let { m -> " ($m)" } ?: "") }, 0.0) }
        return byPerk + byCredit
    }
}
