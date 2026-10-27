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

fun Throwable.toUserMessage(): String {
    return when (this) {
        is ApiException -> userMessage
        is UnknownHostException -> "网络不可用，请检查网络"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is SSLException -> "TLS 连接失败，请检查网络"
        else -> message?.takeIf { it.isNotBlank() } ?: "请求失败"
    }
}
