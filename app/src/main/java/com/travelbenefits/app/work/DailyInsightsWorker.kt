package com.travelbenefits.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.travelbenefits.app.data.local.AppPrefs
import com.travelbenefits.app.data.local.dao.LoyaltyAccountDao
import com.travelbenefits.app.data.local.dao.NotifiedAlertDao
import com.travelbenefits.app.data.local.dao.TripDao
import com.travelbenefits.app.data.local.entity.NotifiedAlertEntity
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.LoyaltyInsights
import com.travelbenefits.app.notifications.AppNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Once a day, recomputes the alert list from local data (no network) and
 * pushes any alert whose key hasn't been notified before: staged points
 * expiry (90/60/30/7 days), unused credits near period end, certificates,
 * welcome-bonus deadlines, quarter activations and trip/check-in reminders.
 */
@HiltWorker
class DailyInsightsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appPrefs: AppPrefs,
    private val loyaltyAccountDao: LoyaltyAccountDao,
    private val tripDao: TripDao,
    private val walletRepository: WalletRepository,
    private val benefitsRepository: BenefitsRepository,
    private val notifiedAlertDao: NotifiedAlertDao,
    private val insights: LoyaltyInsights,
    private val appNotifier: AppNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = appPrefs.syncSettings.value
        if (!settings.dailyRemindersEnabled || !settings.notificationsEnabled) return Result.success()

        val accounts = loyaltyAccountDao.getAll().map { it.toDomain() }
        val trips = tripDao.getAll().map { it.toDomain() }
        val cards = walletRepository.getResolvedCards()
        val credits = benefitsRepository.getCreditStatuses()
        val benefits = benefitsRepository.getItems()

        val alerts = insights.alerts(accounts, trips, cards, credits, benefits)
        val keyed = alerts.filter { it.notifyKey != null }
        if (keyed.isEmpty()) return Result.success()
        val already = notifiedAlertDao.findExisting(keyed.map { it.notifyKey!! }).toSet()
        val fresh = keyed.filter { it.notifyKey !in already }
        if (fresh.isNotEmpty()) {
            appNotifier.notifyAlerts(fresh)
            val now = System.currentTimeMillis()
            notifiedAlertDao.insertAll(fresh.map { NotifiedAlertEntity(it.notifyKey!!, now) })
            notifiedAlertDao.prune(now - TimeUnit.DAYS.toMillis(400))
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily_insights"
    }
}
