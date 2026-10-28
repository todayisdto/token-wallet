package io.github.balancewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the [RefreshHeartbeat] alarm and the boot/update broadcasts. Both
 * only schedule work, so this receiver stays fast and safe on the main thread.
 */
class RefreshAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RefreshHeartbeat.ACTION_HEARTBEAT -> {
                if (!BalanceWidgetProvider.hasAnyWidget(context)) {
                    BalanceWidgetProvider.cancelBackgroundWork(context)
                    return
                }
                BalanceWorker.enqueueIfStale(
                    context,
                    SnapshotStore.load(context),
                    RefreshLog.TRIGGER_ALARM,
                    expedited = true
                )
                RefreshHeartbeat.schedule(context)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!BalanceWidgetProvider.hasAnyWidget(context)) return
                BalanceWidgetProvider.scheduleBackgroundWork(context)
                RefreshHeartbeat.schedule(context)
                BalanceWorker.enqueueIfStale(
                    context,
                    SnapshotStore.load(context),
                    RefreshLog.TRIGGER_BOOT,
                    expedited = true
                )
            }
        }
    }
}
