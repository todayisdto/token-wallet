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

object WidgetRenderer {

    fun build(
        context: Context,
        snapshot: BalanceSnapshot,
        refreshing: Boolean = false,
        themeMode: String = SettingsStore.THEME_SYSTEM
    ): RemoteViews {
        val palette = palette(context, themeMode)
        val systemTheme = themeMode == SettingsStore.THEME_SYSTEM
        val views = RemoteViews(context.packageName, R.layout.widget_balance)

        views.setTextViewText(
            R.id.widget_updated,
            if (refreshing) "刷新中…" else formatTime(context, snapshot.lastUpdated)
        )

        // In system mode the layout uses values-night resources, so Android can
        // recolor the widget without waiting for the app to be opened.
        if (!systemTheme) {
            runCatching {
                views.setInt(R.id.widget_root, "setBackgroundColor", palette.background)
            }
            views.setTextColor(R.id.widget_title, palette.text)
            views.setTextColor(R.id.widget_updated, palette.secondary)
            views.setTextColor(R.id.widget_settings, palette.secondary)
            views.setTextColor(R.id.widget_refresh, palette.text)
            views.setTextColor(R.id.widget_ds_label, palette.text)
            views.setTextColor(R.id.widget_ali_label, palette.text)
        }

        bindProvider(
            views = views,
            balance = snapshot.deepSeek,
            amountId = R.id.widget_ds_amount,
            detailId = R.id.widget_ds_detail,
            palette = palette,
            systemTheme = systemTheme
        )
        bindProvider(
            views = views,
            balance = snapshot.aliyun,
            amountId = R.id.widget_ali_amount,
            detailId = R.id.widget_ali_detail,
            palette = palette,
            systemTheme = systemTheme
        )

        views.setInt(R.id.widget_ds_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget_ali_dot, "setImageResource", R.drawable.dot)
        views.setInt(R.id.widget_ds_dot, "setColorFilter", palette.dsDot)
        views.setInt(R.id.widget_ali_dot, "setColorFilter", palette.aliDot)

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
        palette: Palette,
        systemTheme: Boolean
    ) {
        val hasError = !balance.error.isNullOrBlank()
        val amountText = formatAmount(balance)
        val detailText = if (hasError) balance.error else (balance.detail ?: "未配置")

        // Keep colors inside the text, not on the View. Otherwise an old red
        // color can survive after a successful refresh and only re-adding the
        // widget would clear it.
        val amountColor = when {
            hasError -> palette.error
            balance.amount.isNullOrBlank() -> palette.secondary
            !systemTheme -> palette.text
            else -> null
        }
        val detailColor = when {
            hasError -> palette.error
            !systemTheme -> palette.secondary
            else -> null
        }

        views.setTextViewText(amountId, colored(amountText, amountColor))
        views.setTextViewText(detailId, colored(detailText, detailColor))
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
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refresh(context: Context): PendingIntent {
        val intent = Intent(context, BalanceWidgetProvider::class.java)
            .setAction(BalanceWidgetProvider.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
