package com.travelbenefits.app.ui.optimize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.catalog.TransferPartnerCatalog
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.PointsAdvisorRepository
import com.travelbenefits.app.data.repository.ResearchRepository
import com.travelbenefits.app.data.repository.PlaidRepository
import com.travelbenefits.app.domain.SpendAnalyzer
import com.travelbenefits.app.domain.CapUsageCalculator
import com.travelbenefits.app.domain.MerchantClassifier
import com.travelbenefits.app.domain.PurchaseRecommender
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.Confidence
import com.travelbenefits.app.domain.model.PurchaseQuery
import com.travelbenefits.app.domain.model.PurchaseRecommendation
import com.travelbenefits.app.domain.model.SpendPeriod
import com.travelbenefits.app.domain.model.SpendReport
import com.travelbenefits.app.domain.LoyaltyInsights
import com.travelbenefits.app.domain.model.AwardWatch
import com.travelbenefits.app.domain.model.TransferBonus
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
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.data.local.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /** Researched bonuses into this program, keyed by source currency. */
    val bonuses: Map<RewardCurrency, TransferBonus> = emptyMap(),
    val allBonuses: List<TransferBonus> = emptyList(),
    val bonusesCheckedAt: Long? = null,
    val isRefreshingBonuses: Boolean = false,
)

/** "What your balances buy" row: one program with a balance, translated into nights/flights at the catalog's award bands. */
data class BalanceBuys(
    val account: LoyaltyAccount,
    val balanceLabel: String?,
    val lowNights: Int?,
    val typicalNights: Int?,
    val highNights: Int?,
    val bandNote: String?,
)

/** "Which card for this purchase?" form + result. */
data class PurchaseUiState(
    val merchant: String = "",
    val amount: String = "",
    val isForeign: Boolean = false,
    val channel: BookingChannel = BookingChannel.ANY,
    /** Null = use the classifier's guess. */
    val categoryOverride: SpendingCategory? = null,
    val guessedCategory: SpendingCategory? = null,
    val guessConfidence: Confidence = Confidence.LOW,
    val guessSource: String? = null,
    val recommendation: PurchaseRecommendation? = null,
)

data class SpendUiState(
    val period: SpendPeriod = SpendPeriod.LAST_MONTH,
    val report: SpendReport? = null,
    val isConfigured: Boolean = false,
    val hasAccounts: Boolean = false,
    val isSyncing: Boolean = false,
    val message: String? = null,
)

data class WatchUiState(
    val watches: List<AwardWatch> = emptyList(),
    val isChecking: Boolean = false,
    val message: String? = null,
    val researchEnabled: Boolean = false,
)

