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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appNotifier.ensureChannels()
        // Re-applies whatever sync schedule the user last chose (no-op when
        // background sync is off), so a reinstall/update never silently
        // stops monitoring.
        syncScheduler.applyCurrentSettings()
        syncScheduler.applyPlaidSchedule(!securePrefs.plaidBackendUrl.isNullOrBlank() && !securePrefs.plaidAppToken.isNullOrBlank())
    }
}
