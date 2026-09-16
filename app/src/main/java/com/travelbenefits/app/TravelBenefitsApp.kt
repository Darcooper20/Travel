package com.travelbenefits.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.travelbenefits.app.notifications.AppNotifier
import com.travelbenefits.app.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TravelBenefitsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var appNotifier: AppNotifier

    @Inject
    lateinit var securePrefs: com.travelbenefits.app.data.local.SecurePrefs

    @Inject
    lateinit var crashLog: com.travelbenefits.app.data.local.CrashLog

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // First, so anything below that dies is recorded rather than invisible.
        crashLog.install()

        // Everything here runs on every process start, including one Android
        // makes in the background. A failure in any of it used to take the
        // whole app down with no way to find out why, which reads to the user
        // as "keeps stopping". None of it is required for the UI to open, so
        // each part fails soft and is reported instead.
        runCatching { appNotifier.ensureChannels() }
        // Re-applies whatever sync schedule the user last chose (no-op when
        // background sync is off), so a reinstall/update never silently
        // stops monitoring.
        runCatching { syncScheduler.applyCurrentSettings() }
        runCatching {
            // Touching SecurePrefs builds a keystore-backed master key and
            // opens EncryptedSharedPreferences, which is real crypto on the
            // main thread and can throw if the keyset is damaged.
            syncScheduler.applyPlaidSchedule(!securePrefs.plaidBackendUrl.isNullOrBlank() && !securePrefs.plaidAppToken.isNullOrBlank())
        }
    }
}
