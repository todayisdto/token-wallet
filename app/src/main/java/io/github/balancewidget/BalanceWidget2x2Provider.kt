package io.github.balancewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BalanceWidget2x2Provider : AppWidgetProvider() {

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
                WidgetRenderer2x2.build(context, snapshot, themeMode = themeMode)
            )
        }
        BalanceWidgetProvider.scheduleBackgroundWork(context)
        RefreshHeartbeat.schedule(context)
        BalanceWorker.enqueueIfStale(context, snapshot, RefreshLog.TRIGGER_SYSTEM)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        BalanceWidgetProvider.scheduleBackgroundWork(context)
        RefreshHeartbeat.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!BalanceWidgetProvider.hasAnyWidget(context)) {
            BalanceWidgetProvider.cancelBackgroundWork(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val snapshot = SnapshotStore.markRefreshRequested(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget2x2Provider::class.java)
            )
            val themeMode = themeMode(context)
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer2x2.build(context, snapshot, themeMode = themeMode)
                )
            }
            BalanceWorker.enqueue(context, RefreshLog.TRIGGER_WIDGET)
            BalanceWorker.scheduleRefreshStateReset(context)
        } else if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            updateAll(context, SnapshotStore.load(context))
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.balancewidget.ACTION_REFRESH_2X2"

        fun updateAll(context: Context, snapshot: BalanceSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget2x2Provider::class.java)
            )
            val themeMode = themeMode(context)
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer2x2.build(context, snapshot, themeMode = themeMode)
                )
            }
        }

        private fun themeMode(context: Context): String {
            return runCatching { SettingsStore.get(context).themeMode }
                .getOrDefault(SettingsStore.THEME_SYSTEM)
        }
    }
}
