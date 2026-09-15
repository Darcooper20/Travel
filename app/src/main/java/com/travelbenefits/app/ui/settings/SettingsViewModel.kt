package com.travelbenefits.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.auth.OAuthSetupInfo
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.SyncSettings
import com.travelbenefits.app.data.repository.ActivityRepository
import com.travelbenefits.app.data.repository.BackupRepository
import com.travelbenefits.app.data.repository.PlaidRepository
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.auth.PlaidLinkCoordinator
import com.travelbenefits.app.domain.model.PlaidItem
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.travelbenefits.app.data.repository.EmailMonitorRepository
import com.travelbenefits.app.notifications.AppNotifier
import com.travelbenefits.app.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val anthropicApiKey: String = "",
    val googleClientId: String = "",
    val gmailAccountEmail: String? = null,
    val plaidBackendUrl: String = "",
    val plaidAppToken: String = "",
    val seatsAeroKey: String = "",
)

data class PlaidUiState(
    val items: List<PlaidItem> = emptyList(),
    val cards: List<ResolvedWalletCard> = emptyList(),
    val linkState: PlaidLinkCoordinator.LinkState = PlaidLinkCoordinator.LinkState.Idle,
    val isBusy: Boolean = false,
    val transactionCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val appPrefs: AppPrefs,
    private val gmailAuthManager: GmailAuthManager,
    val oauthSetupInfo: OAuthSetupInfo,
    private val syncScheduler: SyncScheduler,
    private val emailMonitorRepository: EmailMonitorRepository,
    private val activityRepository: ActivityRepository,
    private val appNotifier: AppNotifier,
    private val backupRepository: BackupRepository,
    private val plaidRepository: PlaidRepository,
    private val plaidLinkCoordinator: PlaidLinkCoordinator,
    private val overrideRepository: OverrideRepository,
    private val offerRepository: com.travelbenefits.app.data.repository.OfferRepository,
    walletRepository: WalletRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val plaidBusy = MutableStateFlow(false)
    val plaidState: StateFlow<PlaidUiState> = combine(
        plaidRepository.observeItems(),
        walletRepository.observeResolvedCards(),
        plaidLinkCoordinator.state,
        plaidBusy,
        plaidRepository.observeTransactionCount(),
    ) { items, cards, link, busy, count -> PlaidUiState(items, cards, link, busy, count) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaidUiState())

    fun onSeatsAeroKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(seatsAeroKey = value)
        securePrefs.seatsAeroApiKey = value.trim().ifBlank { null }
    }

    val preferences: StateFlow<com.travelbenefits.app.domain.model.UserPreferences> = overrideRepository.observeOverrides()
        .map { com.travelbenefits.app.domain.model.UserPreferences.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.travelbenefits.app.domain.model.UserPreferences())

    fun setPreference(key: String, value: String?) { viewModelScope.launch { overrideRepository.set(OverrideRepository.SCOPE_GLOBAL, key, value?.trim()?.ifBlank { null }) } }

    fun resetPreferences() { viewModelScope.launch { com.travelbenefits.app.domain.model.UserPreferences.ALL_KEYS.forEach { overrideRepository.set(OverrideRepository.SCOPE_GLOBAL, it, null) } } }

    val members: StateFlow<List<com.travelbenefits.app.data.repository.HouseholdMember>> = offerRepository.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMember(name: String) { if (name.isNotBlank()) viewModelScope.launch { offerRepository.addMember(name, null) } }
    fun deleteMember(id: Long) { viewModelScope.launch { offerRepository.deleteMember(id) } }

    fun onPlaidBackendUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(plaidBackendUrl = value)
        securePrefs.plaidBackendUrl = value.trim().ifBlank { null }
        syncScheduler.applyPlaidSchedule(plaidRepository.isConfigured)
    }

    fun onPlaidAppTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(plaidAppToken = value)
        securePrefs.plaidAppToken = value.trim().ifBlank { null }
        syncScheduler.applyPlaidSchedule(plaidRepository.isConfigured)
    }

    /** Fetches a link token from the backend and hands it to the Activity to open Plaid Link. */
    fun startPlaidLink(launch: (String) -> Unit) {
        viewModelScope.launch {
            plaidBusy.value = true
            plaidRepository.createLinkToken().fold(
                onSuccess = { token -> plaidLinkCoordinator.reset(); launch(token) },
                onFailure = { _message.value = "Couldn't get a link token: ${it.message}" },
            )
            plaidBusy.value = false
        }
    }

    fun acknowledgePlaidLink() = plaidLinkCoordinator.reset()

    fun mapPlaidAccount(accountId: String, walletCardId: Long?) {
        viewModelScope.launch { plaidRepository.mapAccount(accountId, walletCardId) }
    }

    fun removePlaidItem(itemId: String) {
        viewModelScope.launch {
            plaidRepository.removeItem(itemId).onFailure { _message.value = "Couldn't remove: ${it.message}" }
        }
    }

    fun syncPlaidNow() {
        if (plaidBusy.value) return
        viewModelScope.launch {
            plaidBusy.value = true
            _message.value = plaidRepository.syncAll().fold({ it.summary() }, { "Sync failed: ${it.message}" })
            plaidBusy.value = false
        }
    }

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            anthropicApiKey = securePrefs.anthropicApiKey.orEmpty(),
            googleClientId = securePrefs.googleOAuthClientId.orEmpty(),
            gmailAccountEmail = securePrefs.gmailAccountEmail,
            plaidBackendUrl = securePrefs.plaidBackendUrl.orEmpty(),
            plaidAppToken = securePrefs.plaidAppToken.orEmpty(),
            seatsAeroKey = securePrefs.seatsAeroApiKey.orEmpty(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val syncSettings: StateFlow<SyncSettings> = appPrefs.syncSettings

    val isGmailConnected: StateFlow<Boolean> = gmailAuthManager.isSignedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    fun hasNotificationPermission(): Boolean = appNotifier.hasPermission()

    fun onAnthropicApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(anthropicApiKey = value)
        securePrefs.anthropicApiKey = value.trim().ifBlank { null }
    }

    fun onGoogleClientIdChange(value: String) {
        _uiState.value = _uiState.value.copy(googleClientId = value)
        securePrefs.googleOAuthClientId = value.trim().ifBlank { null }
    }

    /** The scheme this client ID needs when this build cannot receive it; null when they agree. */
    fun redirectSchemeMismatch(): String? = oauthSetupInfo.mismatchFor(_uiState.value.googleClientId)

    /** Null when no Google OAuth client ID has been configured yet, or the intent couldn't be built. */
    fun buildGmailAuthIntent(): Intent? {
        val clientId = _uiState.value.googleClientId.trim()
        if (clientId.isBlank()) return null
        // Fail here with an explanation rather than sending the user to a browser
        // that will bounce the response into a scheme this build does not own.
        redirectSchemeMismatch()?.let {
            _message.value = "This build listens on ${oauthSetupInfo.redirectScheme}, but that client ID needs $it. " +
                "Sign-in cannot complete until you install a build made with that scheme - see README.md."
            return null
        }
        return try {
            gmailAuthManager.createAuthIntent(clientId)
        } catch (e: Exception) {
            _message.value = "Couldn't start Google sign-in: ${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    fun disconnectGmail() {
        gmailAuthManager.signOut()
        _uiState.value = _uiState.value.copy(gmailAccountEmail = null)
        updateSync { it.copy(autoSyncEnabled = false) }
    }

    fun updateSync(transform: (SyncSettings) -> SyncSettings) {
        appPrefs.update(transform)
        syncScheduler.applyCurrentSettings()
    }

    fun resetScanHistory() {
        viewModelScope.launch {
            emailMonitorRepository.resetHistory()
            _message.value = "Scan history cleared - the next sync re-reads the full lookback window."
        }
    }

    fun clearActivity() {
        viewModelScope.launch { activityRepository.clear() }
    }

    val valuations: StateFlow<Map<RewardCurrency, Double>> = overrideRepository.observeOverrides()
        .map { overrideRepository.valuations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setValuation(currency: RewardCurrency, centsPerPoint: String) {
        viewModelScope.launch { overrideRepository.set("currency:${currency.name}", OverrideRepository.KEY_VALUATION, centsPerPoint.trim().toDoubleOrNull()?.toString()) }
    }

    fun showSetupGuideAgain() = appPrefs.setOnboardingDone(false)

    fun runRemindersNow() {
        syncScheduler.runRemindersNow()
        _message.value = "Reminder check queued - anything new arrives as a notification."
    }

    /** Writes a JSON or CSV export to the document the user picked. */
    fun exportTo(uri: Uri, csv: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                val text = if (csv) backupRepository.exportBalancesCsv() else backupRepository.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Couldn't open the file for writing.")
                }
            }
            _message.value = result.fold({ "Exported ${if (csv) "balances CSV" else "JSON backup"}." }, { "Export failed: ${it.message}" })
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Couldn't read the file.") }
            }.getOrElse { _message.value = "Import failed: ${it.message}"; return@launch }
            _message.value = backupRepository.importJson(text).fold({ it }, { "Import failed: ${it.message}" })
        }
    }
}
