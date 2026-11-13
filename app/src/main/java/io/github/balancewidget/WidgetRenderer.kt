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

object WidgetRenderer {

    fun build(context: Context, snapshot: BalanceSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        val pending = isRefreshPending(snapshot)
        val stale = !pending && isSnapshotStale(context, snapshot)
        views.setTextViewText(
            R.id.widget_updated,
            if (pending) "刷新中…" else formatTime(context, snapshot.lastSuccessAt)
        )
        views.setViewVisibility(
            R.id.widget_stale_chip,
            if (stale) View.VISIBLE else View.GONE
        )
        views.setTextViewText(R.id.widget_stale_chip, "旧")

        // Surface and base text colours are left to the layout on purpose: their
        // resources have a light and a night variant, so the launcher can repaint
        // the widget on its own when the system switches theme. Painting them from
        // code would freeze the numbers into the RemoteViews, and a copy the
        // launcher keeps and replays would then show the old theme — the pale
        // amounts on a light card after coming back from 深色.
        val error = ContextCompat.getColor(context, R.color.error)
        val secondary = ContextCompat.getColor(context, R.color.widget_secondary)

        bindProvider(
            views = views,
            balance = snapshot.deepSeek,
            amountId = R.id.widget_ds_amount,
            detailId = R.id.widget_ds_detail,
            error = error,
            secondary = secondary
        )
        bindProvider(
            views = views,
            balance = snapshot.aliyun,
            amountId = R.id.widget_ali_amount,
            detailId = R.id.widget_ali_detail,
            error = error,
            secondary = secondary
        )

        views.setInt(R.id.widget_ds_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget_ali_dot, "setImageResource", R.drawable.dot)
        views.setInt(
            R.id.widget_ds_dot,
            "setColorFilter",
            ContextCompat.getColor(context, R.color.vendor_deepseek)
        )
        views.setInt(
            R.id.widget_ali_dot,
            "setColorFilter",
            ContextCompat.getColor(context, R.color.vendor_aliyun)
        )

        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_settings, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh(context))
        return views
    }

    private fun bindProvider(
        views: RemoteViews,
        balance: ProviderBalance,
        amountId: Int,
        detailId: Int,
        error: Int,
        secondary: Int
    ) {
        val hasError = !balance.error.isNullOrBlank()
        val amountText = formatAmount(balance)
        val detailText = if (hasError) balance.error else (balance.detail ?: "未配置")

        // Only the exception states carry a colour; the normal one is left to the
        // layout. The colour travels inside the text as a span, so a reused
        // RemoteViews cannot keep a red error tint after a successful refresh.
        val amountColor = when {
            hasError -> error
            balance.amount.isNullOrBlank() -> secondary
            else -> null
        }
        val detailColor = if (hasError) error else null

        views.setTextViewText(amountId, colored(amountText, amountColor))
        views.setTextViewText(detailId, colored(detailText, detailColor))
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
            100,
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
