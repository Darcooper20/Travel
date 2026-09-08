package com.travelbenefits.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted-at-rest local storage (backed by the Android Keystore) for the
 * few secrets this app holds: your own Anthropic API key, your own Google
 * OAuth client ID, and the Gmail OAuth token state. Nothing here ever
 * leaves the device except in direct API calls to Anthropic/Google that you
 * initiate (card lookup, Gmail scan).
 */
@Singleton
class SecurePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var anthropicApiKey: String?
        get() = prefs.getString(KEY_ANTHROPIC_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_ANTHROPIC_API_KEY, value).apply()

    var googleOAuthClientId: String?
        get() = prefs.getString(KEY_GOOGLE_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, value).apply()

    /** Serialized `net.openid.appauth.AuthState.jsonSerializeString()`, or null if never signed in. */
    var gmailAuthStateJson: String?
        get() = prefs.getString(KEY_GMAIL_AUTH_STATE, null)
        set(value) = prefs.edit().putString(KEY_GMAIL_AUTH_STATE, value).apply()

    var gmailAccountEmail: String?
        get() = prefs.getString(KEY_GMAIL_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_GMAIL_ACCOUNT_EMAIL, value).apply()

    fun clearGmailAuth() {
        prefs.edit()
            .remove(KEY_GMAIL_AUTH_STATE)
            .remove(KEY_GMAIL_ACCOUNT_EMAIL)
            .apply()
    }

    private companion object {
        const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        const val KEY_GOOGLE_CLIENT_ID = "google_oauth_client_id"
        const val KEY_GMAIL_AUTH_STATE = "gmail_auth_state"
        const val KEY_GMAIL_ACCOUNT_EMAIL = "gmail_account_email"
    }
}
