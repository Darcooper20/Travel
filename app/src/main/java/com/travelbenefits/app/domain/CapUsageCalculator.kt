package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.CapPeriod
import com.travelbenefits.app.domain.model.CapUsage
import com.travelbenefits.app.domain.model.Quarters
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendTransaction
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Spend already counted against each capped rate in its current period,
 * from mapped transactions (Plaid) with user overrides taking precedence.
 * Statement-cycle caps are approximated by calendar month and labelled so.
 */
class CapUsageCalculator @Inject constructor() {

    fun compute(
        cards: List<ResolvedWalletCard>,
        transactions: List<SpendTransaction>,
        overrides: Map<Long, Map<String, Double>>,
        today: LocalDate = LocalDate.now(),
    ): List<CapUsage> {
        val out = mutableListOf<CapUsage>()
        cards.filterIsInstance<ResolvedWalletCard.Catalog>().forEach { card ->
            val id = card.walletCard.id
            val mine = transactions.filter { it.walletCardId == id && !it.pending && it.amountUsd > 0 }
            val manual = overrides[id].orEmpty()
            val seen = mutableSetOf<String>()
            card.entry.categoryRates.filter { it.capUsd != null && it.capPeriod != CapPeriod.NONE }.forEach { rate ->
                val key = rate.capGroup ?: rate.category.name
                if (!seen.add(key)) return@forEach
                manual[key]?.let { out += CapUsage(id, key, it, "your entry"); return@forEach }
                val start = periodStart(rate.capPeriod, today, card.walletCard.dateOpenedEpochDay)
                val categories = if (rate.capGroup != null) card.entry.categoryRates.filter { it.capGroup == rate.capGroup }.map { it.category }.toSet() else setOf(rate.category)
                val spent = mine.filter { it.epochDay >= start && it.category in categories }.sumOf { it.amountUsd }
                if (mine.isNotEmpty()) out += CapUsage(id, key, spent, if (rate.capPeriod == CapPeriod.STATEMENT_CYCLE) "transactions, month approximates the statement cycle" else "transactions")
            }
            card.rotating?.let { rot ->
                val key = "rotating:${rot.quarterKey}"
                manual[key]?.let { out += CapUsage(id, key, it, "your entry"); return@let }
                val start = Quarters.start(rot.quarterKey).toEpochDay()
                val spent = mine.filter { it.epochDay >= start && it.category in rot.categories }.sumOf { it.amountUsd }
                if (mine.isNotEmpty()) out += CapUsage(id, key, spent, "transactions")
            }
        }
        return out
    }

    fun periodStart(period: CapPeriod, today: LocalDate, dateOpenedEpochDay: Long?): Long = when (period) {
        CapPeriod.NONE -> Long.MIN_VALUE
        CapPeriod.MONTHLY, CapPeriod.STATEMENT_CYCLE -> YearMonth.from(today).atDay(1).toEpochDay()
        CapPeriod.QUARTERLY -> Quarters.start(Quarters.keyFor(today)).toEpochDay()
        CapPeriod.ANNUAL -> LocalDate.of(today.year, 1, 1).toEpochDay()
        CapPeriod.ACCOUNT_YEAR -> {
            val opened = dateOpenedEpochDay?.let { LocalDate.ofEpochDay(it) } ?: return LocalDate.of(today.year, 1, 1).toEpochDay()
            var start = opened.withYear(today.year)
            if (start.isAfter(today)) start = start.minusYears(1)
            start.toEpochDay()
        }
    }
}
