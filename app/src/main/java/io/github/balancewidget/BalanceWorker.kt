package io.github.balancewidget

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class BalanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Render-only pass scheduled after a manual tap: clears "刷新中…" even
        // when the network request never got a chance to run.
        if (inputData.getBoolean(KEY_RENDER_ONLY, false)) {
            BalanceWidgetProvider.updateAll(
                applicationContext,
                SnapshotStore.load(applicationContext)
            )
            return Result.success()
        }

        // All widgets were removed while a retry was still queued.
        if (!BalanceWidgetProvider.hasAnyWidget(applicationContext)) {
            BalanceWidgetProvider.cancelBackgroundWork(applicationContext)
            return Result.success()
        }

        val trigger = inputData.getString(KEY_TRIGGER) ?: RefreshLog.TRIGGER_PERIODIC
        val isRetry = inputData.getBoolean(KEY_IS_RETRY, false)

        return try {
            val snapshot = BalanceRepository(applicationContext).refreshAll(trigger)
            BalanceWidgetProvider.updateAll(applicationContext, snapshot)
            // Keep the heartbeat anchored to the most recent attempt.
            RefreshHeartbeat.schedule(applicationContext)
            scheduleRetryIfNeeded(snapshot, isRetry)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * One short retry when nothing succeeded (typical case: screen off, network
     * blocked). The retry is marked so a second failure cannot loop.
     */
    private fun scheduleRetryIfNeeded(snapshot: BalanceSnapshot, isRetry: Boolean) {
        if (isRetry) return
        val configured = runCatching { SettingsStore.get(applicationContext).hasAnyKey }
            .getOrDefault(false)
        if (!configured) return
        val anySuccess = snapshot.deepSeek.error == null || snapshot.aliyun.error == null
        if (anySuccess) return
        enqueue(
            context = applicationContext,
            trigger = RefreshLog.TRIGGER_RETRY,
            expedited = false,
            delayMinutes = 5,
            isRetry = true
        )
    }

    companion object {
        const val PERIODIC_NAME = "balance-refresh-periodic"
        private const val ONE_TIME_NAME = "balance-refresh-once"
        private const val RETRY_NAME = "balance-refresh-retry"
        private const val RENDER_RESET_NAME = "balance-refresh-render-reset"
        private const val KEY_RENDER_ONLY = "render_only"
        private const val KEY_TRIGGER = "trigger"
        private const val KEY_IS_RETRY = "is_retry"

        fun enqueue(
            context: Context,
            trigger: String,
            expedited: Boolean = true,
            delayMinutes: Long = 0L,
            isRetry: Boolean = false
        ) {
            val workManager = WorkManager.getInstance(context)
            if (!isRetry) workManager.cancelUniqueWork(RETRY_NAME)

            val builder = OneTimeWorkRequestBuilder<BalanceWorker>()
                .setConstraints(networkConstraints())
                .setInputData(
                    workDataOf(KEY_TRIGGER to trigger, KEY_IS_RETRY to isRetry)
                )
            if (delayMinutes > 0L) {
                builder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            }
            if (expedited && delayMinutes <= 0L) {
                builder.setExpeditedIfSupported()
            }
            workManager.enqueueUniqueWork(
                if (isRetry) RETRY_NAME else ONE_TIME_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.build()
            )
        }

        fun scheduleRefreshStateReset(context: Context) {
            val request = OneTimeWorkRequestBuilder<BalanceWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_RENDER_ONLY to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                RENDER_RESET_NAME,
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
            // A narrow flex window near the end of the interval makes the run
            // land close to "one interval later" instead of anywhere inside it.
            val flex = (interval / 4).coerceIn(5, 15).toLong()
            val request = PeriodicWorkRequest.Builder(
                BalanceWorker::class.java,
                interval.toLong(),
                TimeUnit.MINUTES,
                flex,
                TimeUnit.MINUTES
            )
                .setConstraints(networkConstraints())
                .setInputData(workDataOf(KEY_TRIGGER to RefreshLog.TRIGGER_PERIODIC))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                policy,
                request
            )
        }

        fun enqueueIfStale(
            context: Context,
            snapshot: BalanceSnapshot,
            trigger: String,
            expedited: Boolean = true
        ) {
            val minutes = runCatching { SettingsStore.get(context).refreshMinutes }
                .getOrDefault(60)
            val age = System.currentTimeMillis() - snapshot.lastUpdated
            val due = snapshot.lastUpdated <= 0L || age >= minutes * 60_000L
            if (due) enqueue(context, trigger, expedited)
        }

        private fun OneTimeWorkRequest.Builder.setExpeditedIfSupported():
            OneTimeWorkRequest.Builder = apply {
            // Android 12+ runs expedited work as an expedited job. Older
            // versions would have to start a foreground service for the same
            // result, so they keep the normal (still constrained) path.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
        }

        private fun networkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        }
    }
}
