package com.travelbenefits.app.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** How a card credit resets - parsed from the free-text frequency in the catalog. */
enum class CreditPeriod(val label: String) {
    MONTHLY("Monthly"),
    ANNUAL("Annual"),
    EVERY_4_YEARS("Every 4 years"),
    ;

    companion object {
        fun fromFrequency(frequency: String): CreditPeriod = when {
            frequency.startsWith("monthly", ignoreCase = true) -> MONTHLY
            frequency.contains("4", ignoreCase = true) && frequency.contains("year", ignoreCase = true) -> EVERY_4_YEARS
            else -> ANNUAL
        }
    }
}

/**
 * A catalog card credit instantiated for one wallet card, with the user's
 * "I used it" state for the current period. Derived, not stored: only the
 * last-used date lives in the database.
 */
data class CreditStatus(
    val walletCard: ResolvedWalletCard,
    val credit: CardCredit,
    val period: CreditPeriod,
    val lastUsedAt: Long?,
    /** True when the credit hasn't been used within the current period. */
    val isAvailable: Boolean,
    /** Last day of the current period, when the unused value is lost. */
    val periodEndsEpochDay: Long,
    /** Value at stake this period (monthly credits are the annual figure / 12). */
    val periodValueUsd: Double,
) {
    val key: String get() = "credit:${walletCard.walletCard.id}:${credit.label}"
}

enum class BenefitKind(val label: String) {
    FREE_NIGHT("Free night certificate"),
    COMPANION("Companion pass / certificate"),
    UPGRADE("Upgrade award"),
    LOUNGE_PASS("Lounge pass"),
    VOUCHER("Voucher / travel credit"),
    OTHER("Other"),
}

/** A certificate, voucher or pass the user holds - free nights, companion fares, upgrade awards. */
data class BenefitItem(
    val id: Long,
    val kind: BenefitKind,
    val title: String,
    val program: LoyaltyProgram?,
    val walletCardId: Long?,
    val valueUsd: Double?,
    val expiresEpochDay: Long?,
    val usedAt: Long?,
    val notes: String?,
    val source: LoyaltyAccountSource,
    val sourceEmailSubject: String?,
    val createdAt: Long,
) {
    val isUsed: Boolean get() = usedAt != null
}

/** Rotating 5% categories the user has set for one card and quarter. */
data class RotatingSelection(
    val walletCardId: Long,
    val quarterKey: String,
    val categories: List<SpendingCategory>,
    val activated: Boolean,
)

enum class RotatingKind(val label: String) {
    /** Fixed categories set by the issuer each quarter; must be activated (Chase Freedom Flex, Discover it). */
    QUARTERLY_ACTIVATION("Activate each quarter"),
    /** Cardholder picks categories each quarter (U.S. Bank Cash+). */
    QUARTERLY_CHOICE("Choose each quarter"),
}

/** Welcome-bonus progress for a card. */
data class BonusProgress(
    val walletCard: ResolvedWalletCard,
    val spendRequiredUsd: Long,
    val spendToDateUsd: Long,
    val deadlineEpochDay: Long?,
    val earnedAt: Long?,
) {
    val remainingUsd: Long get() = (spendRequiredUsd - spendToDateUsd).coerceAtLeast(0)
    val fraction: Float get() = if (spendRequiredUsd <= 0) 1f else (spendToDateUsd.toFloat() / spendRequiredUsd).coerceIn(0f, 1f)
    fun daysLeft(today: Long = LocalDate.now().toEpochDay()): Long? = deadlineEpochDay?.let { it - today }
}

/** A saved "tell me when this award opens up" request, checked by the research worker. */
data class AwardWatch(
    val id: Long,
    val title: String,
    val program: LoyaltyProgram?,
    val origin: String?,
    val destination: String?,
    val dateFrom: String?,
    val dateTo: String?,
    val notes: String?,
    val active: Boolean,
    val createdAt: Long,
    val lastCheckedAt: Long?,
    val lastResult: String?,
    val lastFound: Boolean,
)

/** A bank transfer bonus currently running, as found by the research worker (web search) - never assumed, always dated. */
data class TransferBonus(
    val id: Long,
    val from: RewardCurrency,
    val to: LoyaltyProgram,
    val bonusPercent: Int,
    val endsEpochDay: Long?,
    val note: String?,
    val checkedAt: Long,
) {
    /** Partner ratio times the bonus, e.g. 1:1 with 30% -> 1.3. */
    fun effectiveRatio(baseRatio: Double): Double = baseRatio * (1 + bonusPercent / 100.0)
}

object Quarters {
    fun keyFor(date: LocalDate = LocalDate.now()): String = "${date.year}-Q${(date.monthValue - 1) / 3 + 1}"

    fun label(key: String): String = key.replace("-", " ")

    fun start(key: String): LocalDate {
        val (year, q) = key.split("-Q")
        return LocalDate.of(year.toInt(), (q.toInt() - 1) * 3 + 1, 1)
    }

    fun end(key: String): LocalDate = start(key).plusMonths(3).minusDays(1)

    fun next(key: String): String = keyFor(start(key).plusMonths(3))

    fun endOfMonth(date: LocalDate = LocalDate.now()): LocalDate = YearMonth.from(date).atEndOfMonth()
}
