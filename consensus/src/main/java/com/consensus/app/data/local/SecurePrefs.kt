package com.consensus.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.consensus.app.domain.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypted-at-rest storage (Android Keystore) for the four provider API keys. */
@Singleton
class SecurePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "consensus_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _configured = MutableStateFlow(readConfigured())
    /** Which providers currently have a non-blank key. */
    val configured: StateFlow<Set<ProviderId>> = _configured

    fun apiKey(provider: ProviderId): String? = prefs.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }

    fun setApiKey(provider: ProviderId, value: String?) {
        prefs.edit().putString(keyName(provider), value?.trim()).apply()
        _configured.value = readConfigured()
    }

    private fun readConfigured(): Set<ProviderId> =
        ProviderId.values().filter { !prefs.getString(keyName(it), null).isNullOrBlank() }.toSet()

    private fun keyName(p: ProviderId) = "api_key_" + p.name.lowercase()
}
