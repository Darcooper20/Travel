package com.travelbenefits.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.repository.EmailMonitorRepository
import com.travelbenefits.app.notifications.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background sync. Reads new mail, records what it found, and
 * posts a notification only when something the user would care about
 * changed (new trip, balance/status change, expiring points).
 */
@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val emailMonitorRepository: EmailMonitorRepository,
    private val appPrefs: AppPrefs,
    private val appNotifier: AppNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = appPrefs.syncSettings.value
        if (!settings.autoSyncEnabled) return Result.success()

        return emailMonitorRepository.sync().fold(
            onSuccess = { report ->
                if (settings.notificationsEnabled && report.notableCount > 0) {
                    appNotifier.notifySyncReport(report)
                }
                Result.success()
            },
            onFailure = { error ->
                // Missing key / not signed in are configuration problems - retrying won't help.
                if (error is IllegalStateException) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val UNIQUE_NAME = "email_sync_periodic"
        const val ONE_OFF_NAME = "email_sync_now"
    }
}
