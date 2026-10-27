package io.github.balancewidget

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var repository: BalanceRepository

    private lateinit var dsHeader: View
    private lateinit var dsBody: View
    private lateinit var dsChevron: TextView
    private lateinit var dsKey: TextInputEditText
    private lateinit var dsSave: MaterialButton
    private lateinit var dsStatus: TextView

    private lateinit var aliHeader: View
    private lateinit var aliBody: View
    private lateinit var aliChevron: TextView
    private lateinit var aliId: TextInputEditText
    private lateinit var aliSecret: TextInputEditText
    private lateinit var aliSave: MaterialButton
    private lateinit var aliStatus: TextView

    private lateinit var pageConfig: View
    private lateinit var pageRefresh: View
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var intervalSpinner: Spinner
    private lateinit var themeGroup: RadioGroup
    private lateinit var refreshNow: MaterialButton
    private lateinit var batteryRow: View
    private lateinit var hideRecentsRow: View
    private lateinit var hideRecentsSwitch: SwitchMaterial
    private lateinit var lastRefresh: TextView

    private var selectedTab = R.id.nav_config
    private var recreatingForTheme = false

    private val intervals = listOf(
        30 to "30 分钟",
        60 to "1 小时",
        180 to "3 小时",
        360 to "6 小时"
    )
    private var ignoreSpinnerChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settings = SettingsStore(this)
        applyTheme(settings.themeMode)
        setContentView(R.layout.activity_main)
        applySystemBars()

        repository = BalanceRepository(this)

        bindViews()
        prefill()
        setupListeners()
        setupTabs(savedInstanceState)
        renderSnapshot(SnapshotStore.load(this))

        BalanceWorker.schedulePeriodic(this, settings.refreshMinutes)
        if (settings.hasAnyKey) {
            BalanceWorker.enqueue(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val snapshot = SnapshotStore.load(this)
        renderSnapshot(snapshot)
        BalanceWidgetProvider.updateAll(this, snapshot)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (settings.themeMode == SettingsStore.THEME_SYSTEM) {
            BalanceWidgetProvider.updateAll(this, SnapshotStore.load(this))
            recreatingForTheme = true
            recreate()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !recreatingForTheme && settings.hideRecents) {
            finishAndRemoveTask()
        }
    }

    private fun bindViews() {
        pageConfig = findViewById(R.id.page_config)
        pageRefresh = findViewById(R.id.page_refresh)
        bottomNav = findViewById(R.id.bottom_nav)

        dsHeader = findViewById(R.id.ds_header)
        dsBody = findViewById(R.id.ds_body)
        dsChevron = findViewById(R.id.ds_chevron)
        dsKey = findViewById(R.id.ds_key)
        dsSave = findViewById(R.id.ds_save)
        dsStatus = findViewById(R.id.ds_status)
        aliHeader = findViewById(R.id.ali_header)
        aliBody = findViewById(R.id.ali_body)
        aliChevron = findViewById(R.id.ali_chevron)
        aliId = findViewById(R.id.ali_id)
        aliSecret = findViewById(R.id.ali_secret)
        aliSave = findViewById(R.id.ali_save)
        aliStatus = findViewById(R.id.ali_status)
        intervalSpinner = findViewById(R.id.interval_spinner)
        themeGroup = findViewById(R.id.theme_group)
        refreshNow = findViewById(R.id.refresh_now)
        batteryRow = findViewById(R.id.battery_row)
        hideRecentsRow = findViewById(R.id.hide_recents_row)
        hideRecentsSwitch = findViewById(R.id.hide_recents_switch)
        lastRefresh = findViewById(R.id.last_refresh)
    }

    private fun prefill() {
        dsKey.setText(settings.deepSeekKey)
        aliId.setText(settings.aliyunId)
        aliSecret.setText(settings.aliyunSecret)

        val selected = intervals.indexOfFirst { it.first == settings.refreshMinutes }
            .coerceAtLeast(0)
        ignoreSpinnerChange = true
        intervalSpinner.setSelection(selected, false)
        ignoreSpinnerChange = false

        when (settings.themeMode) {
            SettingsStore.THEME_LIGHT -> themeGroup.check(R.id.theme_light)
            SettingsStore.THEME_DARK -> themeGroup.check(R.id.theme_dark)
            else -> themeGroup.check(R.id.theme_system)
        }
        hideRecentsSwitch.isChecked = settings.hideRecents

        setExpanded(dsBody, dsChevron, settings.deepSeekKey.isBlank())
        setExpanded(
            aliBody,
            aliChevron,
            settings.aliyunId.isBlank() || settings.aliyunSecret.isBlank()
        )
    }

    private fun setupListeners() {
        dsSave.setOnClickListener { saveDeepSeek() }
        aliSave.setOnClickListener { saveAliyun() }
        refreshNow.setOnClickListener { refreshAllNow() }
        batteryRow.setOnClickListener { openBatterySettings() }
        hideRecentsSwitch.setOnCheckedChangeListener { _, checked ->
            settings.hideRecents = checked
            toast(
                if (checked) "已开启：离开后不在最近任务显示"
                else "已关闭：保留最近任务卡片"
            )
        }
        hideRecentsRow.setOnClickListener { hideRecentsSwitch.toggle() }

        dsHeader.setOnClickListener { toggle(dsBody, dsChevron) }
        aliHeader.setOnClickListener { toggle(aliBody, aliChevron) }

        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.theme_light -> SettingsStore.THEME_LIGHT
                R.id.theme_dark -> SettingsStore.THEME_DARK
                else -> SettingsStore.THEME_SYSTEM
            }
            if (settings.themeMode != mode) {
                settings.themeMode = mode
                BalanceWidgetProvider.updateAll(this, SnapshotStore.load(this))
                applyTheme(mode)
            }
        }

        intervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (ignoreSpinnerChange) return
                val minutes = intervals[position].first
                settings.refreshMinutes = minutes
                BalanceWorker.reschedulePeriodic(this@MainActivity, minutes)
                toast("刷新频率已改为 ${intervals[position].second}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun saveDeepSeek() {
        val key = dsKey.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) {
            setStatus(dsStatus, "请填写 API Key", false)
            return
        }

        dsSave.isEnabled = false
        setStatus(dsStatus, "正在验证…", null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(DeepSeekClient().fetch(key))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            dsSave.isEnabled = true
            result.onSuccess { balance ->
                settings.deepSeekKey = key
                val snapshot = SnapshotStore.load(this@MainActivity).copy(
                    deepSeek = balance,
                    lastUpdated = System.currentTimeMillis()
                )
                SnapshotStore.save(this@MainActivity, snapshot)
                renderSnapshot(snapshot)
                BalanceWidgetProvider.updateAll(this@MainActivity, snapshot)
                setStatus(dsStatus, "已连接 · ${formatAmount(balance)}", true)
                setExpanded(dsBody, dsChevron, false)
                BalanceWorker.enqueue(this@MainActivity)
            }.onFailure { error ->
                setStatus(dsStatus, error.toUserMessage(), false)
            }
        }
    }

    private fun saveAliyun() {
        val id = aliId.text?.toString()?.trim().orEmpty()
        val secret = aliSecret.text?.toString()?.trim().orEmpty()
        if (id.isBlank() || secret.isBlank()) {
            setStatus(aliStatus, "请填写 AccessKey ID 和 Secret", false)
            return
        }

        aliSave.isEnabled = false
        setStatus(aliStatus, "正在验证…", null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Result.success(AliyunClient().fetch(id, secret))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            aliSave.isEnabled = true
            result.onSuccess { balance ->
                settings.aliyunId = id
                settings.aliyunSecret = secret
                val snapshot = SnapshotStore.load(this@MainActivity).copy(
                    aliyun = balance,
                    lastUpdated = System.currentTimeMillis()
                )
                SnapshotStore.save(this@MainActivity, snapshot)
                renderSnapshot(snapshot)
                BalanceWidgetProvider.updateAll(this@MainActivity, snapshot)
                setStatus(aliStatus, "已连接 · ${formatAmount(balance)}", true)
                setExpanded(aliBody, aliChevron, false)
                BalanceWorker.enqueue(this@MainActivity)
            }.onFailure { error ->
                setStatus(aliStatus, error.toUserMessage(), false)
            }
        }
    }

    private fun refreshAllNow() {
        refreshNow.isEnabled = false
        lastRefresh.text = "正在刷新…"
        lifecycleScope.launch {
            val snapshot = repository.refreshAll()
            refreshNow.isEnabled = true
            renderSnapshot(snapshot)
            BalanceWidgetProvider.updateAll(this@MainActivity, snapshot)
            toast("刷新完成")
        }
    }

    private fun openBatterySettings() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(direct)
        } catch (e: Exception) {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            toast("请在列表中找到“词元钱包”，设为不限制")
        }
    }

    private fun renderSnapshot(snapshot: BalanceSnapshot) {
        updateProviderStatus(
            statusView = dsStatus,
            configured = settings.deepSeekKey.isNotBlank(),
            balance = snapshot.deepSeek
        )
        updateProviderStatus(
            statusView = aliStatus,
            configured = settings.aliyunId.isNotBlank() && settings.aliyunSecret.isNotBlank(),
            balance = snapshot.aliyun
        )
        lastRefresh.text = if (snapshot.lastUpdated <= 0L) {
            "上次更新：尚未更新"
        } else {
            "上次更新：${formatTime(this, snapshot.lastUpdated)}"
        }
    }

    private fun updateProviderStatus(
        statusView: TextView,
        configured: Boolean,
        balance: ProviderBalance
    ) {
        when {
            !configured -> setStatus(statusView, "未配置", null)
            !balance.error.isNullOrBlank() -> setStatus(statusView, balance.error, false)
            else -> setStatus(statusView, "已连接 · ${formatAmount(balance)}", true)
        }
    }

    private fun setStatus(view: TextView, text: String?, ok: Boolean?) {
        view.text = text.orEmpty()
        val colorRes = when (ok) {
            true -> R.color.success
            false -> R.color.error
            null -> R.color.text_secondary
        }
        view.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun setupTabs(state: Bundle?) {
        val saved = state?.getInt(KEY_TAB, R.id.nav_config) ?: R.id.nav_config
        val initial = when (saved) {
            R.id.nav_config, R.id.nav_refresh -> saved
            else -> R.id.nav_config
        }
        bottomNav.setOnItemSelectedListener { item ->
            showPage(item.itemId)
            true
        }
        bottomNav.selectedItemId = initial
        showPage(initial)
    }

    private fun showPage(tabId: Int) {
        selectedTab = tabId
        pageConfig.visibility = if (tabId == R.id.nav_config) View.VISIBLE else View.GONE
        pageRefresh.visibility = if (tabId == R.id.nav_refresh) View.VISIBLE else View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun applySystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        val root = findViewById<View>(R.id.root_shell)
        val nav = findViewById<View>(R.id.bottom_nav)
        val start = root.paddingLeft
        val top = root.paddingTop
        val end = root.paddingRight
        val navStart = nav.paddingLeft
        val navTop = nav.paddingTop
        val navEnd = nav.paddingRight
        val navBottom = nav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The tab bar background should reach the bottom edge, with its
            // content padded above vivo's gesture bar.
            view.setPadding(start, top + bars.top, end, 0)
            nav.setPadding(navStart, navTop, navEnd, navBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun toggle(body: View, chevron: TextView) {
        setExpanded(body, chevron, body.visibility != View.VISIBLE)
    }

    private fun setExpanded(body: View, chevron: TextView, expanded: Boolean) {
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.text = if (expanded) "▴" else "▾"
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            SettingsStore.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsStore.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val KEY_TAB = "selected_tab"
    }
}
