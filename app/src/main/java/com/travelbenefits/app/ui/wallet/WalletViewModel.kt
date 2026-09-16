package com.travelbenefits.app.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.CardLookupRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.model.CardCatalogEntry
import com.travelbenefits.app.domain.model.CardLookupResult
import com.travelbenefits.app.domain.model.Quarters
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.data.repository.OfferRepository
import com.travelbenefits.app.domain.model.MerchantOffer
import com.travelbenefits.app.ui.common.parseUserDate
import java.time.LocalDate
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
    val memberName: String = "",
    val dateOpened: String = "",
    val bonusSpendRequired: String = "",
    val bonusDeadline: String = "",
    val bonusSpendToDate: String = "",
    val bonusEarned: Boolean = false,
    val isAuthorizedUser: Boolean = false,
)

data class OfferFormState(
    val isOpen: Boolean = false,
    val walletCardId: Long? = null,
    val merchant: String = "",
    val description: String = "",
    val value: String = "",
    val percent: String = "",
    val minSpend: String = "",
    val expires: String = "",
    val enrolled: Boolean = true,
    val kind: MerchantOffer.Kind = MerchantOffer.Kind.CARD_OFFER,
)

data class RotatingEditState(
    val isOpen: Boolean = false,
    val cardId: Long = 0,
    val displayName: String = "",
    val quarterKey: String = "",
    val categories: Set<SpendingCategory> = emptySet(),
    val activated: Boolean = false,
    val isLookingUp: Boolean = false,
    val lookupNote: String? = null,
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
    private val offerRepository: OfferRepository,
) : ViewModel() {

    val offers: StateFlow<List<MerchantOffer>> = offerRepository.observeOffers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _offerForm = MutableStateFlow(OfferFormState())
    val offerForm: StateFlow<OfferFormState> = _offerForm.asStateFlow()

    fun openOffer(cardId: Long?) { _offerForm.value = OfferFormState(isOpen = true, walletCardId = cardId) }
    fun closeOffer() { _offerForm.value = OfferFormState(isOpen = false) }
    fun updateOffer(transform: (OfferFormState) -> OfferFormState) { _offerForm.value = transform(_offerForm.value) }

    fun saveOffer() {
        val f = _offerForm.value
        if (f.merchant.isBlank()) return
        viewModelScope.launch {
            offerRepository.addOffer(
                f.walletCardId, f.merchant, f.description.ifBlank { f.merchant }, f.value.replace("$", "").toDoubleOrNull(), f.percent.replace("%", "").toDoubleOrNull(),
                f.minSpend.replace("$", "").toDoubleOrNull(), parseUserDate(f.expires), f.enrolled, f.kind, null,
            )
            closeOffer()
        }
    }

    fun setOfferEnrolled(id: Long, enrolled: Boolean) { viewModelScope.launch { offerRepository.setEnrolled(id, enrolled) } }
    fun deleteOffer(id: Long) { viewModelScope.launch { offerRepository.deleteOffer(id) } }

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
            memberName = card.walletCard.memberName.orEmpty(),
            dateOpened = card.walletCard.dateOpenedEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
            bonusSpendRequired = card.walletCard.bonusSpendRequiredUsd?.toString().orEmpty(),
            bonusDeadline = card.walletCard.bonusDeadlineEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
            bonusSpendToDate = card.walletCard.bonusSpendToDateUsd?.toString().orEmpty(),
            bonusEarned = card.walletCard.bonusEarnedAt != null,
            isAuthorizedUser = card.walletCard.isAuthorizedUser == true,
        )
    }

    private val _rotatingState = MutableStateFlow(RotatingEditState())
    val rotatingState: StateFlow<RotatingEditState> = _rotatingState.asStateFlow()

    fun openRotating(card: ResolvedWalletCard.Catalog) {
        val quarter = Quarters.keyFor()
        _rotatingState.value = RotatingEditState(
            isOpen = true,
            cardId = card.walletCard.id,
            displayName = card.displayName,
            quarterKey = quarter,
            categories = card.rotating?.categories?.toSet().orEmpty(),
            activated = card.rotating?.activated ?: false,
        )
    }

    fun closeRotating() {
        _rotatingState.value = RotatingEditState(isOpen = false)
    }

    fun toggleRotatingCategory(category: SpendingCategory) {
        val s = _rotatingState.value
        _rotatingState.value = s.copy(categories = if (category in s.categories) s.categories - category else s.categories + category)
    }

    fun setRotatingActivated(activated: Boolean) {
        _rotatingState.value = _rotatingState.value.copy(activated = activated)
    }

    fun saveRotating() {
        val s = _rotatingState.value
        viewModelScope.launch {
            walletRepository.setRotatingSelection(s.cardId, s.quarterKey, s.categories.toList(), s.activated)
            closeRotating()
        }
    }

    /** Asks Claude (web search) what this quarter's categories are for the card and pre-selects the matches. */
    fun lookupRotatingCategories() {
        val s = _rotatingState.value
        if (s.isLookingUp) return
        viewModelScope.launch {
            _rotatingState.value = s.copy(isLookingUp = true, lookupNote = null)
            cardLookupRepository.lookupRotatingCategories(s.displayName, s.quarterKey).fold(
                onSuccess = { result ->
                    _rotatingState.value = _rotatingState.value.copy(
                        isLookingUp = false,
                        categories = if (result.categories.isNotEmpty()) result.categories.toSet() else _rotatingState.value.categories,
                        lookupNote = result.note,
                    )
                },
                onFailure = { e -> _rotatingState.value = _rotatingState.value.copy(isLookingUp = false, lookupNote = e.message ?: "Lookup failed.") },
            )
        }
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
                memberName = s.memberName.trim().ifBlank { null },
                dateOpenedEpochDay = parseUserDate(s.dateOpened),
                bonusSpendRequiredUsd = s.bonusSpendRequired.replace(",", "").replace("$", "").trim().toDoubleOrNull()?.toLong(),
                bonusDeadlineEpochDay = parseUserDate(s.bonusDeadline),
                bonusSpendToDateUsd = s.bonusSpendToDate.replace(",", "").replace("$", "").trim().toDoubleOrNull()?.toLong(),
                bonusEarned = s.bonusEarned,
                isAuthorizedUser = s.isAuthorizedUser,
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

    /** Accepts a card the email monitor added, optionally pinning the product the user picked. */
    fun confirmCard(id: Long, catalogId: String? = null) {
        viewModelScope.launch { walletRepository.confirmCard(id, catalogId) }
    }
}
