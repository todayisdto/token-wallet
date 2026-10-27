package io.github.balancewidget

import android.content.Context

object SnapshotStore {

    private const val PREFS = "balance_snapshot"

    fun load(context: Context): BalanceSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BalanceSnapshot(
            deepSeek = ProviderBalance(
                provider = Provider.DEEPSEEK,
                amount = prefs.getString("ds_amount", null),
                currency = prefs.getString("ds_currency", "CNY") ?: "CNY",
                detail = prefs.getString("ds_detail", null),
                error = prefs.getString("ds_error", null),
                updatedAt = prefs.getLong("ds_updated", 0L)
            ),
            aliyun = ProviderBalance(
                provider = Provider.ALIYUN,
                amount = prefs.getString("ali_amount", null),
                currency = prefs.getString("ali_currency", "CNY") ?: "CNY",
                detail = prefs.getString("ali_detail", null),
                error = prefs.getString("ali_error", null),
                updatedAt = prefs.getLong("ali_updated", 0L)
            ),
            lastUpdated = prefs.getLong("last_updated", 0L)
        )
    }

    fun save(context: Context, snapshot: BalanceSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("ds_amount", snapshot.deepSeek.amount)
            .putString("ds_currency", snapshot.deepSeek.currency)
            .putString("ds_detail", snapshot.deepSeek.detail)
            .putString("ds_error", snapshot.deepSeek.error)
            .putLong("ds_updated", snapshot.deepSeek.updatedAt)
            .putString("ali_amount", snapshot.aliyun.amount)
            .putString("ali_currency", snapshot.aliyun.currency)
            .putString("ali_detail", snapshot.aliyun.detail)
            .putString("ali_error", snapshot.aliyun.error)
            .putLong("ali_updated", snapshot.aliyun.updatedAt)
            .putLong("last_updated", snapshot.lastUpdated)
            .apply()
    }
}
