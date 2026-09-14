package com.travelbenefits.app.ui.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.domain.LoyaltyInsights
import com.travelbenefits.app.domain.StatusForecaster
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.PointsSnapshot
import com.travelbenefits.app.domain.model.ProgramProfile
import com.travelbenefits.app.domain.model.TierProgress
import com.travelbenefits.app.ui.common.parseUserDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/** One loyalty account with everything the screen derives from it. */
data class AccountCardState(
    val account: LoyaltyAccount,
    val profile: ProgramProfile,
    val balanceLabel: String?,
    val progress: TierProgress,
    val expiryEstimateAt: Long?,
    val estimatedValueUsd: Double?,
    val awardNightsEstimate: Int?,
    val history: List<PointsSnapshot>,
    val forecast: StatusForecaster.Forecast? = null,
)

data class EditAccountState(
    val isOpen: Boolean = false,
    val program: LoyaltyProgram = LoyaltyProgram.MARRIOTT_BONVOY,
    val membershipNumber: String = "",
    val tier: String = "",
    val pointsBalance: String = "",
    val qualifyingProgress: String = "",
    val pointsExpireOn: String = "",
    val memberName: String = "",
    val editingId: Long? = null,
)

@HiltViewModel
class LoyaltyViewModel @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
    private val insights: LoyaltyInsights,
    private val forecaster: StatusForecaster,
    tripRepository: TripRepository,
    private val overrideRepository: OverrideRepository,
    gmailAuthManager: GmailAuthManager,
) : ViewModel() {

    private val hypothetical = MutableStateFlow<Map<Long, Int>>(emptyMap())

    val accounts: StateFlow<List<AccountCardState>> = combine(
        loyaltyRepository.observeAccounts(),
        loyaltyRepository.observeSnapshots(),
        tripRepository.observeTrips(),
        hypothetical,
        overrideRepository.observeOverrides(),
    ) { accounts, snapshots, trips, hypo, overrides ->
        val unitCost = overrides[OverrideRepository.SCOPE_GLOBAL to "typicalNightCostUsd"]?.toDoubleOrNull()
        accounts.map { account ->
            AccountCardState(
                account = account,
                profile = insights.profile(account.program),
                balanceLabel = insights.balanceLabel(account),
                progress = insights.tierProgress(account),
                expiryEstimateAt = insights.expiryEstimate(account),
                estimatedValueUsd = insights.estimatedValueUsd(account),
                awardNightsEstimate = insights.awardNightsEstimate(account),
                history = snapshots.filter { it.program == account.program }.sortedByDescending { it.recordedAt }.take(8),
                forecast = forecaster.forecast(account, trips, hypo[account.id] ?: 0, unitCost),
            )
        }.sortedWith(compareBy({ it.account.memberName ?: "" }, { it.account.program.kind }, { -(it.estimatedValueUsd ?: -1.0) }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isGmailConnected: StateFlow<Boolean> = gmailAuthManager.isSignedIn

    private val _editState = MutableStateFlow(EditAccountState())
    val editState: StateFlow<EditAccountState> = _editState.asStateFlow()

    fun openAdd(existing: LoyaltyAccount? = null) {
        _editState.value = if (existing != null) {
            EditAccountState(
                isOpen = true,
                program = existing.program,
                membershipNumber = existing.membershipNumber.orEmpty(),
                tier = existing.tier.orEmpty(),
                pointsBalance = existing.pointsNumeric?.toString() ?: existing.pointsBalance.orEmpty(),
                qualifyingProgress = existing.qualifyingProgress?.toString().orEmpty(),
                pointsExpireOn = existing.pointsExpireAt?.let { LocalDate.ofEpochDay(it / 86_400_000L).toString() }.orEmpty(),
                memberName = existing.memberName.orEmpty(),
                editingId = existing.id,
            )
        } else {
            EditAccountState(isOpen = true)
        }
    }

    fun closeEdit() {
        _editState.value = EditAccountState(isOpen = false)
    }

    fun updateEdit(transform: (EditAccountState) -> EditAccountState) {
        _editState.value = transform(_editState.value)
    }

    fun saveEdit() {
        val state = _editState.value
        viewModelScope.launch {
            loyaltyRepository.upsertManual(
                program = state.program,
                membershipNumber = state.membershipNumber.ifBlank { null },
                tier = state.tier.ifBlank { null },
                pointsBalance = state.pointsBalance.ifBlank { null },
                qualifyingProgress = state.qualifyingProgress.trim().toIntOrNull(),
                pointsExpireAt = parseUserDate(state.pointsExpireOn)?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
                memberName = state.memberName,
                editingId = state.editingId,
            )
            closeEdit()
        }
    }

    fun setHypothetical(accountId: Long, value: Int) { hypothetical.value = hypothetical.value + (accountId to value) }

    fun setTypicalNightCost(value: String) {
        viewModelScope.launch { overrideRepository.set(OverrideRepository.SCOPE_GLOBAL, "typicalNightCostUsd", value.trim().toDoubleOrNull()?.toString()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { loyaltyRepository.delete(id) }
    }
}
