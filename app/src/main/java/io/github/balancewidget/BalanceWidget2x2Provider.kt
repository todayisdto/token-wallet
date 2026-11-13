package io.github.balancewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

class BalanceWidget2x2Provider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = SnapshotStore.load(context)
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(
                widgetId,
                WidgetRenderer2x2.build(context, snapshot)
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


    companion object {

        fun updateAll(context: Context, snapshot: BalanceSnapshot) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BalanceWidget2x2Provider::class.java)
            )
            ids.forEach { widgetId ->
                manager.updateAppWidget(
                    widgetId,
                    WidgetRenderer2x2.build(context, snapshot)
                )
            }
        }

    }
}
