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
            // Explicitly labelled: with two nested forEach loops a bare `return@forEach`
            // is ambiguous, and that ambiguity is what let a manual override fall through
            // and record a second row for the same cap.
            card.entry.categoryRates.filter { it.capUsd != null && it.capPeriod != CapPeriod.NONE }.forEach rates@{ rate ->
                val key = rate.capGroup ?: rate.category.name
                if (!seen.add(key)) return@rates
                val override = manual[key]
                if (override != null) {
                    out += CapUsage(id, key, override, "your entry")
                    return@rates
                }
                val start = periodStart(rate.capPeriod, today, card.walletCard.dateOpenedEpochDay)
                val categories = if (rate.capGroup != null) card.entry.categoryRates.filter { it.capGroup == rate.capGroup }.map { it.category }.toSet() else setOf(rate.category)
                val spent = mine.filter { it.epochDay >= start && it.category in categories }.sumOf { it.amountUsd }
                if (mine.isNotEmpty()) out += CapUsage(id, key, spent, if (rate.capPeriod == CapPeriod.STATEMENT_CYCLE) "transactions, month approximates the statement cycle" else "transactions")
            }
            val rot = card.rotating
            if (rot != null) {
                val key = "rotating:${rot.quarterKey}"
                // Written as a plain if/else on purpose. With `manual[key]?.let { ...; return@let }`
                // the label bound to the INNER let, so an override recorded its entry and then fell
                // through and recorded a second, transaction-derived one for the same key.
                val override = manual[key]
                if (override != null) {
                    out += CapUsage(id, key, override, "your entry")
                } else {
                    val start = Quarters.start(rot.quarterKey).toEpochDay()
                    val spent = mine.filter { it.epochDay >= start && it.category in rot.categories }.sumOf { it.amountUsd }
                    if (mine.isNotEmpty()) out += CapUsage(id, key, spent, "transactions")
                }
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
            val opened = dateOpenedEpochDay?.let { LocalDate.ofEpochDay(it) }
            if (opened == null) {
                LocalDate.of(today.year, 1, 1).toEpochDay()
            } else {
                var start = opened.withYear(today.year)
                if (start.isAfter(today)) start = start.minusYears(1)
                start.toEpochDay()
            }
        }
    }
}
