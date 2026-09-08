package com.travelbenefits.app.ui.hotels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.repository.GmailScanRepository
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanState {
    data object Idle : ScanState()
    data class InProgress(val message: String) : ScanState()
    data class Done(val count: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

data class EditAccountState(
    val isOpen: Boolean = false,
    val program: LoyaltyProgram = LoyaltyProgram.MARRIOTT_BONVOY,
    val membershipNumber: String = "",
    val tier: String = "",
    val pointsBalance: String = "",
)

@HiltViewModel
class HotelsViewModel @Inject constructor(
    private val loyaltyRepository: LoyaltyRepository,
    private val gmailScanRepository: GmailScanRepository,
    private val gmailAuthManager: GmailAuthManager,
) : ViewModel() {

    val accounts: StateFlow<List<LoyaltyAccount>> =
        loyaltyRepository.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isGmailConnected: StateFlow<Boolean> = gmailAuthManager.isSignedIn

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _editState = MutableStateFlow(EditAccountState())
    val editState: StateFlow<EditAccountState> = _editState.asStateFlow()

    fun startScan() {
        if (_scanState.value is ScanState.InProgress) return
        viewModelScope.launch {
            _scanState.value = ScanState.InProgress("Starting…")
            gmailScanRepository.scanForLoyaltyAccounts { message ->
                _scanState.value = ScanState.InProgress(message)
            }.fold(
                onSuccess = { found -> _scanState.value = ScanState.Done(found.size) },
                onFailure = { error -> _scanState.value = ScanState.Error(error.message ?: "Scan failed.") },
            )
        }
    }

    fun dismissScanState() {
        _scanState.value = ScanState.Idle
    }

    fun openAdd(existing: LoyaltyAccount? = null) {
        _editState.value = if (existing != null) {
            EditAccountState(
                isOpen = true,
                program = existing.program,
                membershipNumber = existing.membershipNumber.orEmpty(),
                tier = existing.tier.orEmpty(),
                pointsBalance = existing.pointsBalance.orEmpty(),
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
            )
            closeEdit()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { loyaltyRepository.delete(id) }
    }
}
