package io.github.balancewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the refresh button inside both widget sizes.
 *
 * This used to live on the AppWidgetProvider, which has to stay exported so the
 * launcher can deliver APPWIDGET_UPDATE. That also made the refresh action
 * reachable by any other app on the device, which could then burn through the
 * stored keys and the user's traffic. The providers stay exported for the system
 * only; the refresh path lives here, declared exported="false", and is reached
 * exclusively through the PendingIntent we put into our own RemoteViews.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return

        val appContext = context.applicationContext
        val snapshot = SnapshotStore.markRefreshRequested(appContext)
        // Redraws both sizes; one shared "刷新中…" state is what the user expects
        // when either button was tapped.
        BalanceWidgetProvider.updateAll(appContext, snapshot)
        BalanceWorker.enqueue(appContext, RefreshLog.TRIGGER_WIDGET)
        BalanceWorker.scheduleRefreshStateReset(appContext)
    }

    companion object {
        const val ACTION_REFRESH = "io.github.balancewidget.ACTION_REFRESH"
    }
}
