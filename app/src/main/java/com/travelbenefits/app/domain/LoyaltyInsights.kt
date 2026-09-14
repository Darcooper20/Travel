package com.travelbenefits.app.domain

import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.BalanceUnit
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BonusProgress
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.Quarters
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.TripKind
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
        return LoyaltyProgramCatalog.profileFor(account.program).estimatedValueUsd(points)
    }

    /** Balance for display, unit-aware ("42,500" vs "$42"), falling back to the raw text from the email. */
    fun balanceLabel(account: LoyaltyAccount): String? {
        val numeric = account.pointsNumeric ?: return account.pointsBalance
        val profile = LoyaltyProgramCatalog.profileFor(account.program)
        return if (profile.balanceUnit == BalanceUnit.DOLLARS && !account.pointsBalance.isNullOrBlank() && account.pointsBalance.contains('.')) {
            // Keep the cents from the source text ("$42.57") rather than the whole-dollar number.
            account.pointsBalance
        } else {
            profile.formatBalance(numeric)
        }
    }

    fun portfolioValueUsd(accounts: List<LoyaltyAccount>): Double = accounts.sumOf { estimatedValueUsd(it) ?: 0.0 }

    /** Estimated value of the rewards balances sitting on credit cards. */
    fun cardRewardsValueUsd(cards: List<ResolvedWalletCard>): Double = cards.sumOf { it.rewardsValueUsd ?: 0.0 }

    /** Roughly how many typical award nights the balance covers, using the program's [ProgramProfile.awardBand]. */
    fun awardNightsEstimate(account: LoyaltyAccount): Int? {
        val points = account.pointsNumeric ?: return null
        val band = LoyaltyProgramCatalog.profileFor(account.program).awardBand ?: return null
        return (points / band.typicalNightPoints).toInt()
    }

    /** "How to reset the clock" tip per program - a cheap qualifying activity. */
    fun resetTip(program: LoyaltyProgram): String = when (program.kind) {
        com.travelbenefits.app.domain.model.LoyaltyProgramKind.HOTEL -> "Any earn or redeem resets it: a co-brand card purchase, a shopping-portal order, a dining program meal, or a small points purchase/transfer."
        com.travelbenefits.app.domain.model.LoyaltyProgramKind.AIRLINE -> "Any earn resets it: a dining program meal, a shopping-portal order, a co-brand card charge, or transferring a few bank points in."
        com.travelbenefits.app.domain.model.LoyaltyProgramKind.SHOP -> "A single purchase on the account usually restarts the clock; check the program's terms."
    }

    fun bonusProgress(card: ResolvedWalletCard): BonusProgress? {
        val required = card.walletCard.bonusSpendRequiredUsd ?: return null
        return BonusProgress(
            walletCard = card,
            spendRequiredUsd = required,
            spendToDateUsd = card.walletCard.bonusSpendToDateUsd ?: 0L,
            deadlineEpochDay = card.walletCard.bonusDeadlineEpochDay,
            earnedAt = card.walletCard.bonusEarnedAt,
        )
    }

    /**
     * Everything that needs attention now. Alerts with a [Alert.notifyKey]
     * are also pushed (once per key) by the daily reminder worker; keys
     * encode the stage (90/60/30/7 days) so each stage fires exactly once.
     */
    fun alerts(
        accounts: List<LoyaltyAccount>,
        trips: List<Trip>,
        cards: List<ResolvedWalletCard> = emptyList(),
        credits: List<CreditStatus> = emptyList(),
        benefits: List<BenefitItem> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val today = LocalDate.now().toEpochDay()

        for (account in accounts) {
            val profile = LoyaltyProgramCatalog.profileFor(account.program)
            val who = account.memberName?.let { "$it's " } ?: ""
            val expiry = expiryEstimate(account)
            if (expiry != null && (account.pointsNumeric ?: 1L) > 0) {
                val days = TimeUnit.MILLISECONDS.toDays(expiry - now)
                val stage = EXPIRY_STAGES.firstOrNull { days <= it }
                val stageKey = "expiry:${account.id}:${expiryDayKey(expiry)}:${stage ?: "none"}"
                when {
                    days < 0 -> alerts += Alert(
                        Alert.Severity.URGENT,
                        "$who${account.program.displayName} points may have expired",
                        "Estimated expiry passed ${-days} day(s) ago. Check the account. " + resetTip(account.program),
                        account.program,
                        notifyKey = "expiry:${account.id}:${expiryDayKey(expiry)}:passed",
                    )
                    days <= EXPIRY_URGENT_DAYS -> alerts += Alert(
                        Alert.Severity.URGENT,
                        "$who${account.program.displayName} points expire in $days day(s)",
                        "${profile.expiration.summary} " + resetTip(account.program),
                        account.program,
                        notifyKey = stageKey,
                    )
                    days <= EXPIRY_WARN_DAYS -> alerts += Alert(
                        Alert.Severity.WARNING,
                        "$who${account.program.displayName} points expire in ~${days / 30} month(s)",
                        "${profile.expiration.summary} " + resetTip(account.program),
                        account.program,
                        notifyKey = stageKey,
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
                    "No $who${account.program.displayName} member number saved",
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
                    notifyKey = if (daysOut != null && daysOut <= 7) "tripnumber:${trip.id}" else null,
                    destination = Alert.Destination.TRIPS,
                )
            }
            if (daysOut != null && daysOut in 0..UPCOMING_TRIP_DAYS) {
                val isTomorrowOrToday = daysOut <= 1
                alerts += Alert(
                    Alert.Severity.INFO,
                    when {
                        daysOut == 0L -> "${trip.title} is today"
                        daysOut == 1L && trip.kind == TripKind.FLIGHT -> "${trip.title} tomorrow - check in now"
                        daysOut == 1L -> "${trip.title} tomorrow"
                        else -> "${trip.title} in $daysOut day(s)"
                    },
                    listOfNotNull(trip.confirmationNumber?.let { "Confirmation $it" }, trip.destination).joinToString(" • ").ifBlank { trip.provider } +
                        if (daysOut == 1L && trip.kind == TripKind.FLIGHT) " - online check-in usually opens 24 hours before departure." else "",
                    program,
                    trip.id,
                    notifyKey = if (isTomorrowOrToday) "trip:${trip.id}:${if (daysOut == 0L) "today" else "tomorrow"}" else null,
                    destination = Alert.Destination.TRIPS,
                )
            }
        }

        // Card credits: unused and the period is ending.
        for (credit in credits.filter { it.isAvailable }) {
            val daysLeft = credit.periodEndsEpochDay - today
            val stage = CREDIT_STAGES.firstOrNull { daysLeft <= it } ?: continue
            alerts += Alert(
                if (daysLeft <= 7) Alert.Severity.WARNING else Alert.Severity.INFO,
                "${credit.credit.label} on ${credit.walletCard.displayName}: $daysLeft day(s) left",
                "~$${"%,.0f".format(credit.periodValueUsd)} unused this ${credit.period.label.lowercase()} period. ${credit.credit.description}",
                notifyKey = "credit:${credit.walletCard.walletCard.id}:${credit.credit.label}:${credit.periodEndsEpochDay}:$stage",
                destination = Alert.Destination.BENEFITS,
            )
        }

        // Certificates and vouchers.
        for (item in benefits.filter { !it.isUsed }) {
            val expires = item.expiresEpochDay ?: continue
            val daysLeft = expires - today
            val stage = EXPIRY_STAGES.firstOrNull { daysLeft <= it }
            when {
                daysLeft < 0 -> alerts += Alert(Alert.Severity.WARNING, "${item.title} expired", "Expired ${-daysLeft} day(s) ago - some programs extend on request.", item.program, notifyKey = "benefit:${item.id}:passed", destination = Alert.Destination.BENEFITS)
                stage != null -> alerts += Alert(
                    if (daysLeft <= EXPIRY_URGENT_DAYS) Alert.Severity.URGENT else Alert.Severity.WARNING,
                    "${item.title} expires in $daysLeft day(s)",
                    listOfNotNull(item.program?.displayName, item.valueUsd?.let { "~$${"%,.0f".format(it)}" }, item.notes).joinToString(" • ").ifBlank { item.kind.label },
                    item.program,
                    notifyKey = "benefit:${item.id}:$expires:$stage",
                    destination = Alert.Destination.BENEFITS,
                )
            }
        }

        // Welcome bonuses and rotating categories.
        val quarter = Quarters.keyFor()
        val nextQuarter = Quarters.next(quarter)
        val daysToNextQuarter = Quarters.start(nextQuarter).toEpochDay() - today
        for (card in cards) {
            bonusProgress(card)?.takeIf { it.earnedAt == null }?.let { bonus ->
                val daysLeft = bonus.daysLeft(today)
                if (daysLeft != null && daysLeft < 0) {
                    alerts += Alert(Alert.Severity.WARNING, "${card.displayName}: bonus deadline passed", "Confirm whether the welcome bonus posted; ${"%,d".format(bonus.remainingUsd)} was still showing as unmet.", notifyKey = "bonus:${card.walletCard.id}:passed", destination = Alert.Destination.CARDS)
                } else if (daysLeft != null && daysLeft <= BONUS_WARN_DAYS && bonus.remainingUsd > 0) {
                    val stage = BONUS_STAGES.firstOrNull { daysLeft <= it } ?: BONUS_WARN_DAYS
                    alerts += Alert(
                        if (daysLeft <= 14) Alert.Severity.URGENT else Alert.Severity.WARNING,
                        "${card.displayName}: $${"%,d".format(bonus.remainingUsd)} more spend in $daysLeft day(s) for the bonus",
                        "$${"%,d".format(bonus.spendToDateUsd)} of $${"%,d".format(bonus.spendRequiredUsd)} so far. Put the next big purchases here.",
                        notifyKey = "bonus:${card.walletCard.id}:${bonus.deadlineEpochDay}:$stage",
                        destination = Alert.Destination.CARDS,
                    )
                }
            }
            val catalogCard = card as? ResolvedWalletCard.Catalog
            if (catalogCard?.entry?.rotatingKind != null) {
                val selection = catalogCard.rotating
                if (selection == null || selection.categories.isEmpty()) {
                    alerts += Alert(Alert.Severity.INFO, "${card.displayName}: set this quarter's 5% categories", "Record ${Quarters.label(quarter)} categories so the best-card picker can use them.", notifyKey = "rotate:${card.walletCard.id}:$quarter:set", destination = Alert.Destination.CARDS)
                } else if (!selection.activated) {
                    alerts += Alert(Alert.Severity.WARNING, "${card.displayName}: activate ${Quarters.label(quarter)} categories", "5% only applies after activation - it takes a minute in the issuer app.", notifyKey = "rotate:${card.walletCard.id}:$quarter:activate", destination = Alert.Destination.CARDS)
                }
                if (daysToNextQuarter in 0..QUARTER_HEADS_UP_DAYS) {
                    alerts += Alert(Alert.Severity.INFO, "${card.displayName}: ${Quarters.label(nextQuarter)} starts in $daysToNextQuarter day(s)", "New categories are usually announced and open for activation about now.", notifyKey = "rotate:${card.walletCard.id}:$nextQuarter:headsup", destination = Alert.Destination.CARDS)
                }
            }
        }

        return alerts.sortedBy { it.severity.ordinal }.reversed()
    }

    private fun expiryDayKey(epochMillis: Long): Long = epochMillis / 86_400_000L

    fun profile(program: LoyaltyProgram): ProgramProfile = LoyaltyProgramCatalog.profileFor(program)

    private companion object {
        const val EXPIRY_URGENT_DAYS = 30L
        const val EXPIRY_WARN_DAYS = 120L
        /** AwardWallet-style staged reminders: each stage notifies once. */
        val EXPIRY_STAGES = listOf(7L, 30L, 60L, 90L)
        val CREDIT_STAGES = listOf(3L, 7L, 30L)
        val BONUS_STAGES = listOf(7L, 14L, 30L, 60L)
        const val BONUS_WARN_DAYS = 60L
        const val QUARTER_HEADS_UP_DAYS = 14L
        const val TIER_NUDGE_FRACTION = 0.6f
        const val UPCOMING_TRIP_DAYS = 14L
    }
}
