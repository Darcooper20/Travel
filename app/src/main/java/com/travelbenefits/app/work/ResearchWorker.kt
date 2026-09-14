package com.travelbenefits.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.entity.ActivityEventEntity
import com.travelbenefits.app.data.repository.ResearchRepository
import com.travelbenefits.app.domain.model.ActivityKind
import com.travelbenefits.app.notifications.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily web-search research (opt-in, costs API calls): checks active award
 * watches and, weekly, refreshes the transfer-bonus list. Notifies when a
 * watch newly finds availability or a new bonus appears.
 */
@HiltWorker
class ResearchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appPrefs: AppPrefs,
    private val researchRepository: ResearchRepository,
    private val activityEventDao: ActivityEventDao,
    private val appNotifier: AppNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = appPrefs.syncSettings.value
        if (!settings.researchEnabled) return Result.success()
        val now = System.currentTimeMillis()

        researchRepository.checkWatches().onSuccess { results ->
            val found = results.filter { it.newlyFound }
            found.forEach { r ->
                activityEventDao.insert(ActivityEventEntity(kind = ActivityKind.INFO, program = r.watch.program, title = "Award watch: ${r.watch.title} looks available", detail = r.summary, occurredAt = now, isRead = false))
            }
            if (found.isNotEmpty() && settings.notificationsEnabled) {
                appNotifier.notifySimple("Award availability found", found.joinToString("\n") { it.watch.title })
            }
        }

        if (researchRepository.bonusesAreStale()) {
            val before = researchRepository.getBonuses().map { "${it.from}->${it.to}:${it.bonusPercent}" }.toSet()
            researchRepository.refreshTransferBonuses().onSuccess { bonuses ->
                val new = bonuses.filter { "${it.from}->${it.to}:${it.bonusPercent}" !in before }
                new.forEach { b ->
                    activityEventDao.insert(ActivityEventEntity(kind = ActivityKind.INFO, program = b.to, title = "Transfer bonus: ${b.from.displayName} → ${b.to.displayName} +${b.bonusPercent}%", detail = b.note, occurredAt = now, isRead = false))
                }
                if (new.isNotEmpty() && settings.notificationsEnabled) {
                    appNotifier.notifySimple("New transfer bonus", new.joinToString("\n") { "${it.from.displayName} → ${it.to.displayName} +${it.bonusPercent}%" })
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily_research"
    }
}
