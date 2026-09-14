package com.travelbenefits.app.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** A linked Plaid item (bank login) for the UI. */
data class PlaidItem(
    val itemId: String,
    val institutionName: String?,
    val createdAt: Long,
    val lastSyncAt: Long?,
    val lastError: String?,
    val accounts: List<PlaidAccount>,
)

data class PlaidAccount(
    val accountId: String,
    val itemId: String,
    val name: String,
    val officialName: String?,
    val mask: String?,
    val type: String?,
    val subtype: String?,
    val walletCardId: Long?,
) {
    val label: String get() = (officialName ?: name) + (mask?.let { " ••••$it" } ?: "")
    val isCard: Boolean get() = type == "credit" || subtype == "credit card"
}

data class SpendTransaction(
    val transactionId: String,
    val accountId: String,
    val walletCardId: Long?,
    val amountUsd: Double,
    val epochDay: Long,
    val name: String,
    val merchantName: String?,
    val category: SpendingCategory?,
    val pending: Boolean,
)

/** Reporting window for the spend analysis. */
enum class SpendPeriod(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_90_DAYS("Last 90 days"),
    ;

    fun range(today: LocalDate = LocalDate.now()): LongRange = when (this) {
        THIS_MONTH -> YearMonth.from(today).atDay(1).toEpochDay()..today.toEpochDay()
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1).toEpochDay()..it.atEndOfMonth().toEpochDay() }
        LAST_90_DAYS -> today.minusDays(90).toEpochDay()..today.toEpochDay()
    }
}

/** Spend on one card within one category, with what it earned. */
data class CardSpend(
    val card: ResolvedWalletCard?,
    val accountLabel: String,
    val spendUsd: Double,
    val valueEarnedUsd: Double,
    val multiplierLabel: String,
)

data class CategorySpend(
    val category: SpendingCategory,
    val spendUsd: Double,
    val byCard: List<CardSpend>,
    val bestCard: ResolvedWalletCard?,
    val bestMultiplierLabel: String,
    val valueEarnedUsd: Double,
    val valueIfBestUsd: Double,
) {
    val missedUsd: Double get() = (valueIfBestUsd - valueEarnedUsd).coerceAtLeast(0.0)
    /** Spend that went on a card other than the best one. */
    val misroutedUsd: Double get() = byCard.filter { bestCard != null && it.card?.walletCard?.id != bestCard.walletCard.id }.sumOf { it.spendUsd }
}

data class SpendReport(
    val period: SpendPeriod,
    val totalSpendUsd: Double,
    val mappedSpendUsd: Double,
    val unmappedSpendUsd: Double,
    val categories: List<CategorySpend>,
    val transactionCount: Int,
) {
    val missedTotalUsd: Double get() = categories.sumOf { it.missedUsd }
    val earnedTotalUsd: Double get() = categories.sumOf { it.valueEarnedUsd }
    /** Top line: "You put $1,200 of dining on the wrong card - ~$36 left on the table." */
    val headline: CategorySpend? get() = categories.filter { it.missedUsd > 0 }.maxByOrNull { it.missedUsd }
}
