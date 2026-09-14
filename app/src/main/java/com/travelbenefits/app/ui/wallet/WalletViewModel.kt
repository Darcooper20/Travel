package com.travelbenefits.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.CardLookupRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.CardLookupResult
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditCardUiState(
    val isOpen: Boolean = false,
    val cardId: Long = 0,
    val displayName: String = "",
    val currencyName: String? = null,
    val nickname: String = "",
    val last4: String = "",
    val rewardsBalance: String = "",
    val notes: String = "",
)

data class AddCardUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val catalogResults: List<CardCatalogEntry> = emptyList(),
    val isLookingUp: Boolean = false,
    val lookupResult: CardLookupResult? = null,
    val lookupError: String? = null,
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val cardLookupRepository: CardLookupRepository,
) : ViewModel() {

    val resolvedCards: StateFlow<List<ResolvedWalletCard>> =
        walletRepository.observeResolvedCards()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addCardState = MutableStateFlow(AddCardUiState())
    val addCardState: StateFlow<AddCardUiState> = _addCardState.asStateFlow()

    private val _editCardState = MutableStateFlow(EditCardUiState())
    val editCardState: StateFlow<EditCardUiState> = _editCardState.asStateFlow()

    fun openEditCard(card: ResolvedWalletCard) {
        _editCardState.value = EditCardUiState(
            isOpen = true,
            cardId = card.walletCard.id,
            displayName = card.displayName,
            currencyName = card.rewardCurrency?.displayName,
            nickname = card.walletCard.nickname.orEmpty(),
            last4 = card.walletCard.last4.orEmpty(),
            rewardsBalance = card.walletCard.rewardsBalance?.toString().orEmpty(),
            notes = card.walletCard.notes.orEmpty(),
        )
    }

    fun closeEditCard() {
        _editCardState.value = EditCardUiState(isOpen = false)
    }

    fun updateEditCard(transform: (EditCardUiState) -> EditCardUiState) {
        _editCardState.value = transform(_editCardState.value)
    }

    fun saveEditCard() {
        val s = _editCardState.value
        viewModelScope.launch {
            walletRepository.updateCardDetails(
                id = s.cardId,
                nickname = s.nickname.trim().ifBlank { null },
                last4 = s.last4.filter { it.isDigit() }.takeLast(4).ifBlank { null },
                notes = s.notes.trim().ifBlank { null },
            )
            val balance = s.rewardsBalance.trim().replace(",", "").replace("$", "").toDoubleOrNull()?.toLong()
            walletRepository.updateRewardsBalance(s.cardId, balance, System.currentTimeMillis())
            closeEditCard()
        }
    }

    fun openAddCard() {
        _addCardState.value = AddCardUiState(isOpen = true, catalogResults = cardLookupRepository.searchCatalog(""))
    }

    fun closeAddCard() {
        _addCardState.value = AddCardUiState(isOpen = false)
    }

    fun onQueryChange(query: String) {
        _addCardState.value = _addCardState.value.copy(
            query = query,
            catalogResults = cardLookupRepository.searchCatalog(query),
            lookupResult = null,
            lookupError = null,
        )
    }

    fun addCatalogCard(entry: CardCatalogEntry) {
        viewModelScope.launch {
            walletRepository.addCatalogCard(entry.id, nickname = null, notes = null)
            closeAddCard()
        }
    }

    fun lookupCustomCard() {
        val name = _addCardState.value.query.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            _addCardState.value = _addCardState.value.copy(isLookingUp = true, lookupError = null)
            cardLookupRepository.lookupCustomCard(name).fold(
                onSuccess = { result ->
                    _addCardState.value = _addCardState.value.copy(isLookingUp = false, lookupResult = result)
                },
                onFailure = { error ->
                    _addCardState.value = _addCardState.value.copy(isLookingUp = false, lookupError = error.message ?: "Lookup failed.")
                },
            )
        }
    }

    fun confirmAddCustomCard() {
        val name = _addCardState.value.query.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            walletRepository.addCustomCard(name, nickname = null, notes = null)
            closeAddCard()
        }
    }

    fun removeCard(id: Long) {
        viewModelScope.launch { walletRepository.removeCard(id) }
    }
}
