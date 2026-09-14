package com.travelbenefits.app.ui.cardvalue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.data.repository.PlaidRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.CardValueAnalyzer
import com.travelbenefits.app.domain.model.CardValueAnalysis
import com.travelbenefits.app.domain.model.DuplicateBenefit
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.TravelPerk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CardValueUiState(
    val analyses: List<CardValueAnalysis> = emptyList(),
    val duplicates: List<DuplicateBenefit> = emptyList(),
    val overrides: Map<Pair<String, String>, String> = emptyMap(),
)

@HiltViewModel
class CardValueViewModel @Inject constructor(
    walletRepository: WalletRepository,
    plaidRepository: PlaidRepository,
    benefitsRepository: BenefitsRepository,
    tripRepository: TripRepository,
    private val overrideRepository: OverrideRepository,
    private val analyzer: CardValueAnalyzer,
) : ViewModel() {

    val uiState: StateFlow<CardValueUiState> = combine(
        walletRepository.observeResolvedCards(),
        plaidRepository.observeTransactions(LocalDate.now().minusDays(400).toEpochDay()),
        combine(benefitsRepository.observeLedger(), benefitsRepository.observeCreditStatuses(), benefitsRepository.observeItems()) { l, c, i -> Triple(l, c, i) },
        overrideRepository.observeOverrides(),
        tripRepository.observeTrips(),
    ) { cards, txns, (ledger, credits, items), overrides, trips ->
        val today = LocalDate.now()
        val valuations = overrideRepository.valuations(overrides)
        val analyses = cards.mapNotNull { card ->
            val id = card.walletCard.id
            val scope = "card:$id"
            val currency = card.rewardCurrency
            analyzer.analyze(
                CardValueAnalyzer.Inputs(
                    card = card, transactions = txns, ledger = ledger, credits = credits, certificates = items,
                    perkValuesUsd = overrideRepository.perkValues(overrides, id),
                    authorizedUsers = overrides[scope to OverrideRepository.KEY_AU_COUNT]?.toIntOrNull() ?: 0,
                    valuations = valuations,
                    bonusPoints = overrides[scope to OverrideRepository.KEY_BONUS_POINTS]?.toLongOrNull(),
                    today = today,
                    otherCardsEarningSameCurrency = cards.count { it.walletCard.id != id && it.rewardCurrency == currency },
                    upcomingTripsPaidWithCard = trips.count { it.isUpcoming(today.toEpochDay()) && it.paymentCardId == id },
                ),
            )
        }.sortedBy { it.renewalEpochDay ?: Long.MAX_VALUE }
        CardValueUiState(analyses, analyzer.duplicates(cards), overrides)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardValueUiState())

    fun setOverride(card: ResolvedWalletCard, key: String, value: String) {
        viewModelScope.launch { overrideRepository.set("card:${card.walletCard.id}", key, value.trim().ifBlank { null }) }
    }

    fun setPerkValue(card: ResolvedWalletCard, kind: TravelPerk.Kind, value: String) = setOverride(card, "perk:${kind.name}", value)
}
