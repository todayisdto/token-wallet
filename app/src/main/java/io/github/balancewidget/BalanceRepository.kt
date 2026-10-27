package io.github.balancewidget

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BalanceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val deepSeekClient = DeepSeekClient()
    private val aliyunClient = AliyunClient()

    suspend fun refreshAll(): BalanceSnapshot = withContext(Dispatchers.IO) {
        val previous = SnapshotStore.load(appContext)
        val now = System.currentTimeMillis()

        val deepSeek = if (settings.deepSeekKey.isBlank()) {
            previous.deepSeek.copy(error = "未配置 API Key", updatedAt = now)
        } else {
            try {
                deepSeekClient.fetch(settings.deepSeekKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                previous.deepSeek.copy(error = e.toUserMessage(), updatedAt = now)
            }
        }

        val aliyun = if (settings.aliyunId.isBlank() || settings.aliyunSecret.isBlank()) {
            previous.aliyun.copy(error = "未配置 AccessKey", updatedAt = now)
        } else {
            try {
                aliyunClient.fetch(settings.aliyunId, settings.aliyunSecret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                previous.aliyun.copy(error = e.toUserMessage(), updatedAt = now)
            }
        }

        val snapshot = BalanceSnapshot(
            deepSeek = deepSeek,
            aliyun = aliyun,
            lastUpdated = now
        )
        SnapshotStore.save(appContext, snapshot)
        snapshot
    }
}
