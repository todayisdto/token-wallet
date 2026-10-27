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
        BalanceWorker.schedulePeriodic(context, refreshMinutes(context))
        BalanceWorker.enqueueIfStale(context, snapshot)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        BalanceWorker.schedulePeriodic(context, refreshMinutes(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget2x2Provider::class.java)
            )
            val snapshot = SnapshotStore.load(context)
            val themeMode = themeMode(context)
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer2x2.build(
                        context,
                        snapshot,
                        refreshing = true,
                        themeMode = themeMode
                    )
                )
            }
            BalanceWorker.enqueue(context)
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

        private fun refreshMinutes(context: Context): Int {
            return runCatching { SettingsStore(context).refreshMinutes }.getOrDefault(60)
        }

        private fun themeMode(context: Context): String {
            return runCatching { SettingsStore(context).themeMode }
                .getOrDefault(SettingsStore.THEME_SYSTEM)
        }
    }
}
