package io.github.balancewidget

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class BalanceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore.get(appContext)
    private val deepSeekClient = DeepSeekClient()
    private val aliyunClient = AliyunClient()

    suspend fun refreshAll(trigger: String): BalanceSnapshot = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val previous = SnapshotStore.load(appContext)

        // Background timers can fire in the same maintenance window (for example
        // the system widget update and the periodic job). Deduplicate automatic
        // triggers that arrive within a minute of the previous attempt; explicit
        // user actions (manual tap, widget tap, save) always refresh.
        val allowed = synchronized(gate) {
            val lastAttempt = maxOf(lastStartedAt, previous.lastUpdated)
            val tooSoon = startedAt - lastAttempt < MIN_REFRESH_GAP_MS
            if (tooSoon && trigger in AUTOMATIC_TRIGGERS) {
                false
            } else {
                lastStartedAt = startedAt
                true
            }
        }
        if (!allowed) return@withContext previous

        val now = startedAt
        val deepSeekKey = settings.deepSeekKey
        val aliyunId = settings.aliyunId
        val aliyunSecret = settings.aliyunSecret

        // Fetch both providers at the same time. They are independent, and a
        // slow endpoint must not delay the other one.
        val (deepSeek, aliyun) = coroutineScope {
            val ds = async { fetchDeepSeek(deepSeekKey, previous.deepSeek, now) }
            val ali = async { fetchAliyun(aliyunId, aliyunSecret, previous.aliyun, now) }
            ds.await() to ali.await()
        }

        val snapshot = BalanceSnapshot(
            deepSeek = deepSeek.balance,
            aliyun = aliyun.balance,
            lastUpdated = now,
            lastSuccessAt = if (deepSeek.success || aliyun.success) {
                now
            } else {
                previous.lastSuccessAt
            },
            refreshRequestedAt = 0L
        )
        SnapshotStore.save(appContext, snapshot)
        recordAttempt(trigger, deepSeek, aliyun, startedAt)
        snapshot
    }

    private fun recordAttempt(
        trigger: String,
        deepSeek: Attempt,
        aliyun: Attempt,
        startedAt: Long
    ) {
        val configured = listOf(deepSeek, aliyun).filter { it.configured }
        if (configured.isEmpty()) return

        val successes = configured.count { it.success }
        val result = when {
            successes == configured.size -> RefreshLog.RESULT_OK
            successes == 0 -> RefreshLog.RESULT_FAILED
            else -> RefreshLog.RESULT_PARTIAL
        }
        val detail = configured.joinToString("；") { attempt ->
            val name = if (attempt.balance.provider == Provider.DEEPSEEK) "DeepSeek" else "阿里云"
            if (attempt.success) {
                "$name ${formatAmount(attempt.balance)}"
            } else {
                "$name：${attempt.balance.error.orEmpty()}"
            }
        }
        RefreshLog.record(
            context = appContext,
            trigger = trigger,
            result = result,
            detail = detail,
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun fetchDeepSeek(
        key: String,
        previous: ProviderBalance,
        now: Long
    ): Attempt {
        if (key.isBlank()) {
            return Attempt(
                balance = previous.copy(error = "未配置 API Key", updatedAt = now),
                success = false,
                configured = false
            )
        }
        return try {
            Attempt(deepSeekClient.fetch(key), success = true, configured = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt(
                balance = previous.copy(error = e.toUserMessage(), updatedAt = now),
                success = false,
                configured = true
            )
        }
    }

    private fun fetchAliyun(
        id: String,
        secret: String,
        previous: ProviderBalance,
        now: Long
    ): Attempt {
        if (id.isBlank() || secret.isBlank()) {
            return Attempt(
                balance = previous.copy(error = "未配置 AccessKey", updatedAt = now),
                success = false,
                configured = false
            )
        }
        return try {
            Attempt(aliyunClient.fetch(id, secret), success = true, configured = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt(
                balance = previous.copy(error = e.toUserMessage(), updatedAt = now),
                success = false,
                configured = true
            )
        }
    }

    private data class Attempt(
        val balance: ProviderBalance,
        val success: Boolean,
        val configured: Boolean
    )

    companion object {
        private const val MIN_REFRESH_GAP_MS = 60_000L

        /** Process-wide gate; both the periodic and one-time workers live here. */
        private val gate = Any()
        private var lastStartedAt = 0L

        private val AUTOMATIC_TRIGGERS = setOf(
            RefreshLog.TRIGGER_PERIODIC,
            RefreshLog.TRIGGER_ALARM,
            RefreshLog.TRIGGER_SYSTEM,
            RefreshLog.TRIGGER_APP,
            RefreshLog.TRIGGER_BOOT,
            RefreshLog.TRIGGER_RETRY
        )
    }
}
