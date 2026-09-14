package com.travelbenefits.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.travelbenefits.app.MainActivity
import com.travelbenefits.app.R
import com.travelbenefits.app.ui.navigation.NavigationRequests

/** One-tap "Which card?" widget: opens the purchase form. */
class BestCardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(NavigationRequests.EXTRA_OPEN, NavigationRequests.OPEN_PURCHASE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.widget_best_card).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
