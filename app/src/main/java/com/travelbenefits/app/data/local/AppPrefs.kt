package com.travelbenefits.app.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** User-facing monitor settings. Nothing sensitive lives here (secrets go in [SecurePrefs]). */
data class SyncSettings(
    /** Background email sync via WorkManager. Off until the user turns it on after connecting Gmail. */
    val autoSyncEnabled: Boolean = false,
    val syncIntervalHours: Int = 12,
    val notificationsEnabled: Boolean = true,
    /** How far back the very first scan looks; later scans only fetch mail newer than the last run. */
    val firstScanLookbackDays: Int = 365,
    val scanLoyaltyEmails: Boolean = true,
    val scanTripEmails: Boolean = true,
    /** Anthropic API spend guard: at most this many emails are read per sync. */
    val maxEmailsPerSync: Int = 60,
)

@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _syncSettings = MutableStateFlow(load())
    val syncSettings: StateFlow<SyncSettings> = _syncSettings.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC_AT, 0L))
    /** Epoch millis of the last completed sync (manual or background), 0 if never. */
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _lastSyncSummary = MutableStateFlow(prefs.getString(KEY_LAST_SYNC_SUMMARY, null))
    val lastSyncSummary: StateFlow<String?> = _lastSyncSummary.asStateFlow()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    fun update(transform: (SyncSettings) -> SyncSettings) {
        val next = transform(_syncSettings.value)
        prefs.edit()
            .putBoolean(KEY_AUTO_SYNC, next.autoSyncEnabled)
            .putInt(KEY_INTERVAL_HOURS, next.syncIntervalHours)
            .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
            .putInt(KEY_LOOKBACK_DAYS, next.firstScanLookbackDays)
            .putBoolean(KEY_SCAN_LOYALTY, next.scanLoyaltyEmails)
            .putBoolean(KEY_SCAN_TRIPS, next.scanTripEmails)
            .putInt(KEY_MAX_EMAILS, next.maxEmailsPerSync)
            .apply()
        _syncSettings.value = next
    }

    fun recordSync(completedAt: Long, summary: String) {
        prefs.edit().putLong(KEY_LAST_SYNC_AT, completedAt).putString(KEY_LAST_SYNC_SUMMARY, summary).apply()
        _lastSyncAt.value = completedAt
        _lastSyncSummary.value = summary
    }

    /** Forget the sync watermark so the next scan re-reads the full lookback window (processed-email ledger still dedupes). */
    fun resetSyncWatermark() {
        prefs.edit().remove(KEY_LAST_SYNC_AT).remove(KEY_LAST_SYNC_SUMMARY).apply()
        _lastSyncAt.value = 0L
        _lastSyncSummary.value = null
    }

    private fun load(): SyncSettings {
        val defaults = SyncSettings()
        return SyncSettings(
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, defaults.autoSyncEnabled),
            syncIntervalHours = prefs.getInt(KEY_INTERVAL_HOURS, defaults.syncIntervalHours),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, defaults.notificationsEnabled),
            firstScanLookbackDays = prefs.getInt(KEY_LOOKBACK_DAYS, defaults.firstScanLookbackDays),
            scanLoyaltyEmails = prefs.getBoolean(KEY_SCAN_LOYALTY, defaults.scanLoyaltyEmails),
            scanTripEmails = prefs.getBoolean(KEY_SCAN_TRIPS, defaults.scanTripEmails),
            maxEmailsPerSync = prefs.getInt(KEY_MAX_EMAILS, defaults.maxEmailsPerSync),
        )
    }

    private companion object {
        const val KEY_AUTO_SYNC = "auto_sync_enabled"
        const val KEY_INTERVAL_HOURS = "sync_interval_hours"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LOOKBACK_DAYS = "first_scan_lookback_days"
        const val KEY_SCAN_LOYALTY = "scan_loyalty_emails"
        const val KEY_SCAN_TRIPS = "scan_trip_emails"
        const val KEY_MAX_EMAILS = "max_emails_per_sync"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
        const val KEY_ONBOARDED = "has_seen_onboarding"
    }
}
