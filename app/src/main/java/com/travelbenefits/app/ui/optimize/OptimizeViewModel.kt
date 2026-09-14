package com.travelbenefits.app.ui.optimize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.PointsAdvisorRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.PointsOptimizer
import com.travelbenefits.app.domain.RecommendationEngine
import com.travelbenefits.app.domain.model.CashVsPointsInput
import com.travelbenefits.app.domain.model.CashVsPointsResult
import com.travelbenefits.app.domain.model.EarnPlanEntry
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.RecommendationEntry
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.SpendingCategory
import com.travelbenefits.app.domain.model.TransferOption
import com.travelbenefits.app.domain.model.TransferPartner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Earn" tab: best card by category, either for overall value or aimed at one program. */
data class EarnUiState(
    val category: SpendingCategory = SpendingCategory.HOTELS,
    /** Null = rank by estimated cash value; non-null = rank by points landed in that program. */
    val targetProgram: LoyaltyProgram? = null,
    val byValue: List<RecommendationEntry> = emptyList(),
    val byProgram: List<EarnPlanEntry> = emptyList(),
)

data class RedeemFormState(
    val program: LoyaltyProgram = LoyaltyProgram.WORLD_OF_HYATT,
    val cashPrice: String = "",
    val pointsRequired: String = "",
    val awardFees: String = "0",
    val payWithCardId: Long? = null,
    val result: CashVsPointsResult? = null,
)

data class TransferUiState(
    val program: LoyaltyProgram = LoyaltyProgram.WORLD_OF_HYATT,
    val fromWallet: List<TransferOption> = emptyList(),
    /** Everything the catalog knows for this program, including currencies the user doesn't hold. */
    val allPartners: List<TransferPartner> = emptyList(),
    val currentBalance: Long? = null,
)

data class AdvisorMessage(val isUser: Boolean, val text: String)

data class AdvisorUiState(
    val question: String = "",
    val messages: List<AdvisorMessage> = emptyList(),
    val isThinking: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OptimizeViewModel @Inject constructor(
    walletRepository: WalletRepository,
    loyaltyRepository: LoyaltyRepository,
    private val recommendationEngine: RecommendationEngine,
    private val optimizer: PointsOptimizer,
    private val advisorRepository: PointsAdvisorRepository,
) : ViewModel() {

    val cards: StateFlow<List<ResolvedWalletCard>> = walletRepository.observeResolvedCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<LoyaltyAccount>> = loyaltyRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Earn ----
    private val earnCategory = MutableStateFlow(SpendingCategory.HOTELS)
    private val earnTarget = MutableStateFlow<LoyaltyProgram?>(null)

    val earnState: StateFlow<EarnUiState> = combine(cards, earnCategory, earnTarget) { cards, category, target ->
        EarnUiState(
            category = category,
            targetProgram = target,
            byValue = if (target == null) recommendationEngine.rank(cards, category) else emptyList(),
            byProgram = if (target != null) optimizer.earnPlan(target, category, cards) else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EarnUiState())

    fun selectEarnCategory(category: SpendingCategory) { earnCategory.value = category }
    fun selectEarnTarget(program: LoyaltyProgram?) { earnTarget.value = program }

    // ---- Redeem (cash vs points) ----
    private val _redeem = MutableStateFlow(RedeemFormState())
    val redeemState: StateFlow<RedeemFormState> = _redeem.asStateFlow()

    fun updateRedeem(transform: (RedeemFormState) -> RedeemFormState) {
        _redeem.value = transform(_redeem.value).copy(result = null)
    }

    fun evaluateRedeem() {
        val s = _redeem.value
        val cash = s.cashPrice.replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: return
        val points = LoyaltyAccount.parsePoints(s.pointsRequired) ?: return
        val fees = s.awardFees.replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: 0.0
        val card = cards.value.firstOrNull { it.walletCard.id == s.payWithCardId }
        val tier = accounts.value.firstOrNull { it.program == s.program }?.tier
        val result = optimizer.cashVsPoints(
            CashVsPointsInput(
                program = s.program,
                cashPriceUsd = cash,
                pointsRequired = points,
                awardTaxesFeesUsd = fees,
                payWithCard = card,
                memberTier = tier,
            ),
        )
        _redeem.value = s.copy(result = result)
    }

    // ---- Transfer ----
    private val transferProgram = MutableStateFlow(LoyaltyProgram.WORLD_OF_HYATT)

    val transferState: StateFlow<TransferUiState> = combine(cards, accounts, transferProgram) { cards, accounts, program ->
        TransferUiState(
            program = program,
            fromWallet = optimizer.transferOptions(program, cards),
            allPartners = TransferPartnerCatalog.partnersInto(program),
            currentBalance = accounts.firstOrNull { it.program == program }?.pointsNumeric,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransferUiState())

    fun selectTransferProgram(program: LoyaltyProgram) { transferProgram.value = program }

    fun profileFor(program: LoyaltyProgram) = LoyaltyProgramCatalog.profileFor(program)

    // ---- Advisor ----
    private val _advisor = MutableStateFlow(AdvisorUiState())
    val advisorState: StateFlow<AdvisorUiState> = _advisor.asStateFlow()

    fun updateQuestion(text: String) {
        _advisor.value = _advisor.value.copy(question = text)
    }

    fun ask() {
        val question = _advisor.value.question.trim()
        if (question.isBlank() || _advisor.value.isThinking) return
        _advisor.value = _advisor.value.copy(
            question = "",
            messages = _advisor.value.messages + AdvisorMessage(isUser = true, text = question),
            isThinking = true,
            error = null,
        )
        viewModelScope.launch {
            advisorRepository.ask(question, accounts.value, cards.value).fold(
                onSuccess = { answer ->
                    _advisor.value = _advisor.value.copy(
                        messages = _advisor.value.messages + AdvisorMessage(isUser = false, text = answer.ifBlank { "No answer came back - try rephrasing." }),
                        isThinking = false,
                    )
                },
                onFailure = { error ->
                    _advisor.value = _advisor.value.copy(isThinking = false, error = error.message ?: "Request failed.")
                },
            )
        }
    }

    fun clearAdvisor() {
        _advisor.value = AdvisorUiState()
    }
}