data class AddWatchState(
    val isOpen: Boolean = false,
    val title: String = "",
    val program: LoyaltyProgram? = null,
    val origin: String = "",
    val destination: String = "",
    val dateFrom: String = "",
    val dateTo: String = "",
    val notes: String = "",
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
    private val walletRepository: WalletRepository,
    private val loyaltyRepository: LoyaltyRepository,
    private val tripRepository: com.travelbenefits.app.data.repository.TripRepository,
    private val recommendationEngine: RecommendationEngine,
    private val optimizer: PointsOptimizer,
    private val advisorRepository: PointsAdvisorRepository,
    private val researchRepository: ResearchRepository,
    private val insights: LoyaltyInsights,
    private val appPrefs: AppPrefs,
    private val syncScheduler: com.travelbenefits.app.work.SyncScheduler,
    private val plaidRepository: PlaidRepository,
    private val spendAnalyzer: SpendAnalyzer,
    private val purchaseRecommender: PurchaseRecommender,
    private val merchantClassifier: MerchantClassifier,
    private val capUsageCalculator: CapUsageCalculator,
    private val overrideRepository: OverrideRepository,
) : ViewModel() {


    val cards: StateFlow<List<ResolvedWalletCard>> = walletRepository.observeResolvedCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<LoyaltyAccount>> = loyaltyRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Spend (Plaid) ----
    private val spendPeriod = MutableStateFlow(SpendPeriod.LAST_MONTH)
    private val spendSyncing = MutableStateFlow(false)
    private val spendMessage = MutableStateFlow<String?>(null)

    val spendState: StateFlow<SpendUiState> = combine(
        combine(plaidRepository.observeTransactions(), plaidRepository.observeAccounts(), cards) { t, a, c -> Triple(t, a, c) },
        spendPeriod,
        spendSyncing,
        spendMessage,
    ) { (txns, accounts, cards), period, syncing, message ->
        SpendUiState(
            period = period,
            report = if (accounts.isEmpty()) null else spendAnalyzer.analyze(txns, accounts, cards, period),
            isConfigured = plaidRepository.isConfigured,
            hasAccounts = accounts.isNotEmpty(),
            isSyncing = syncing,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendUiState())

    fun selectSpendPeriod(period: SpendPeriod) { spendPeriod.value = period }

    fun syncSpendNow() {
        if (spendSyncing.value) return
        viewModelScope.launch {
            spendSyncing.value = true
            spendMessage.value = plaidRepository.syncAll().fold({ it.summary() }, { it.message ?: "Sync failed." })
            spendSyncing.value = false
        }
    }

    fun clearSpendMessage() { spendMessage.value = null }

    // ---- Purchase (which card for this purchase?) ----
    private val purchaseForm = MutableStateFlow(PurchaseUiState())

    val purchaseState: StateFlow<PurchaseUiState> = combine(
        purchaseForm,
        cards,
        plaidRepository.observeTransactions(),
        overrideRepository.observeOverrides(),
    ) { form, cards, txns, overrides ->
        val guess = if (form.merchant.isBlank()) null else merchantClassifier.classify(form.merchant, txns)
        val category = form.categoryOverride ?: guess?.category
        val amount = form.amount.replace("$", "").replace(",", "").trim().toDoubleOrNull()
        val recommendation = if (amount != null && amount > 0 && cards.isNotEmpty()) {
            purchaseRecommender.recommend(
                PurchaseQuery(
                    merchant = form.merchant.ifBlank { null }, category = category, amountUsd = amount, isForeign = form.isForeign, channel = form.channel,
                    categoryConfidence = if (form.categoryOverride != null) Confidence.HIGH else guess?.confidence ?: Confidence.LOW,
                    categorySource = if (form.categoryOverride != null) "your choice" else guess?.source,
                ),
                cards,
                capUsageCalculator.compute(cards, txns, overrideRepository.capSpend(overrides)),
                overrideRepository.valuations(overrides),
            )
        } else null
        form.copy(guessedCategory = guess?.category, guessConfidence = guess?.confidence ?: Confidence.LOW, guessSource = guess?.source, recommendation = recommendation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurchaseUiState())

    fun updatePurchase(transform: (PurchaseUiState) -> PurchaseUiState) { purchaseForm.value = transform(purchaseForm.value) }

    /** Prefills the form from a widget/shortcut query. */
    fun prefillPurchase(merchant: String?) { if (!merchant.isNullOrBlank()) purchaseForm.value = purchaseForm.value.copy(merchant = merchant) }

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
    private val refreshingBonuses = MutableStateFlow(false)

    val transferState: StateFlow<TransferUiState> = combine(cards, accounts, transferProgram, researchRepository.observeBonuses(), refreshingBonuses) { cards, accounts, program, bonuses, refreshing ->
        val real = bonuses.filter { it.bonusPercent > 0 }
        TransferUiState(
            program = program,
            fromWallet = optimizer.transferOptions(program, cards),
            allPartners = TransferPartnerCatalog.partnersInto(program),
            currentBalance = accounts.firstOrNull { it.program == program }?.pointsNumeric,
            bonuses = real.filter { it.to == program }.associateBy { it.from },
            allBonuses = real,
            bonusesCheckedAt = bonuses.maxOfOrNull { it.checkedAt },
            isRefreshingBonuses = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransferUiState())

    fun selectTransferProgram(program: LoyaltyProgram) { transferProgram.value = program }

    /** Records a completed points transfer so balances move and totals don't double count (attribution ledger). Never initiates a transfer. */
    fun recordTransfer(card: ResolvedWalletCard, program: LoyaltyProgram, cardPoints: Long, programPointsReceived: Long) {
        viewModelScope.launch {
            val current = card.walletCard.rewardsBalance
            if (current != null) walletRepository.updateRewardsBalance(card.walletCard.id, (current - cardPoints).coerceAtLeast(0), System.currentTimeMillis())
            loyaltyRepository.adjustBalance(program, programPointsReceived, "Transfer from ${card.displayName}")
            tripRepository.recordAttribution("TRANSFER", card.rewardCurrency?.name, program.name, programPointsReceived.toDouble(), null, "$cardPoints ${card.rewardCurrency?.displayName ?: "points"} → $programPointsReceived ${program.displayName}")
            _watch.value = _watch.value.copy(message = "Transfer recorded: balances updated, attribution logged.")
        }
    }

    fun refreshBonuses() {
        if (refreshingBonuses.value) return
        viewModelScope.launch {
            refreshingBonuses.value = true
            researchRepository.refreshTransferBonuses().onFailure { e -> _watch.value = _watch.value.copy(message = e.message) }
            refreshingBonuses.value = false
        }
    }

    // ---- What balances buy ----
    val balanceBuys: StateFlow<List<BalanceBuys>> = accounts.map { list ->
        list.filter { it.pointsNumeric != null && it.program.kind != com.travelbenefits.app.domain.model.LoyaltyProgramKind.SHOP }.map { a ->
            val band = insights.profile(a.program).awardBand
            val pts = a.pointsNumeric ?: 0L
            BalanceBuys(
                account = a,
                balanceLabel = insights.balanceLabel(a),
                lowNights = band?.let { (pts / it.lowNightPoints).toInt() },
                typicalNights = band?.let { (pts / it.typicalNightPoints).toInt() },
                highNights = band?.let { (pts / it.highNightPoints).toInt() },
                bandNote = band?.note,
            )
        }.sortedByDescending { it.typicalNights ?: -1 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Award watches ----
    private val _watch = MutableStateFlow(WatchUiState())
    val watchState: StateFlow<WatchUiState> = combine(_watch, researchRepository.observeWatches(), appPrefs.syncSettings) { ui, watches, settings ->
        ui.copy(watches = watches, researchEnabled = settings.researchEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WatchUiState())

    private val _addWatch = MutableStateFlow(AddWatchState())
    val addWatchState: StateFlow<AddWatchState> = _addWatch.asStateFlow()

    fun openAddWatch() { _addWatch.value = AddWatchState(isOpen = true) }
    fun closeAddWatch() { _addWatch.value = AddWatchState(isOpen = false) }
    fun updateAddWatch(transform: (AddWatchState) -> AddWatchState) { _addWatch.value = transform(_addWatch.value) }

    fun saveWatch() {
        val s = _addWatch.value
        val title = s.title.trim().ifBlank { listOfNotNull(s.origin.trim().ifBlank { null }, s.destination.trim().ifBlank { null }).joinToString(" → ") }
        if (title.isBlank()) return
        viewModelScope.launch {
            researchRepository.addWatch(title, s.program, s.origin.trim().ifBlank { null }, s.destination.trim().ifBlank { null }, s.dateFrom.trim().ifBlank { null }, s.dateTo.trim().ifBlank { null }, s.notes.trim().ifBlank { null })
            closeAddWatch()
        }
    }

    fun setWatchActive(id: Long, active: Boolean) { viewModelScope.launch { researchRepository.setActive(id, active) } }
    fun deleteWatch(id: Long) { viewModelScope.launch { researchRepository.deleteWatch(id) } }

    fun checkWatchNow(id: Long? = null) {
        if (_watch.value.isChecking) return
        viewModelScope.launch {
            _watch.value = _watch.value.copy(isChecking = true, message = null)
            researchRepository.checkWatches(onlyId = id).fold(
                onSuccess = { results -> _watch.value = _watch.value.copy(isChecking = false, message = if (results.isEmpty()) "Nothing to check." else "${results.count { it.found }} of ${results.size} watch(es) look available.") },
                onFailure = { e -> _watch.value = _watch.value.copy(isChecking = false, message = e.message ?: "Check failed.") },
            )
        }
    }

    fun setResearchEnabled(enabled: Boolean) {
        appPrefs.update { it.copy(researchEnabled = enabled) }
        syncScheduler.applyCurrentSettings()
    }

    fun clearWatchMessage() { _watch.value = _watch.value.copy(message = null) }

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
