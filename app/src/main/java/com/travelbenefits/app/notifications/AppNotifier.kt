package com.travelbenefits.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.travelbenefits.app.MainActivity
import com.travelbenefits.app.data.repository.SyncReport
import com.travelbenefits.app.domain.model.Alert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SYNC, "Loyalty & trip updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New trips, balance and status changes, and expiring points found by the email sync."
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Expiring points and certificates, unused credits, welcome-bonus deadlines, quarterly activations, check-in reminders, award watches."
                },
            )
        }
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notifySyncReport(report: SyncReport) {
        if (!hasPermission()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = report.highlights.take(5)
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(report.summary())
            .setContentText(lines.firstOrNull() ?: "Open the app for details.")
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SYNC, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post - nothing to do.
        }
    }

    /** One notification summarising the day's fresh reminders. */
    fun notifyAlerts(alerts: List<Alert>) {
        if (alerts.isEmpty()) return
        val title = if (alerts.size == 1) alerts.first().title else "${alerts.size} loyalty reminders"
        notifySimple(title, alerts.joinToString("\n") { it.title }, CHANNEL_REMINDERS, NOTIFICATION_ID_REMINDERS)
    }

    fun notifySimple(title: String, body: String, channel: String = CHANNEL_REMINDERS, id: Int = NOTIFICATION_ID_RESEARCH) {
        if (!hasPermission()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = body.split('\n').filter { it.isNotBlank() }.take(6)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: body)
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post - nothing to do.
        }
    }

    private companion object {
        const val CHANNEL_SYNC = "sync_updates"
        const val CHANNEL_REMINDERS = "reminders"
        const val NOTIFICATION_ID_SYNC = 1001
        const val NOTIFICATION_ID_REMINDERS = 1002
        const val NOTIFICATION_ID_RESEARCH = 1003
    }
}
