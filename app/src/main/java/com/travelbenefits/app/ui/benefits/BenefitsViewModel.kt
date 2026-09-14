package com.travelbenefits.app.ui.benefits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.CreditStatus
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ResolvedWalletCard
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
import javax.inject.Inject

data class BenefitsUiState(
    val credits: List<CreditStatus> = emptyList(),
    val items: List<BenefitItem> = emptyList(),
    val cards: List<ResolvedWalletCard> = emptyList(),
    val unusedCreditValueUsd: Double = 0.0,
    val expiringSoonCount: Int = 0,
)

data class EditBenefitState(
    val isOpen: Boolean = false,
    val id: Long = 0,
    val kind: BenefitKind = BenefitKind.FREE_NIGHT,
    val title: String = "",
    val program: LoyaltyProgram? = null,
    val walletCardId: Long? = null,
    val valueUsd: String = "",
    val expiresOn: String = "",
    val notes: String = "",
)

@HiltViewModel
class BenefitsViewModel @Inject constructor(
    private val benefitsRepository: BenefitsRepository,
    walletRepository: WalletRepository,
) : ViewModel() {

    val uiState: StateFlow<BenefitsUiState> = combine(
        benefitsRepository.observeCreditStatuses(),
        benefitsRepository.observeItems(),
        walletRepository.observeResolvedCards(),
    ) { credits, items, cards ->
        val today = LocalDate.now().toEpochDay()
        BenefitsUiState(
            credits = credits.sortedWith(compareBy({ !it.isAvailable }, { it.periodEndsEpochDay })),
            items = items,
            cards = cards,
            unusedCreditValueUsd = credits.filter { it.isAvailable }.sumOf { it.periodValueUsd },
            expiringSoonCount = credits.count { it.isAvailable && it.periodEndsEpochDay - today <= 30 } +
                items.count { !it.isUsed && it.expiresEpochDay != null && it.expiresEpochDay - today <= 30 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BenefitsUiState())

    private val _edit = MutableStateFlow(EditBenefitState())
    val editState: StateFlow<EditBenefitState> = _edit.asStateFlow()

    fun toggleCredit(credit: CreditStatus) {
        viewModelScope.launch { benefitsRepository.markCreditUsed(credit.walletCard.walletCard.id, credit.credit.label, credit.isAvailable) }
    }

    fun toggleItemUsed(item: BenefitItem) {
        viewModelScope.launch { benefitsRepository.setUsed(item.id, !item.isUsed) }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { benefitsRepository.delete(id) }
    }

    fun openAdd(existing: BenefitItem? = null) {
        _edit.value = if (existing == null) {
            EditBenefitState(isOpen = true)
        } else {
            EditBenefitState(
                isOpen = true,
                id = existing.id,
                kind = existing.kind,
                title = existing.title,
                program = existing.program,
                walletCardId = existing.walletCardId,
                valueUsd = existing.valueUsd?.let { "%.0f".format(it) }.orEmpty(),
                expiresOn = existing.expiresEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
                notes = existing.notes.orEmpty(),
            )
        }
    }

    fun closeEdit() {
        _edit.value = EditBenefitState(isOpen = false)
    }

    fun updateEdit(transform: (EditBenefitState) -> EditBenefitState) {
        _edit.value = transform(_edit.value)
    }

    fun saveEdit() {
        val s = _edit.value
        if (s.title.isBlank()) return
        viewModelScope.launch {
            val value = s.valueUsd.replace("$", "").replace(",", "").trim().toDoubleOrNull()
            val expires = parseUserDate(s.expiresOn)
            if (s.id == 0L) {
                benefitsRepository.addItem(s.kind, s.title.trim(), s.program, s.walletCardId, value, expires, s.notes.trim().ifBlank { null })
            } else {
                val existing = uiState.value.items.firstOrNull { it.id == s.id }
                if (existing != null) {
                    benefitsRepository.update(
                        existing.copy(kind = s.kind, title = s.title.trim(), program = s.program, walletCardId = s.walletCardId, valueUsd = value, expiresEpochDay = expires, notes = s.notes.trim().ifBlank { null }),
                    )
                }
            }
            closeEdit()
        }
    }
}
