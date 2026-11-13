package io.github.balancewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.work.WorkManager

class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = SnapshotStore.load(context)
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(
                widgetId,
                WidgetRenderer.build(context, snapshot)
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


    companion object {

        fun updateAll(context: Context, snapshot: BalanceSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java)
            )
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer.build(context, snapshot)
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

    }
}
