package io.github.balancewidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

object WidgetRenderer2x2 {

    fun build(context: Context, snapshot: BalanceSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance_2x2)

        val pending = isRefreshPending(snapshot)
        val stale = !pending && isSnapshotStale(context, snapshot)
        views.setTextViewText(
            R.id.widget2_title,
            if (pending) "刷新中…" else "余额"
        )
        views.setViewVisibility(
            R.id.widget2_stale_chip,
            if (stale) View.VISIBLE else View.GONE
        )
        views.setTextViewText(R.id.widget2_stale_chip, "旧")

        // Same rule as the 4x2: surface and base text colours come from the
        // layout's light/night resources, so the launcher can repaint the widget
        // when the system switches theme without waiting for an update from the app.
        val error = ContextCompat.getColor(context, R.color.error)
        val secondary = ContextCompat.getColor(context, R.color.widget_secondary)

        bindAmount(views, snapshot.deepSeek, R.id.widget2_ds_amount, error, secondary)
        bindAmount(views, snapshot.aliyun, R.id.widget2_ali_amount, error, secondary)

        views.setInt(R.id.widget2_ds_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget2_ali_dot, "setImageResource", R.drawable.dot)
        views.setInt(
            R.id.widget2_ds_dot,
            "setColorFilter",
            ContextCompat.getColor(context, R.color.vendor_deepseek)
        )
        views.setInt(
            R.id.widget2_ali_dot,
            "setColorFilter",
            ContextCompat.getColor(context, R.color.vendor_aliyun)
        )

        views.setOnClickPendingIntent(R.id.widget2_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget2_refresh, refresh(context))
        return views
    }

    private fun bindAmount(
        views: RemoteViews,
        balance: ProviderBalance,
        amountId: Int,
        error: Int,
        secondary: Int
    ) {
        val hasError = !balance.error.isNullOrBlank()
        val color = when {
            hasError -> error
            balance.amount.isNullOrBlank() -> secondary
            else -> null
        }
        // Use a span rather than setTextColor so a red error color cannot stick
        // to the reused RemoteViews after a successful refresh.
        views.setTextViewText(
            amountId,
            colored(formatAmount(balance), color)
        )
    }

    private fun colored(text: CharSequence, color: Int?): CharSequence {
        if (color == null) return text
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refresh(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java)
            .setAction(WidgetRefreshReceiver.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
