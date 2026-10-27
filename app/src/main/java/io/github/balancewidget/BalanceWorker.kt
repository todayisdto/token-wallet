package io.github.balancewidget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class BalanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val snapshot = BalanceRepository(applicationContext).refreshAll()
            BalanceWidgetProvider.updateAll(applicationContext, snapshot)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_NAME = "balance-refresh-periodic"
        private const val ONE_TIME_NAME = "balance-refresh-once"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BalanceWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodic(context: Context, minutes: Int) {
            enqueuePeriodic(context, minutes, ExistingPeriodicWorkPolicy.KEEP)
        }

        fun reschedulePeriodic(context: Context, minutes: Int) {
            enqueuePeriodic(context, minutes, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
        }

        private fun enqueuePeriodic(
            context: Context,
            minutes: Int,
            policy: ExistingPeriodicWorkPolicy
        ) {
            val interval = minutes.coerceAtLeast(15)
            val request = PeriodicWorkRequestBuilder<BalanceWorker>(
                interval.toLong(),
                TimeUnit.MINUTES
            )
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                policy,
                request
            )
        }

        fun enqueueIfStale(context: Context, snapshot: BalanceSnapshot) {
            val minutes = runCatching { SettingsStore(context).refreshMinutes }
                .getOrDefault(60)
            val age = System.currentTimeMillis() - snapshot.lastUpdated
            val due = snapshot.lastUpdated <= 0L || age >= minutes * 60_000L
            if (due) enqueue(context)
        }

        private fun networkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }
    }
}
