package com.travelbenefits.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val cardCount: Int = 0,
    val loyaltyAccountCount: Int = 0,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    walletRepository: WalletRepository,
    loyaltyRepository: LoyaltyRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        walletRepository.observeWalletCards(),
        loyaltyRepository.observeAccounts(),
    ) { cards, accounts ->
        DashboardUiState(cardCount = cards.size, loyaltyAccountCount = accounts.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
