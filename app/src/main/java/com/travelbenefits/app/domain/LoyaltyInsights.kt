package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ProgramProfile
import com.travelbenefits.app.domain.model.TierProgress
import com.travelbenefits.app.domain.model.Trip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Pure calculations over the stored loyalty state: tier progress, point
 * expiry, portfolio value and the "needs attention" alert list. Nothing here
 * touches the network or the database, so it's cheap to recompute on every
 * state change.
 */
class LoyaltyInsights @Inject constructor() {

    fun tierProgress(account: LoyaltyAccount): TierProgress {
        val profile = LoyaltyProgramCatalog.profileFor(account.program)
        val current = profile.findTier(account.tier)
        val ladder = profile.tiers
        val currentIndex = current?.let { ladder.indexOf(it) } ?: -1
        val next = ladder.getOrNull(currentIndex + 1)
        val value = account.qualifyingProgress
        val threshold = next?.threshold
        val fraction = if (value != null && threshold != null && threshold > 0) (value.toFloat() / threshold).coerceIn(0f, 1f) else null
        val remaining = if (value != null && threshold != null) (threshold - value).coerceAtLeast(0) else null
        return TierProgress(
            program = account.program,
            currentTier = current,
            nextTier = next,
            metric = profile.qualifyingMetric,
            currentValue = value,
            fraction = fraction,
            remaining = remaining,
        )
    }

    /** Epoch millis the account's points are expected to expire: an explicit date from an email, else the inactivity rule applied to the last activity we saw. */
    fun expiryEstimate(account: LoyaltyAccount): Long? {
        account.pointsExpireAt?.let { return it }
        val profile = LoyaltyProgramCatalog.profileFor(account.program)
        val months = profile.expiration.inactivityMonths ?: return null
        val anchor = account.lastActivityAt ?: return null
        return Instant.ofEpochMilli(anchor).atZone(ZoneId.systemDefault()).plusMonths(months.toLong()).toInstant().toEpochMilli()
    }

    fun estimatedValueUsd(account: LoyaltyAccount): Double? {
        val points = account.pointsNumeric ?: return null
        return points * LoyaltyProgramCatalog.profileFor(account.program).estValueCentsPerPoint / 100.0
    }

    fun portfolioValueUsd(accounts: List<LoyaltyAccount>): Double = accounts.sumOf { estimatedValueUsd(it) ?: 0.0 }

    /** Roughly how many typical award nights the balance covers, using the program's [ProgramProfile.awardBand]. */
    fun awardNightsEstimate(account: LoyaltyAccount): Int? {
        val points = account.pointsNumeric ?: return null
        val band = LoyaltyProgramCatalog.profileFor(account.program).awardBand ?: return null
        return (points / band.typicalNightPoints).toInt()
    }

    fun alerts(accounts: List<LoyaltyAccount>, trips: List<Trip>, now: Long = System.currentTimeMillis()): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val today = LocalDate.now().toEpochDay()

        for (account in accounts) {
            val profile = LoyaltyProgramCatalog.profileFor(account.program)
            val expiry = expiryEstimate(account)
            if (expiry != null && (account.pointsNumeric ?: 1L) > 0) {
                val days = TimeUnit.MILLISECONDS.toDays(expiry - now)
                when {
                    days < 0 -> alerts += Alert(
                        Alert.Severity.URGENT,
                        "${account.program.displayName} points may have expired",
                        "Estimated expiry passed ${-days} day(s) ago. Check the account - any small earn or redeem usually restarts the clock.",
                        account.program,
                    )
                    days <= EXPIRY_URGENT_DAYS -> alerts += Alert(
                        Alert.Severity.URGENT,
                        "${account.program.displayName} points expire in $days day(s)",
                        "${profile.expiration.summary} Earn or redeem a few points (a partner purchase or dining program works) to reset it.",
                        account.program,
                    )
                    days <= EXPIRY_WARN_DAYS -> alerts += Alert(
                        Alert.Severity.WARNING,
                        "${account.program.displayName} points expire in ~${days / 30} month(s)",
                        profile.expiration.summary,
                        account.program,
                    )
                }
            }

            val progress = tierProgress(account)
            val next = progress.nextTier
            val remaining = progress.remaining
            if (next != null && remaining != null && progress.fraction != null && progress.fraction >= TIER_NUDGE_FRACTION && remaining > 0) {
                alerts += Alert(
                    Alert.Severity.INFO,
                    "$remaining ${progress.metric.unit} to ${next.name} with ${account.program.displayName}",
                    next.perks?.let { "Unlocks: $it" } ?: "Close to the next tier - route the next stay here.",
                    account.program,
                )
            }
            if (account.membershipNumber.isNullOrBlank()) {
                alerts += Alert(
                    Alert.Severity.INFO,
                    "No ${account.program.displayName} member number saved",
                    "Add it so it's on hand when booking, and so the trip check can confirm bookings are earning.",
                    account.program,
                )
            }
        }

        val heldPrograms = accounts.map { it.program }.toSet()
        for (trip in trips.filter { it.isUpcoming(today) }) {
            val start = trip.startEpochDay
            val daysOut = start?.let { it - today }
            val program = trip.loyaltyProgram
            if (program != null && !trip.loyaltyNumberOnBooking) {
                val hasAccount = program in heldPrograms
                alerts += Alert(
                    if (daysOut != null && daysOut <= 7) Alert.Severity.WARNING else Alert.Severity.INFO,
                    "${trip.title}: add your ${program.displayName} number",
                    if (hasAccount) {
                        "The confirmation doesn't show a loyalty number on the booking, so it may not earn points or count toward status. Add it before check-in."
                    } else {
                        "This booking would credit to ${program.displayName}, which isn't in your wallet yet - joining is free and the stay could earn points."
                    },
                    program,
                    trip.id,
                )
            }
            if (daysOut != null && daysOut in 0..UPCOMING_TRIP_DAYS) {
                alerts += Alert(
                    Alert.Severity.INFO,
                    "${trip.title} in $daysOut day(s)",
                    listOfNotNull(trip.confirmationNumber?.let { "Confirmation $it" }, trip.destination).joinToString(" • ").ifBlank { trip.provider },
                    program,
                    trip.id,
                )
            }
        }

        return alerts.sortedBy { it.severity.ordinal }.reversed()
    }

    fun profile(program: LoyaltyProgram): ProgramProfile = LoyaltyProgramCatalog.profileFor(program)

    private companion object {
        const val EXPIRY_URGENT_DAYS = 30L
        const val EXPIRY_WARN_DAYS = 120L
        const val TIER_NUDGE_FRACTION = 0.6f
        const val UPCOMING_TRIP_DAYS = 14L
    }
}
