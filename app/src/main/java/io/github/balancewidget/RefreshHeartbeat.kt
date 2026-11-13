package io.github.balancewidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Secondary wake-up path next to WorkManager.
 *
 * WorkManager is the right place to run the network refresh, but its periodic
 * jobs are deferred into Doze maintenance windows, which can be hours apart on
 * a phone that just sits on a desk. An inexact "allow while idle" alarm is
 * allowed to wake the device, so it works as a heartbeat: it never does network
 * I/O itself, it only enqueues the worker and arms the next beat.
 */
object RefreshHeartbeat {

    const val ACTION_HEARTBEAT = "io.github.balancewidget.ACTION_HEARTBEAT"

    private const val REQUEST_CODE = 310
    private const val MINUTE_MS = 60_000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val minutes = runCatching { SettingsStore.get(appContext).refreshMinutes }
            .getOrDefault(60)
            .coerceAtLeast(15)
        val intervalMs = minutes * MINUTE_MS
        val now = System.currentTimeMillis()
        val lastAttempt = runCatching { SnapshotStore.load(appContext).lastUpdated }
            .getOrDefault(0L)

        // Anchor the next beat to the last attempt instead of "now": opening the
        // app or a 30-minute widget redraw must not push the alarm back.
        val target = if (lastAttempt > 0L && lastAttempt + intervalMs > now) {
            lastAttempt + intervalMs
        } else {
            now + intervalMs
        }

        runCatching {
            alarmManager(appContext).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                target,
                pendingIntent(appContext)
            )
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        runCatching { alarmManager(appContext).cancel(pendingIntent(appContext)) }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshAlarmReceiver::class.java)
            .setAction(ACTION_HEARTBEAT)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
