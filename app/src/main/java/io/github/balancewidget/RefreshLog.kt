package io.github.balancewidget

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small ring buffer of refresh attempts, rendered in the settings page.
 *
 * It deliberately lives in plain SharedPreferences: it holds no secrets and is
 * written from workers as well as from the UI thread.
 */
object RefreshLog {

    const val TRIGGER_PERIODIC = "定时任务"
    const val TRIGGER_ALARM = "闹钟心跳"
    const val TRIGGER_SYSTEM = "系统刷新"
    const val TRIGGER_WIDGET = "小组件刷新"
    const val TRIGGER_APP = "打开 App"
    const val TRIGGER_MANUAL = "手动刷新"
    const val TRIGGER_SAVE = "保存 Key"
    const val TRIGGER_RETRY = "失败重试"
    const val TRIGGER_BOOT = "开机/更新"

    const val RESULT_OK = "成功"
    const val RESULT_PARTIAL = "部分失败"
    const val RESULT_FAILED = "失败"

    private const val TAG = "RefreshLog"
    private const val PREFS = "refresh_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 20

    data class Entry(
        val at: Long,
        val trigger: String,
        val result: String,
        val detail: String,
        val durationMs: Long
    )

    fun record(
        context: Context,
        trigger: String,
        result: String,
        detail: String,
        durationMs: Long
    ) {
        runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
            val kept = JSONArray()
            kept.put(
                JSONObject()
                    .put("at", System.currentTimeMillis())
                    .put("trigger", trigger)
                    .put("result", result)
                    .put("detail", detail)
                    .put("ms", durationMs)
            )
            var index = 0
            while (index < existing.length() && kept.length() < MAX_ENTRIES) {
                kept.put(existing.get(index))
                index++
            }
            prefs.edit().putString(KEY_ENTRIES, kept.toString()).apply()
        }.onFailure { Log.w(TAG, "Unable to write the refresh log", it) }
    }

    fun load(context: Context): List<Entry> {
        return runCatching {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val array = JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                Entry(
                    at = item.optLong("at", 0L),
                    trigger = item.optString("trigger", ""),
                    result = item.optString("result", ""),
                    detail = item.optString("detail", ""),
                    durationMs = item.optLong("ms", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun format(entry: Entry): String {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.at))
        val seconds = String.format(Locale.US, "%.1fs", entry.durationMs / 1000.0)
        val head = "$time  ${entry.trigger} · ${entry.result} · $seconds"
        return if (entry.detail.isBlank()) head else "$head\n    ${entry.detail}"
    }
}
