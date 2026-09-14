package com.travelbenefits.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
        val wm = WorkManager.getInstance(context)
        if (settings.dailyRemindersEnabled) {
            wm.enqueueUniquePeriodicWork(
                DailyInsightsWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyInsightsWorker>(24, TimeUnit.HOURS).build(),
            )
        } else {
            wm.cancelUniqueWork(DailyInsightsWorker.UNIQUE_NAME)
        }
        if (settings.researchEnabled) {
            wm.enqueueUniquePeriodicWork(
                ResearchWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ResearchWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(ResearchWorker.UNIQUE_NAME)
        }
    }

    /** Plaid transaction sync every 12 hours while a backend is configured. */
    fun applyPlaidSchedule(configured: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (configured) {
            wm.enqueueUniquePeriodicWork(
                PlaidSyncWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlaidSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(PlaidSyncWorker.UNIQUE_NAME)
        }
    }

    /** Runs the local reminder check right away (e.g. after the user changes a date) without waiting for the daily slot. */
    fun runRemindersNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DailyInsightsWorker.UNIQUE_NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DailyInsightsWorker>().build(),
        )
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
