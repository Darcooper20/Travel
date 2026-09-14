package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.domain.model.CashVsPointsInput
import com.travelbenefits.app.domain.model.CashVsPointsResult
import com.travelbenefits.app.domain.model.EarnPlanEntry
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TransferOption
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * The "maximize points" maths: should this booking be paid with points or
 * cash, which wallet card feeds a target program fastest, and which bank
 * points can be moved into it. All valuations are the app's estimates (see
 * [com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog]) and every
 * result carries an explanation so the user can disagree with the inputs.
 */
class PointsOptimizer @Inject constructor() {

    fun cashVsPoints(input: CashVsPointsInput): CashVsPointsResult {
        val profile = LoyaltyProgramCatalog.profileFor(input.program)
        val points = input.pointsRequired.coerceAtLeast(1)
        val cashSaved = (input.cashPriceUsd - input.awardTaxesFeesUsd).coerceAtLeast(0.0)
        val cpp = cashSaved / points * 100.0

        // What a paid booking would have earned - both with the program and on the card.
        val programPointsForgone = profile.basePointsPerDollar?.let { base ->
            (input.cashPriceUsd * base * (1 + profile.eliteBonusFor(input.memberTier))).roundToLong()
        } ?: 0L
        val programForgoneUsd = programPointsForgone * profile.estValueCentsPerPoint / 100.0

        val category = if (input.program.kind == LoyaltyProgramKind.HOTEL) SpendingCategory.HOTELS else SpendingCategory.AIRFARE
        val cardForgoneUsd = when (val card = input.payWithCard) {
            is ResolvedWalletCard.Catalog -> input.cashPriceUsd * card.entry.estimatedValueCentsPerDollar(category) / 100.0
            is ResolvedWalletCard.Custom -> input.cashPriceUsd * 0.01 // unknown card: assume ~1% back
            null -> 0.0
        }

        val forgone = programForgoneUsd + cardForgoneUsd
        val pointsValueUsd = points * profile.estValueCentsPerPoint / 100.0
        // Redeem: save cashSaved, give up pointsValueUsd of points and the earning a paid stay would have brought.
        val net = cashSaved - pointsValueUsd - forgone
        val benchmark = profile.estValueCentsPerPoint
        val recommendation = when {
            net > 0.05 * input.cashPriceUsd -> CashVsPointsResult.Recommendation.USE_POINTS
            net < -0.05 * input.cashPriceUsd -> CashVsPointsResult.Recommendation.PAY_CASH
            else -> CashVsPointsResult.Recommendation.TOSS_UP
        }
        val explanation = buildString {
            append("Redeeming ${fmt(points)} points saves $${money(cashSaved)}")
            if (input.awardTaxesFeesUsd > 0) append(" after $${money(input.awardTaxesFeesUsd)} in award taxes/fees")
            append(", i.e. ${String.format("%.2f", cpp)}¢ per point versus the app's ~${benchmark}¢ estimate for ${input.program.displayName}. ")
            if (programPointsForgone > 0) {
                append("A paid booking would earn about ${fmt(programPointsForgone)} ${input.program.displayName} points")
                input.memberTier?.takeIf { profile.eliteBonusFor(it) > 0 }?.let { append(" (including your $it bonus)") }
                append(" (~$${money(programForgoneUsd)})")
                if (cardForgoneUsd > 0) append(" plus ~$${money(cardForgoneUsd)} in card rewards")
                append(". ")
            } else if (cardForgoneUsd > 0) {
                append("A paid booking would earn ~$${money(cardForgoneUsd)} in card rewards. ")
            }
            append(
                when (recommendation) {
                    CashVsPointsResult.Recommendation.USE_POINTS -> "Net, using points comes out ~$${money(net)} ahead."
                    CashVsPointsResult.Recommendation.PAY_CASH -> "Net, paying cash and keeping the points comes out ~$${money(-net)} ahead."
                    CashVsPointsResult.Recommendation.TOSS_UP -> "It's close either way - decide on cash-flow or whether you're chasing status (paid stays count, award stays often don't)."
                },
            )
        }
        return CashVsPointsResult(
            input = input,
            centsPerPoint = cpp,
            benchmarkCentsPerPoint = benchmark,
            programPointsForgone = programPointsForgone,
            cardRewardsForgoneUsd = cardForgoneUsd,
            forgoneValueUsd = forgone,
            netAdvantageOfPointsUsd = net,
            recommendation = recommendation,
            explanation = explanation,
        )
    }

