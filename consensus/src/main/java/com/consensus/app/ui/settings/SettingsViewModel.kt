package com.consensus.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consensus.app.data.local.AppSettings
import com.consensus.app.data.local.SecurePrefs
import com.consensus.app.data.local.SettingsStore
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings.DEFAULT,
    val keys: Map<ProviderId, String> = emptyMap(),
    val dirty: Boolean = false,
    val savedMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val s = settingsStore.current()
            _state.value = SettingsUiState(
                loaded = true,
                settings = s,
                keys = ProviderId.values().associateWith { securePrefs.apiKey(it).orEmpty() },
            )
        }
    }

    fun setKey(p: ProviderId, value: String) = edit { it.copy(keys = it.keys + (p to value)) }

    fun setModelId(p: ProviderId, t: Tier, value: String) = edit { st ->
        val perProvider = (st.settings.modelIds[p] ?: emptyMap()) + (t to value)
        st.copy(settings = st.settings.copy(modelIds = st.settings.modelIds + (p to perProvider)))
    }

    fun setDefaultTier(p: ProviderId, t: Tier) = edit { it.copy(settings = it.settings.copy(defaultTier = it.settings.defaultTier + (p to t))) }
    fun setEnabled(p: ProviderId, on: Boolean) = edit { it.copy(settings = it.settings.copy(enabled = it.settings.enabled + (p to on))) }
    fun setDefaultJudge(p: ProviderId) = edit { it.copy(settings = it.settings.copy(defaultJudge = p)) }
    fun setDefaultRounds(r: Int) = edit { it.copy(settings = it.settings.copy(defaultRounds = r)) }
    fun setStopWhenAgreed(b: Boolean) = edit { it.copy(settings = it.settings.copy(stopWhenAgreed = b)) }
    fun setWebSearch(b: Boolean) = edit { it.copy(settings = it.settings.copy(webSearch = b)) }

    fun save() {
        val st = _state.value
        viewModelScope.launch {
            st.keys.forEach { (p, v) -> securePrefs.setApiKey(p, v.trim().ifBlank { null }) }
            settingsStore.save(st.settings)
            _state.value = st.copy(dirty = false, savedMessage = "Saved")
        }
    }

    fun clearMessage() = edit { it.copy(savedMessage = null) }

    private fun edit(transform: (SettingsUiState) -> SettingsUiState) {
        _state.value = transform(_state.value).let { if (it.savedMessage == null) it.copy(dirty = true) else it }
    }
}
