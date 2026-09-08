package com.travelbenefits.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.auth.GmailAuthManager
import com.travelbenefits.app.data.local.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val anthropicApiKey: String = "",
    val googleClientId: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val gmailAuthManager: GmailAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            anthropicApiKey = securePrefs.anthropicApiKey.orEmpty(),
            googleClientId = securePrefs.googleOAuthClientId.orEmpty(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val isGmailConnected: StateFlow<Boolean> = gmailAuthManager.isSignedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onAnthropicApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(anthropicApiKey = value)
        securePrefs.anthropicApiKey = value.trim().ifBlank { null }
    }

    fun onGoogleClientIdChange(value: String) {
        _uiState.value = _uiState.value.copy(googleClientId = value)
        securePrefs.googleOAuthClientId = value.trim().ifBlank { null }
    }

    /** Null when no Google OAuth client ID has been configured yet. */
    fun buildGmailAuthIntent(): Intent? {
        val clientId = _uiState.value.googleClientId.trim()
        if (clientId.isBlank()) return null
        return gmailAuthManager.createAuthIntent(clientId)
    }

    fun disconnectGmail() {
        gmailAuthManager.signOut()
    }
}
