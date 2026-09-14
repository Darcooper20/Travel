package com.travelbenefits.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.travelbenefits.app.data.local.AppPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the WorkManager schedule for [EmailSyncWorker], derived from [AppPrefs.syncSettings]. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPrefs,
) {
    fun applyCurrentSettings() {
        val settings = appPrefs.syncSettings.value
        if (settings.autoSyncEnabled) schedule(settings.syncIntervalHours) else cancel()
    }

    private fun schedule(intervalHours: Int) {
        val hours = intervalHours.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS).toLong()
        val request = PeriodicWorkRequestBuilder<EmailSyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EmailSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(EmailSyncWorker.UNIQUE_NAME)
    }

    companion object {
        const val MIN_INTERVAL_HOURS = 1
        const val MAX_INTERVAL_HOURS = 168
        val INTERVAL_CHOICES = listOf(3, 6, 12, 24, 48)
    }
}
