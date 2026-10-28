package io.github.balancewidget

import android.content.Context
import android.text.format.DateFormat
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLException

fun currencySymbol(currency: String): String {
    return when (currency.uppercase(Locale.ROOT)) {
        "CNY" -> "¥"
        "USD" -> "$"
        "JPY" -> "JP¥"
        else -> currency.uppercase(Locale.ROOT)
    }
}

fun formatAmount(balance: ProviderBalance): String {
    val amount = balance.amount
    if (amount.isNullOrBlank()) return "--"
    return "${currencySymbol(balance.currency)} $amount"
}

fun formatTime(context: Context, timestamp: Long): String {
    if (timestamp <= 0L) return "尚未更新"
    return DateFormat.getTimeFormat(context).format(Date(timestamp))
}

/**
 * True while a manual refresh is recent enough to show "刷新中…". The window is
 * short on purpose: if the worker never runs (no network, app force-stopped),
 * the widget must fall back to the normal timestamp instead of staying stuck.
 */
fun isRefreshPending(snapshot: BalanceSnapshot): Boolean {
    if (snapshot.refreshRequestedAt <= 0L) return false
    return System.currentTimeMillis() - snapshot.refreshRequestedAt < 2 * 60_000L
}

/**
 * True when the last successful update is older than two configured intervals.
 * Shown as a muted "旧" marker so old numbers cannot be mistaken for fresh ones.
 */
fun isSnapshotStale(context: Context, snapshot: BalanceSnapshot): Boolean {
    if (snapshot.lastSuccessAt <= 0L) return false
    val minutes = runCatching { SettingsStore.get(context).refreshMinutes }.getOrDefault(60)
    val limit = 2L * minutes.coerceAtLeast(15) * 60_000L
    return System.currentTimeMillis() - snapshot.lastSuccessAt > limit
}

fun Throwable.toUserMessage(): String {
    return when (this) {
        is ApiException -> userMessage
        is UnknownHostException -> "网络不可用，请检查网络"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is SSLException -> "TLS 连接失败，请检查网络"
        else -> message?.takeIf { it.isNotBlank() } ?: "请求失败"
    }
}
