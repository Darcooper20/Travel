package com.travelbenefits.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.travelbenefits.app.domain.model.ProgramRegion
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
    /** Retail/dining/shopping rewards senders - noisier inboxes, so separately switchable. */
    val scanShopEmails: Boolean = true,
    /** Credit-card issuer statement emails, for rewards balances on wallet cards. */
    val scanCardEmails: Boolean = true,
    /**
     * Which markets' loyalty senders the Gmail scan searches. Every region is
     * on by default because people hold accounts in countries they don't live
     * in - which is the whole reason the catalog is not US-only. Turning
     * regions off is purely a speed control: each programme costs one Gmail
     * search per sync. An empty set searches no programme senders at all
     * (trip, card and generic-rewards sweeps still run).
     */
    val scanRegions: Set<ProgramRegion> = ProgramRegion.entries.toSet(),
    /** Anthropic API spend guard: at most this many emails are read per sync. */
    val maxEmailsPerSync: Int = 60,
    /** Daily local check for expiring points/credits/certificates, bonus deadlines, quarter activations and trip reminders. Needs no network. */
    val dailyRemindersEnabled: Boolean = true,
    /** Daily award-watch checks and weekly transfer-bonus research via web search (costs API calls). */
    val researchEnabled: Boolean = false,
    /** Quiet hours (local time) during which no notification is posted; deferred to the next run. 22-7 by default. */
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val quietHoursEnabled: Boolean = true,
) {
    fun isQuietNow(hour: Int = java.time.LocalTime.now().hour): Boolean {
        if (!quietHoursEnabled) return false
        return if (quietStartHour <= quietEndHour) hour in quietStartHour until quietEndHour else (hour >= quietStartHour || hour < quietEndHour)
    }
}

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

    private val _lastSyncHadFailures = MutableStateFlow(prefs.getBoolean(KEY_LAST_SYNC_FAILED, false))
    /** True when the last sync could not search, download or read part of the mailbox. */
    val lastSyncHadFailures: StateFlow<Boolean> = _lastSyncHadFailures.asStateFlow()

    private val _searchedPrograms = MutableStateFlow(prefs.getStringSet(KEY_SEARCHED_PROGRAMS, null).orEmpty().toSet())
    /**
     * Names of the programmes whose Gmail senders have already been searched
     * over the full lookback window. Anything NOT in here has never been
     * looked for - typically because the app version that added it arrived
     * after the last sync - so its search must use the full window rather
     * than the incremental one, or its mail (all of which predates the sync
     * watermark) would never be found at all.
     */
    val searchedPrograms: StateFlow<Set<String>> = _searchedPrograms.asStateFlow()

    /**
     * Called only when a sync read everything it found. While the per-sync
     * email cap is truncating results, some of what was searched for was
     * never actually read, so the back-fill stays pending and the next sync
     * searches the full window again.
     */
    fun recordSearchedPrograms(names: Set<String>) {
        prefs.edit().putStringSet(KEY_SEARCHED_PROGRAMS, names).apply()
        _searchedPrograms.value = names
    }

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    /** False until the user finishes (or skips) the guided setup; Settings can reset it to show the guide again. */
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, done).apply()
        _onboardingDone.value = done
    }

    // Keeps the flow honest when the file changes behind this singleton's back
    // (a cleared/restored prefs file, or an instrumented test resetting state).
    // Held in a field because SharedPreferences only keeps a weak reference.
    private val externalChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == null || key == KEY_ONBOARDED) _onboardingDone.value = p.getBoolean(KEY_ONBOARDED, false)
        if (key == null) {
            _syncSettings.value = load()
            _searchedPrograms.value = p.getStringSet(KEY_SEARCHED_PROGRAMS, null).orEmpty().toSet()
            _lastSyncAt.value = p.getLong(KEY_LAST_SYNC_AT, 0L)
            _lastSyncSummary.value = p.getString(KEY_LAST_SYNC_SUMMARY, null)
            _lastSyncHadFailures.value = p.getBoolean(KEY_LAST_SYNC_FAILED, false)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(externalChangeListener)
    }

    fun update(transform: (SyncSettings) -> SyncSettings) {
        val next = transform(_syncSettings.value)
        prefs.edit()
            .putBoolean(KEY_AUTO_SYNC, next.autoSyncEnabled)
            .putInt(KEY_INTERVAL_HOURS, next.syncIntervalHours)
            .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
            .putInt(KEY_LOOKBACK_DAYS, next.firstScanLookbackDays)
            .putBoolean(KEY_SCAN_LOYALTY, next.scanLoyaltyEmails)
            .putBoolean(KEY_SCAN_TRIPS, next.scanTripEmails)
            .putBoolean(KEY_SCAN_SHOPS, next.scanShopEmails)
            .putBoolean(KEY_SCAN_CARDS, next.scanCardEmails)
            .putStringSet(KEY_SCAN_REGIONS, next.scanRegions.map { it.name }.toSet())
            .putInt(KEY_MAX_EMAILS, next.maxEmailsPerSync)
            .putBoolean(KEY_DAILY_REMINDERS, next.dailyRemindersEnabled)
            .putBoolean(KEY_RESEARCH, next.researchEnabled)
            .putInt(KEY_QUIET_START, next.quietStartHour)
            .putInt(KEY_QUIET_END, next.quietEndHour)
            .putBoolean(KEY_QUIET_ENABLED, next.quietHoursEnabled)
            .apply()
        _syncSettings.value = next
    }

    fun recordSync(completedAt: Long, summary: String, hadFailures: Boolean = false) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_AT, completedAt)
            .putString(KEY_LAST_SYNC_SUMMARY, summary)
            .putBoolean(KEY_LAST_SYNC_FAILED, hadFailures)
            .apply()
        _lastSyncAt.value = completedAt
        _lastSyncSummary.value = summary
        _lastSyncHadFailures.value = hadFailures
    }

    /** Forget the sync watermark so the next scan re-reads the full lookback window (processed-email ledger still dedupes). */
    fun resetSyncWatermark() {
        // The searched-programme set goes too: it only means anything relative
        // to a watermark, and keeping it would stop the full re-scan being full.
        prefs.edit().remove(KEY_LAST_SYNC_AT).remove(KEY_LAST_SYNC_SUMMARY).remove(KEY_LAST_SYNC_FAILED)
            .remove(KEY_SEARCHED_PROGRAMS).apply()
        _searchedPrograms.value = emptySet()
        _lastSyncAt.value = 0L
        _lastSyncSummary.value = null
        _lastSyncHadFailures.value = false
    }

    /**
     * Region names are stored rather than ordinals so that reordering the enum
     * can't silently re-point a saved choice. A name that no longer exists is
     * dropped; an absent key means "never chosen", which takes the default.
     */
    private fun loadRegions(default: Set<ProgramRegion>): Set<ProgramRegion> {
        val stored = prefs.getStringSet(KEY_SCAN_REGIONS, null) ?: return default
        return stored.mapNotNull { name -> ProgramRegion.entries.firstOrNull { it.name == name } }.toSet()
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
            scanShopEmails = prefs.getBoolean(KEY_SCAN_SHOPS, defaults.scanShopEmails),
            scanCardEmails = prefs.getBoolean(KEY_SCAN_CARDS, defaults.scanCardEmails),
            scanRegions = loadRegions(defaults.scanRegions),
            maxEmailsPerSync = prefs.getInt(KEY_MAX_EMAILS, defaults.maxEmailsPerSync),
            dailyRemindersEnabled = prefs.getBoolean(KEY_DAILY_REMINDERS, defaults.dailyRemindersEnabled),
            researchEnabled = prefs.getBoolean(KEY_RESEARCH, defaults.researchEnabled),
            quietStartHour = prefs.getInt(KEY_QUIET_START, defaults.quietStartHour),
            quietEndHour = prefs.getInt(KEY_QUIET_END, defaults.quietEndHour),
            quietHoursEnabled = prefs.getBoolean(KEY_QUIET_ENABLED, defaults.quietHoursEnabled),
        )
    }

    private companion object {
        const val KEY_AUTO_SYNC = "auto_sync_enabled"
        const val KEY_INTERVAL_HOURS = "sync_interval_hours"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LOOKBACK_DAYS = "first_scan_lookback_days"
        const val KEY_SCAN_LOYALTY = "scan_loyalty_emails"
        const val KEY_SCAN_TRIPS = "scan_trip_emails"
        const val KEY_SCAN_SHOPS = "scan_shop_emails"
        const val KEY_SCAN_CARDS = "scan_card_emails"
        const val KEY_SCAN_REGIONS = "scan_regions"
        const val KEY_SEARCHED_PROGRAMS = "searched_programs"
        const val KEY_MAX_EMAILS = "max_emails_per_sync"
        const val KEY_DAILY_REMINDERS = "daily_reminders_enabled"
        const val KEY_RESEARCH = "research_enabled"
        const val KEY_QUIET_START = "quiet_start_hour"
        const val KEY_QUIET_END = "quiet_end_hour"
        const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
        const val KEY_LAST_SYNC_FAILED = "last_sync_had_failures"
        const val KEY_ONBOARDED = "has_seen_onboarding"
    }
}
