package com.travelbenefits.app.ui.reconcile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.ReconciliationEngine
import com.travelbenefits.app.domain.model.ClaimDraft
import com.travelbenefits.app.domain.model.Expectation
import com.travelbenefits.app.domain.model.ExpectationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpectationRow(val expectation: Expectation, val outcome: ReconciliationEngine.Outcome, val cardName: String?, val tripTitle: String?)

data class ReconcileUiState(val rows: List<ExpectationRow> = emptyList(), val draft: ClaimDraft? = null)

@HiltViewModel
class ReconcileViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    loyaltyRepository: LoyaltyRepository,
    walletRepository: WalletRepository,
    benefitsRepository: BenefitsRepository,
    private val engine: ReconciliationEngine,
) : ViewModel() {

    private val draft = MutableStateFlow<ClaimDraft?>(null)

    val uiState: StateFlow<ReconcileUiState> = combine(
        tripRepository.observeExpectations(),
        combine(loyaltyRepository.observeSnapshots(), walletRepository.observeResolvedCards(), benefitsRepository.observeCreditStatuses()) { s, c, cr -> Triple(s, c, cr) },
        tripRepository.observeTrips(),
        draft,
    ) { expectations, (snapshots, cards, credits), trips, d ->
        val rows = expectations.map { e ->
            ExpectationRow(
                e, engine.evaluate(e, snapshots, cards, credits),
                cards.firstOrNull { it.walletCard.id == e.walletCardId }?.displayName,
                if (e.refType == "TRIP") trips.firstOrNull { it.id.toString() == e.refId }?.title else null,
            )
        }.sortedWith(compareBy({ it.expectation.status != ExpectationStatus.OPEN }, { it.expectation.dueByEpochDay }))
        ReconcileUiState(rows, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReconcileUiState())

    fun resolve(row: ExpectationRow, status: ExpectationStatus, received: Double? = null, evidence: String? = null) {
        viewModelScope.launch { tripRepository.resolveExpectation(row.expectation.id, status, received, evidence) }
    }

    fun applyOutcome(row: ExpectationRow) {
        when (val o = row.outcome) {
            is ReconciliationEngine.Outcome.Matched -> resolve(row, ExpectationStatus.MATCHED, o.received, o.evidence)
            is ReconciliationEngine.Outcome.Partial -> resolve(row, ExpectationStatus.DISCREPANCY, o.received, o.evidence)
            is ReconciliationEngine.Outcome.Discrepancy -> resolve(row, ExpectationStatus.DISCREPANCY, o.received, o.evidence)
            else -> Unit
        }
    }

    fun openDraft(row: ExpectationRow) { draft.value = engine.claimDraft(row.expectation, row.cardName, row.tripTitle) }
    fun closeDraft() { draft.value = null }
}
