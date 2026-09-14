package com.travelbenefits.app.domain.model

/** One line of a card's annual value account. */
data class ValueLine(val label: String, val amountUsd: Double, val isEstimate: Boolean, val note: String? = null)

data class CardValueScenario(
    val name: String,
    val netUsd: Double,
    val effects: List<String>,
    val warnings: List<String>,
)

/** Realized (last 12 months, from records) versus forecast (next 12 months, from run-rates), always separated. */
data class CardValueAnalysis(
    val card: ResolvedWalletCard,
    val annualFeeUsd: Int,
    val authorizedUserFeesUsd: Double,
    val realizedLines: List<ValueLine>,
    val realizedNetUsd: Double,
    val forecastLines: List<ValueLine>,
    val forecastNetUsd: Double,
    /** First-year welcome bonus value, reported separately and never in the ongoing forecast. */
    val firstYearBonusUsd: Double?,
    val assumptions: List<String>,
    val scenarios: List<CardValueScenario>,
    val renewalEpochDay: Long?,
    val dataCoverage: String,
)

/** Same benefit type held on more than one card in the household. */
data class DuplicateBenefit(val label: String, val cards: List<String>, val annualCostUsd: Double)
