package com.travelbenefits.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.repository.PlaidRepository
import com.travelbenefits.app.domain.model.ActivityKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Twice-daily transaction sync through the Plaid backend. Silent unless something fails. */
@HiltWorker
class PlaidSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val plaidRepository: PlaidRepository,
    private val activityEventDao: ActivityEventDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!plaidRepository.isConfigured) return Result.success()
        return plaidRepository.syncAll().fold(
            onSuccess = { report ->
                if (report.errors.isNotEmpty()) {
                    activityEventDao.insert(ActivityEventEntity(kind = ActivityKind.SYNC_FAILED, program = null, title = "Bank sync had errors", detail = report.errors.joinToString("; "), occurredAt = System.currentTimeMillis(), isRead = false))
                }
                Result.success()
            },
            onFailure = { if (runAttemptCount < 2) Result.retry() else Result.failure() },
        )
    }

    companion object {
        const val UNIQUE_NAME = "plaid_sync"
    }
}
