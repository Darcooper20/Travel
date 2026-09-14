package com.travelbenefits.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.data.local.SyncSettings
import com.travelbenefits.app.data.repository.ActivityRepository
import com.travelbenefits.app.data.repository.BackupRepository
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val appPrefs: AppPrefs,
    private val gmailAuthManager: GmailAuthManager,
    private val syncScheduler: SyncScheduler,
    private val emailMonitorRepository: EmailMonitorRepository,
    private val activityRepository: ActivityRepository,
    private val appNotifier: AppNotifier,
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            anthropicApiKey = securePrefs.anthropicApiKey.orEmpty(),
            googleClientId = securePrefs.googleOAuthClientId.orEmpty(),
            gmailAccountEmail = securePrefs.gmailAccountEmail,
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

    /** Null when no Google OAuth client ID has been configured yet, or the intent couldn't be built. */
    fun buildGmailAuthIntent(): Intent? {
        val clientId = _uiState.value.googleClientId.trim()
        if (clientId.isBlank()) return null
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