    /** Ways to move bank points from the wallet into [program], best value first. */
    fun transferOptions(program: LoyaltyProgram, cards: List<ResolvedWalletCard>): List<TransferOption> {
        val profile = LoyaltyProgramCatalog.profileFor(program)
        val seen = mutableSetOf<RewardCurrency>()
        return cards.mapNotNull { card ->
            val entry = (card as? ResolvedWalletCard.Catalog)?.entry ?: return@mapNotNull null
            val currency = entry.rewardCurrency
            if (!seen.add(currency)) return@mapNotNull null
            val partner = TransferPartnerCatalog.partners.firstOrNull { it.from == currency && it.to == program } ?: return@mapNotNull null
            val valueHere = partner.ratio * profile.estValueCentsPerPoint
            TransferOption(
                walletCard = card,
                partner = partner,
                programPointsPer1000 = (1000 * partner.ratio).roundToLong().toInt(),
                valueUpliftCents = valueHere - currency.estValueCentsPerPoint,
            )
        }.sortedByDescending { it.partner.ratio * profile.estValueCentsPerPoint }
    }

    /** For each wallet card, how many [program] points a dollar in [category] yields (directly or via transfer), best first. */
    fun earnPlan(program: LoyaltyProgram, category: SpendingCategory, cards: List<ResolvedWalletCard>): List<EarnPlanEntry> =
        cards.map { card ->
            when (card) {
                is ResolvedWalletCard.Catalog -> {
                    val rotating = card.rotating?.takeIf { category in it.categories }
                    val rate = if (rotating != null && card.entry.rotatingMultiplier > card.entry.rateFor(category).multiplier) {
                        com.travelbenefits.app.domain.model.RewardRate(category, card.entry.rotatingMultiplier, "rotating category this quarter" + if (rotating.activated) "" else ", not activated")
                    } else {
                        card.entry.rateFor(category)
                    }
                    val currency = card.entry.rewardCurrency
                    val ratio = if (card.entry.isDiscontinued) null else TransferPartnerCatalog.ratio(currency, program)
                    EarnPlanEntry(
                        walletCard = card,
                        category = category,
                        cardMultiplier = rate.multiplier,
                        cardCurrency = currency,
                        programPointsPerDollar = ratio?.let { rate.multiplier * it },
                        via = when {
                            card.entry.isDiscontinued -> "Discontinued product"
                            ratio == null -> "${currency.displayName} can't be moved into ${program.displayName}"
                            TransferPartnerCatalog.nativeCurrency[program] == currency -> "Earns ${program.displayName} points directly" + (rate.note?.let { " ($it)" } ?: "")
                            else -> "Transfer ${currency.displayName} at ${TransferPartnerCatalog.partners.first { it.from == currency && it.to == program }.ratioLabel()}" + (rate.note?.let { " ($it)" } ?: "")
                        },
                        isEstimate = false,
                    )
                }
                is ResolvedWalletCard.Custom -> EarnPlanEntry(
                    walletCard = card,
                    category = category,
                    cardMultiplier = 1.0,
                    cardCurrency = RewardCurrency.GENERIC_POINTS,
                    programPointsPerDollar = null,
                    via = "Card from a live lookup - transfer partners unknown",
                    isEstimate = true,
                )
            }
        }.sortedWith(compareByDescending<EarnPlanEntry> { it.programPointsPerDollar ?: -1.0 })

    private fun fmt(n: Long): String = String.format("%,d", n)
    private fun money(d: Double): String = String.format("%,.0f", d)
}
