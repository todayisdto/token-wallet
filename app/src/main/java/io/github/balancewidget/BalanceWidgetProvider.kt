package io.github.balancewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = SnapshotStore.load(context)
        val themeMode = themeMode(context)
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(
                widgetId,
                WidgetRenderer.build(context, snapshot, themeMode = themeMode)
            )
        }
        scheduleBackgroundWork(context)
        // Re-arms a lost alarm; the target itself is anchored to the last attempt.
        RefreshHeartbeat.schedule(context)
        BalanceWorker.enqueueIfStale(context, snapshot, RefreshLog.TRIGGER_SYSTEM)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleBackgroundWork(context)
        RefreshHeartbeat.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!hasAnyWidget(context)) cancelBackgroundWork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val snapshot = SnapshotStore.markRefreshRequested(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            )
            val themeMode = themeMode(context)
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer.build(context, snapshot, themeMode = themeMode)
                )
            }
            BalanceWorker.enqueue(context, RefreshLog.TRIGGER_WIDGET)
            BalanceWorker.scheduleRefreshStateReset(context)
        } else if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            updateAll(context, SnapshotStore.load(context))
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.balancewidget.ACTION_REFRESH"

        fun updateAll(context: Context, snapshot: BalanceSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            )
            val themeMode = themeMode(context)
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer.build(context, snapshot, themeMode = themeMode)
                )
            }
            BalanceWidget2x2Provider.updateAll(context, snapshot)
        }

        fun hasAnyWidget(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            ).isNotEmpty() || manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget2x2Provider::class.java)
            ).isNotEmpty()
        }

        fun scheduleBackgroundWork(context: Context) {
            BalanceWorker.schedulePeriodic(context, refreshMinutes(context))
        }

        fun cancelBackgroundWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(BalanceWorker.PERIODIC_NAME)
            RefreshHeartbeat.cancel(context)
        }

        private fun refreshMinutes(context: Context): Int {
            return runCatching { SettingsStore.get(context).refreshMinutes }.getOrDefault(60)
        }

        private fun themeMode(context: Context): String {
            return runCatching { SettingsStore.get(context).themeMode }
                .getOrDefault(SettingsStore.THEME_SYSTEM)
        }
    }
}
