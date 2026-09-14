package com.travelbenefits.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.repository.ActivityRepository
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.EmailMonitorRepository
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.LoyaltyInsights
import com.travelbenefits.app.domain.model.ActivityEvent
import com.travelbenefits.app.domain.model.Alert
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgramKind
import com.travelbenefits.app.domain.model.Trip
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

data class ProgramSummary(
    val account: LoyaltyAccount,
    val balanceLabel: String?,
    val estimatedValueUsd: Double?,
)

data class DashboardUiState(
    val accounts: List<ProgramSummary> = emptyList(),
    val portfolioValueUsd: Double = 0.0,
    val hotelPointsTotal: Long = 0,
    val airlineMilesTotal: Long = 0,
    val shopValueUsd: Double = 0.0,
    val cardRewardsValueUsd: Double = 0.0,
    val cardsWithBalance: Int = 0,
    val cardCount: Int = 0,
    val unusedCreditsUsd: Double = 0.0,
    val unusedCreditCount: Int = 0,
    val upcomingTrips: List<Trip> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val activity: List<ActivityEvent> = emptyList(),
    val unreadActivity: Int = 0,
    val lastSyncAt: Long = 0,
    val lastSyncSummary: String? = null,
    val autoSyncEnabled: Boolean = false,
    val isGmailConnected: Boolean = false,
    val hasAnthropicKey: Boolean = false,
)

sealed class SyncUiState {
    data object Idle : SyncUiState()
    data class Running(val message: String) : SyncUiState()
    data class Done(val summary: String) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    loyaltyRepository: LoyaltyRepository,
    tripRepository: TripRepository,
    walletRepository: WalletRepository,
    private val activityRepository: ActivityRepository,
    benefitsRepository: BenefitsRepository,
    private val emailMonitorRepository: EmailMonitorRepository,
    private val insights: LoyaltyInsights,
    private val appPrefs: AppPrefs,
    private val securePrefs: SecurePrefs,
    gmailAuthManager: GmailAuthManager,
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val benefitsFlow = combine(benefitsRepository.observeCreditStatuses(), benefitsRepository.observeItems()) { credits, items -> credits to items }

    private val core = combine(
        loyaltyRepository.observeAccounts(),
        tripRepository.observeTrips(),
        walletRepository.observeResolvedCards(),
        combine(activityRepository.observeRecent(40), activityRepository.observeUnreadCount()) { a, u -> a to u },
        benefitsFlow,
    ) { accounts, trips, cards, activityPair, benefitPair ->
        val (activity, unread) = activityPair
        val (credits, items) = benefitPair
        val today = LocalDate.now().toEpochDay()
        val cardValue = insights.cardRewardsValueUsd(cards)
        DashboardUiState(
            accounts = accounts.map { ProgramSummary(it, insights.balanceLabel(it), insights.estimatedValueUsd(it)) }
                .sortedByDescending { it.estimatedValueUsd ?: -1.0 },
            portfolioValueUsd = insights.portfolioValueUsd(accounts) + cardValue,
            hotelPointsTotal = accounts.filter { it.program.kind == LoyaltyProgramKind.HOTEL }.sumOf { it.pointsNumeric ?: 0L },
            airlineMilesTotal = accounts.filter { it.program.kind == LoyaltyProgramKind.AIRLINE }.sumOf { it.pointsNumeric ?: 0L },
            shopValueUsd = accounts.filter { it.program.kind == LoyaltyProgramKind.SHOP }.sumOf { insights.estimatedValueUsd(it) ?: 0.0 },
            cardRewardsValueUsd = cardValue,
            cardsWithBalance = cards.count { it.walletCard.rewardsBalance != null },
            cardCount = cards.size,
            unusedCreditsUsd = credits.filter { it.isAvailable }.sumOf { it.periodValueUsd },
            unusedCreditCount = credits.count { it.isAvailable } + items.count { !it.isUsed },
            upcomingTrips = trips.filter { it.isUpcoming(today) }.sortedBy { it.startEpochDay ?: Long.MAX_VALUE }.take(5),
            alerts = insights.alerts(accounts, trips, cards, credits, items),
            activity = activity,
            unreadActivity = unread,
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        core,
        appPrefs.lastSyncAt,
        appPrefs.lastSyncSummary,
        appPrefs.syncSettings,
        gmailAuthManager.isSignedIn,
    ) { state, lastSync, summary, settings, gmail ->
        state.copy(
            lastSyncAt = lastSync,
            lastSyncSummary = summary,
            autoSyncEnabled = settings.autoSyncEnabled,
            isGmailConnected = gmail,
            hasAnthropicKey = !securePrefs.anthropicApiKey.isNullOrBlank(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun syncNow() {
        if (_syncState.value is SyncUiState.Running) return
        viewModelScope.launch {
            _syncState.value = SyncUiState.Running("Starting…")
            emailMonitorRepository.sync { message -> _syncState.value = SyncUiState.Running(message) }.fold(
                onSuccess = { report -> _syncState.value = SyncUiState.Done(report.summary()) },
                onFailure = { error -> _syncState.value = SyncUiState.Error(error.message ?: "Sync failed.") },
            )
        }
    }

    fun dismissSyncState() {
        _syncState.value = SyncUiState.Idle
    }

    fun markActivityRead() {
        viewModelScope.launch { activityRepository.markAllRead() }
    }
}
