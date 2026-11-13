package io.github.balancewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ThemeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            BalanceWidgetProvider.updateAll(
                context.applicationContext,
                SnapshotStore.load(context)
            )
        }
    }
}
