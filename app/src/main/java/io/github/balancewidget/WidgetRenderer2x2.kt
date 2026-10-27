package io.github.balancewidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

object WidgetRenderer2x2 {

    fun build(
        context: Context,
        snapshot: BalanceSnapshot,
        refreshing: Boolean = false,
        themeMode: String = SettingsStore.THEME_SYSTEM
    ): RemoteViews {
        val palette = palette(context, themeMode)
        val systemTheme = themeMode == SettingsStore.THEME_SYSTEM
        val views = RemoteViews(context.packageName, R.layout.widget_balance_2x2)

        views.setTextViewText(
            R.id.widget2_title,
            if (refreshing) "刷新中…" else "余额"
        )

        if (!systemTheme) {
            runCatching {
                views.setInt(R.id.widget2_root, "setBackgroundColor", palette.background)
            }
            views.setTextColor(R.id.widget2_title, palette.text)
            views.setTextColor(R.id.widget2_refresh, palette.text)
            views.setTextColor(R.id.widget2_ds_label, palette.secondary)
            views.setTextColor(R.id.widget2_ali_label, palette.secondary)
        }

        bindAmount(views, snapshot.deepSeek, R.id.widget2_ds_amount, palette, systemTheme)
        bindAmount(views, snapshot.aliyun, R.id.widget2_ali_amount, palette, systemTheme)

        views.setInt(R.id.widget2_ds_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget2_ali_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget2_ds_dot, "setColorFilter", palette.dsDot)
        views.setInt(R.id.widget2_ali_dot, "setColorFilter", palette.aliDot)

        views.setOnClickPendingIntent(R.id.widget2_root, openApp(context))
        views.setOnClickPendingIntent(R.id.widget2_refresh, refresh(context))
        return views
    }

    private fun bindAmount(
        views: RemoteViews,
        balance: ProviderBalance,
        amountId: Int,
        palette: Palette,
        systemTheme: Boolean
    ) {
        val hasError = !balance.error.isNullOrBlank()
        val color = when {
            hasError -> palette.error
            balance.amount.isNullOrBlank() -> palette.secondary
            !systemTheme -> palette.text
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

    private fun palette(context: Context, themeMode: String): Palette {
        val dark = when (themeMode) {
            SettingsStore.THEME_LIGHT -> false
            SettingsStore.THEME_DARK -> true
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }

        return if (dark) {
            Palette(
                background = ContextCompat.getColor(context, R.color.widget_bg_dark),
                text = ContextCompat.getColor(context, R.color.widget_text_dark),
                secondary = ContextCompat.getColor(context, R.color.widget_secondary_dark),
                error = ContextCompat.getColor(context, R.color.error_dark),
                dsDot = ContextCompat.getColor(context, R.color.vendor_deepseek_dark),
                aliDot = ContextCompat.getColor(context, R.color.vendor_aliyun_dark)
            )
        } else {
            Palette(
                background = ContextCompat.getColor(context, R.color.widget_bg_light),
                text = ContextCompat.getColor(context, R.color.widget_text_light),
                secondary = ContextCompat.getColor(context, R.color.widget_secondary_light),
                error = ContextCompat.getColor(context, R.color.error_light),
                dsDot = ContextCompat.getColor(context, R.color.vendor_deepseek_light),
                aliDot = ContextCompat.getColor(context, R.color.vendor_aliyun_light)
            )
        }
    }

    private data class Palette(
        val background: Int,
        val text: Int,
        val secondary: Int,
        val error: Int,
        val dsDot: Int,
        val aliDot: Int
    )

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
        val intent = Intent(context, BalanceWidget2x2Provider::class.java)
            .setAction(BalanceWidget2x2Provider.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
