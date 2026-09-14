package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.CashVsPointsInput
import com.travelbenefits.app.domain.model.CashVsPointsResult
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.PurchaseOption
import com.travelbenefits.app.domain.model.PurchaseQuery
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TravelPerk
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import javax.inject.Inject

/**
 * "Best way to pay for this trip": cash on the best card (with the card's
 * trip perks), points (cash-vs-points including forgone earnings), and a
 * certificate when one fits. Uses the user's entered cash price only -
 * never an inflated alternative.
 */
class TripPaymentComparator @Inject constructor(
    private val purchaseRecommender: PurchaseRecommender,
    private val pointsOptimizer: PointsOptimizer,
) {
    data class CertificateOption(val item: BenefitItem, val fits: Boolean, val note: String)

    data class Comparison(
        val cashOptions: List<PurchaseOption>,
        val pointsResult: CashVsPointsResult?,
        val certificates: List<CertificateOption>,
        val perksForBestCard: List<TravelPerk>,
        val statusNote: String?,
        val caveats: List<String>,
    )

    fun compare(
        trip: Trip,
        cashPriceUsd: Double?,
        pointsRequired: Long?,
        awardFeesUsd: Double,
        cards: List<ResolvedWalletCard>,
        accounts: List<LoyaltyAccount>,
        certificates: List<BenefitItem>,
        valuations: Map<RewardCurrency, Double>,
        channel: BookingChannel,
    ): Comparison {
        val caveats = mutableListOf<String>()
        val category = when (trip.kind) { TripKind.HOTEL -> SpendingCategory.HOTELS; TripKind.FLIGHT -> SpendingCategory.AIRFARE; else -> SpendingCategory.TRAVEL_GENERAL }
        val cashOptions = if (cashPriceUsd != null && cashPriceUsd > 0) {
            purchaseRecommender.recommend(PurchaseQuery(trip.provider, category, cashPriceUsd, channel = channel, categorySource = "trip type"), cards, valuations = valuations).options
        } else {
            caveats += "Enter the cash price to compare cards."
            emptyList()
        }
        val program = trip.loyaltyProgram
        val pointsResult = if (program != null && cashPriceUsd != null && pointsRequired != null && pointsRequired > 0) {
            val best = cashOptions.firstOrNull()?.card
            pointsOptimizer.cashVsPoints(CashVsPointsInput(program, cashPriceUsd, pointsRequired, awardFeesUsd, best, accounts.firstOrNull { it.program == program }?.tier))
        } else null
        val certs = certificates.filter { !it.isUsed && (it.kind == BenefitKind.FREE_NIGHT && trip.kind == TripKind.HOTEL || it.kind == BenefitKind.COMPANION && trip.kind == TripKind.FLIGHT || it.kind == BenefitKind.VOUCHER) }
            .map { c ->
                val sameProgram = c.program == null || c.program == program
                val expiresBefore = c.expiresEpochDay != null && trip.startEpochDay != null && c.expiresEpochDay < trip.startEpochDay
                val note = when {
                    !sameProgram -> "Different program (${c.program?.displayName})."
                    expiresBefore -> "Expires before the stay - most programs require the stay, not just the booking, before expiry; verify."
                    c.valueUsd != null && cashPriceUsd != null && cashPriceUsd > c.valueUsd * 1.5 -> "Stay costs more than the certificate's typical value; check the point/category cap."
                    else -> "Fits by program and dates; check category/point caps and top-up rules."
                }
                CertificateOption(c, sameProgram && !expiresBefore, note)
            }
        val bestCard = cashOptions.firstOrNull()?.card as? ResolvedWalletCard.Catalog
        val perks = bestCard?.entry?.travelPerks.orEmpty()
        val statusNote = program?.let { p ->
            val profile = LoyaltyProgramCatalog.profileFor(p)
            when (profile.awardStaysCountForStatus) {
                true -> "Award stays count toward ${p.displayName} status (verified) - paying with points doesn't cost elite progress."
                false -> "Award stays do not count toward ${p.displayName} status (verified) - paying with points forgoes elite progress."
                null -> "Whether award stays count toward ${p.displayName} status is not verified - check the program's terms before deciding."
            }
        }
        if (perks.isNotEmpty()) caveats += "Card protections apply only under the conditions shown (usually the fare charged to that card) - confirm in the benefits guide."
        return Comparison(cashOptions, pointsResult, certs, perks, statusNote, caveats)
    }
}
