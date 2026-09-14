package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.QualifyingMetric
import com.travelbenefits.app.domain.model.TierLevel
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import java.time.LocalDate
import javax.inject.Inject

/**
 * Elite-status forecast that keeps three numbers apart: posted (from the
 * program), booked (upcoming stays with the member number confirmed), and
 * hypothetical (a what-if the user types). Award stays are only counted
 * where the program's rule is verified true; unknown stays unknown.
 * Never suggests extra travel: the cost-to-tier figure is information.
 */
class StatusForecaster @Inject constructor() {

    data class Forecast(
        val program: LoyaltyProgram,
        val metric: QualifyingMetric,
        val currentTier: TierLevel?,
        val nextTier: TierLevel?,
        val posted: Int?,
        val booked: Int,
        val bookedExcludedUnknownAward: Int,
        val hypothetical: Int,
        val projected: Int?,
        val remainingAfterBooked: Int?,
        /** Rough additional cash needed to close the gap, from the user's typical night/segment cost; null when no cost given. */
        val estimatedCostToTierUsd: Double?,
        val qualificationYear: String?,
        val awardStaysRule: String,
        val notes: List<String>,
    )

    fun forecast(account: LoyaltyAccount, trips: List<Trip>, hypothetical: Int = 0, typicalUnitCostUsd: Double? = null, today: LocalDate = LocalDate.now()): Forecast {
        val profile = LoyaltyProgramCatalog.profileFor(account.program)
        val current = profile.findTier(account.tier)
        val idx = current?.let { profile.tiers.indexOf(it) } ?: -1
        val next = profile.tiers.getOrNull(idx + 1)
        val notes = mutableListOf<String>()

        val upcoming = trips.filter { it.loyaltyProgram == account.program && it.isUpcoming(today.toEpochDay()) && it.loyaltyNumberState == LoyaltyNumberState.CONFIRMED }
        var booked = 0
        var excluded = 0
        if (profile.qualifyingMetric == QualifyingMetric.NIGHTS) {
            upcoming.filter { it.kind == TripKind.HOTEL }.forEach { t ->
                val nights = t.nights ?: return@forEach
                val isAward = (t.pointsUsed ?: 0L) > 0 || t.certificateId != null
                when {
                    !isAward -> booked += nights
                    profile.awardStaysCountForStatus == true -> booked += nights
                    else -> excluded += nights
                }
            }
            if (excluded > 0) notes += "$excluded award night(s) excluded: whether award stays count for ${account.program.displayName} status is ${if (profile.awardStaysCountForStatus == null) "not verified" else "false"}."
            val unconfirmed = trips.count { it.loyaltyProgram == account.program && it.isUpcoming(today.toEpochDay()) && it.loyaltyNumberState != LoyaltyNumberState.CONFIRMED && it.kind == TripKind.HOTEL }
            if (unconfirmed > 0) notes += "$unconfirmed upcoming stay(s) not counted: member number not confirmed on the reservation."
        } else {
            notes += "Booked flights aren't converted to ${profile.qualifyingMetric.label.lowercase()}: it depends on fare class and spend, which emails rarely state."
        }
        val posted = account.qualifyingProgress
        val projected = posted?.let { it + booked + hypothetical }
        val threshold = next?.threshold
        val remaining = if (projected != null && threshold != null) (threshold - projected).coerceAtLeast(0) else null
        val cost = if (remaining != null && typicalUnitCostUsd != null && remaining > 0) remaining * typicalUnitCostUsd else null
        if (posted == null) notes += "Posted progress unknown - enter this year's ${profile.qualifyingMetric.label.lowercase()} on the account."
        return Forecast(
            program = account.program, metric = profile.qualifyingMetric, currentTier = current, nextTier = next, posted = posted, booked = booked,
            bookedExcludedUnknownAward = excluded, hypothetical = hypothetical, projected = projected, remainingAfterBooked = remaining,
            estimatedCostToTierUsd = cost, qualificationYear = profile.qualificationYear,
            awardStaysRule = when (profile.awardStaysCountForStatus) { true -> "Award stays count toward status (verified)."; false -> "Award stays do not count toward status (verified)."; null -> "Whether award stays count toward status is not verified for this program." },
            notes = notes,
        )
    }
}
