package com.travelbenefits.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.travelbenefits.app.data.local.CrashLog
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

    /**
     * Lazy on purpose. Field injection happens inside [Application.onCreate]'s
     * super call, so a directly injected SecurePrefs would build its keystore
     * master key and open EncryptedSharedPreferences there - before the crash
     * handler below is installed, and outside the guard around its use. A
     * failure was therefore both fatal and invisible. Deferred, it fails inside
     * the guard and gets recorded.
     */
    @Inject
    lateinit var securePrefs: dagger.Lazy<com.travelbenefits.app.data.local.SecurePrefs>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Before super, which is where Hilt injects the fields above. A crash
        // during injection would otherwise go unrecorded, and that is exactly
        // where the riskiest construction used to happen. Built directly rather
        // than injected for the same reason: it has to exist first.
        runCatching { CrashLog(this).install() }
        super.onCreate()

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
            val prefs = securePrefs.get()
            syncScheduler.applyPlaidSchedule(!prefs.plaidBackendUrl.isNullOrBlank() && !prefs.plaidAppToken.isNullOrBlank())
        }
    }
}
