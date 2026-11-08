package com.siryul.talkbar

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 설정 상태 확인 · 옵션 · 테스트 · 기록 화면. 문구는 전부 res/values*(영어 기본 + 한국어). */
class MainActivity : Activity() {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val listenerComponent by lazy { ComponentName(this, KakaoListenerService::class.java) }
  private val uiRefresher: () -> Unit = { mainHandler.post { refreshUi() } }

  private lateinit var statusBox: LinearLayout
  private lateinit var appsSummary: TextView
  private lateinit var logView: TextView
  private lateinit var structureView: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val scroll = ScrollView(this).apply { addView(root, ViewGroup.LayoutParams(MATCH, WRAP)) }
    setContentView(scroll)
    // targetSdk 36은 화면 끝까지 그려지므로 상태 표시줄·내비게이션 바만큼 여백.
    // 위쪽은 스크롤 뷰에 줘서 내린 글이 상태 표시줄 밑으로 겹쳐 보이지 않게, 아래쪽은 내용에 줘서 마지막 줄이 내비게이션 바 위에서 끝나게.
    scroll.setOnApplyWindowInsetsListener { _, insets ->
      val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
      scroll.setPadding(bars.left, bars.top, bars.right, 0)
      root.setPadding(dp(20), dp(20), dp(20), dp(32) + bars.bottom)
      insets
    }

    root.addView(label(getString(R.string.app_name), 26f, bold = true))
    root.addView(label(getString(R.string.tagline), 14f, dim = true))

    root.addView(header(R.string.section_setup))
    statusBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(statusBox)

    root.addView(header(R.string.section_apps))
    appsSummary = label("", 14f)
    root.addView(appsSummary)
    root.addView(
      button(getString(R.string.btn_choose_apps)) { startActivity(Intent(this, AppPickerActivity::class.java)) },
      LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) },
    )

    root.addView(header(R.string.section_options))
    root.addView(toggle(R.string.opt_enabled, null, Prefs.KEY_ENABLED) { NowBarNotifier.refresh(this) })
    root.addView(toggle(R.string.opt_show_content, R.string.hint_show_content, Prefs.KEY_SHOW_CONTENT) { NowBarNotifier.refresh(this) })
    root.addView(toggle(R.string.opt_skip_muted, R.string.hint_skip_muted, Prefs.KEY_SKIP_MUTED) { resync() })
    // 켜고 끄면 위 '처음 설정'에 '화면 켜기 허용' 줄이 생기거나 사라진다.
    root.addView(toggle(R.string.opt_wake, R.string.hint_wake, Prefs.KEY_WAKE_SCREEN) { refreshUi() })
    if (AppInfo.isDebuggable(this)) {
      root.addView(toggle(R.string.opt_accept_shell, R.string.hint_accept_shell, Prefs.KEY_ACCEPT_SHELL) { resync() })
    }

    root.addView(header(R.string.section_test))
    root.addView(label(getString(R.string.test_hint), 14f, dim = true))
    root.addView(
      row(
        button(getString(R.string.btn_test)) { TestItem.show(this) },
        button(getString(R.string.btn_clear)) { TestItem.clear(this) },
      ),
    )
    root.addView(
      button(getString(R.string.btn_test_later, TestItem.DELAY_SECONDS)) { TestItem.showLater(this) },
      LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) },
    )
    root.addView(label(getString(R.string.test_later_hint, TestItem.DELAY_SECONDS), 13f, dim = true))

    root.addView(header(R.string.section_log))
    root.addView(
      row(
        button(getString(R.string.btn_refresh)) { refreshUi() },
        button(getString(R.string.btn_clear_log)) { EventLog.clear(this) },
      ),
    )
    logView = mono()
    root.addView(logView)
    root.addView(label(getString(R.string.structure_title), 14f, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
    structureView = mono()
    root.addView(structureView)
  }

  override fun onResume() {
    super.onResume()
    ChatStore.listener = uiRefresher
    EventLog.listener = uiRefresher
    refreshUi()
  }

  override fun onPause() {
    ChatStore.listener = null
    EventLog.listener = null
    super.onPause()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    NowBarNotifier.refresh(this)
    refreshUi()
  }

  private fun refreshUi() {
    val nm = getSystemService(NotificationManager::class.java)
    val pm = getSystemService(PowerManager::class.java)
    statusBox.removeAllViews()
    statusBox.addView(
      check(R.string.check_listener, nm.isNotificationListenerAccessGranted(listenerComponent), R.string.hint_listener) {
        openListenerSettings()
      },
    )
    statusBox.addView(
      check(
        R.string.check_post_notifications,
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        null,
      ) { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) },
    )
    statusBox.addView(
      check(R.string.check_live_updates, nm.canPostPromotedNotifications(), R.string.hint_live_updates) {
        openPromotionSettings()
      },
    )
    if (AppInfo.isSamsung) {
      // One UI 9 실측: 안드로이드 쪽 승격(PROMOTED_ONGOING)이 돼도, 삼성 Now Bar는 허용 목록 앱만 받는다.
      // 나머지 앱은 개발자 옵션 '모든 앱의 실시간 정보 보기'(enable_notification_nowbar_test)를 켜야 들어간다.
      statusBox.addView(
        check(R.string.check_samsung_nowbar, readSecureFlag(SAMSUNG_NOWBAR_ALL_APPS), R.string.hint_samsung_nowbar) {
          openDeveloperOptions()
        },
      )
      statusBox.addView(
        check(R.string.check_samsung_live_info, null, R.string.hint_samsung_live_info) { openAppNotificationSettings() },
      )
    }
    if (Prefs.wakeScreen(this)) {
      statusBox.addView(
        check(R.string.check_turn_screen_on, ScreenWaker.permissionState(this), R.string.hint_turn_screen_on) {
          openTurnScreenOnSettings()
        },
      )
    }
    statusBox.addView(
      check(R.string.check_battery, pm.isIgnoringBatteryOptimizations(packageName), R.string.hint_battery) {
        openBatterySettings()
      },
    )
    statusBox.addView(
      info(
        R.string.info_listener,
        getString(if (KakaoListenerService.connected) R.string.listener_connected else R.string.listener_disconnected),
      ),
    )
    statusBox.addView(info(R.string.info_status, NowBarNotifier.statusText(this)))
    statusBox.addView(info(R.string.info_verdict, NowBarNotifier.verdictText(this)))

    // 안 깔린 앱(예: 카톡 없는 폰의 기본값 카톡)은 앱 고르기 목록에도 없어서 끌 수 없으니 여기서도 뺀다.
    val apps = Prefs.sources(this).mapNotNull { NotificationParser.installedLabel(this, it) }.sorted()
    appsSummary.text =
      if (apps.isEmpty()) getString(R.string.apps_none) else getString(R.string.apps_summary, apps.joinToString(", "))

    logView.text = EventLog.all(this).asReversed().joinToString("\n").ifEmpty { getString(R.string.log_empty) }
    structureView.text = KakaoListenerService.lastStructure ?: getString(R.string.structure_empty)
  }

  private fun resync() {
    KakaoListenerService.instance?.resync() ?: NowBarNotifier.refresh(this)
  }

  // ---- 설정 화면 열기 (앞의 것이 안 열리면 다음 것)

  private fun openListenerSettings() = launch(
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
      .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString()),
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
  )

  private fun openPromotionSettings() = launch(
    Intent(NowBarNotifier.ACTION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
  )

  private fun openDeveloperOptions() = launch(
    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
    Intent(Settings.ACTION_SETTINGS),
  )

  private fun openAppNotificationSettings() = launch(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    appDetails(),
  )

  /** 특별한 접근 → 화면 켜기 → 이 앱. package: 를 붙이면 목록 대신 이 앱 스위치가 바로 열린다 (One UI 9 확인). */
  private fun openTurnScreenOnSettings() = launch(
    Intent(ACTION_TURN_SCREEN_ON_SETTINGS, Uri.parse("package:$packageName")),
    Intent(ACTION_TURN_SCREEN_ON_SETTINGS),
    appDetails(),
  )

  /**
   * 앱 정보 → 배터리 → '제한 없음'. 바로 묻는 팝업(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)은
   * 플레이 스토어가 특정 앱에만 허용하는 권한이라 쓰지 않는다.
   */
  private fun openBatterySettings() = launch(
    appDetails(),
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
  )

  private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

  /** 삼성 설정값 읽기. 일반 앱이 못 읽는 키면 null (화면에 ❔). */
  private fun readSecureFlag(key: String): Boolean? =
    runCatching { Settings.Secure.getInt(contentResolver, key, 0) == 1 }.getOrNull()

  private fun launch(vararg intents: Intent) {
    var error: Throwable? = null
    for (intent in intents) {
      try {
        startActivity(intent)
        return
      } catch (e: Exception) {
        error = e
      }
    }
    EventLog.add(this, getString(R.string.log_cant_open_settings, error?.javaClass?.simpleName.toString()))
  }

  // ---- 화면 조각

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

  private fun label(text: String, sizeSp: Float, bold: Boolean = false, dim: Boolean = false) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    if (dim) alpha = 0.7f
    setPadding(0, dp(2), 0, dp(2))
  }

  private fun header(textRes: Int) = label(getString(textRes), 18f, bold = true).apply { setPadding(0, dp(24), 0, dp(8)) }

  private fun mono() = TextView(this).apply {
    typeface = Typeface.MONOSPACE
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    setTextIsSelectable(true)
  }

  private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setOnClickListener { onClick() }
  }

  private fun row(vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEachIndexed { i, v ->
      addView(v, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(8) })
    }
  }

  private fun check(titleRes: Int, ok: Boolean?, hintRes: Int?, onOpen: () -> Unit): View {
    val box = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dp(6), 0, dp(6))
    }
    val line = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    val mark = when (ok) {
      true -> "✅ "
      false -> "❌ "
      null -> "❔ "
    }
    line.addView(label(mark + getString(titleRes), 16f), LinearLayout.LayoutParams(0, WRAP, 1f))
    line.addView(button(getString(if (ok == true) R.string.btn_settings else R.string.btn_open), onOpen))
    box.addView(line)
    if (hintRes != null && ok != true) box.addView(label(getString(hintRes), 13f, dim = true))
    return box
  }

  private fun info(titleRes: Int, value: String) =
    label("${getString(titleRes)}: $value", 14f).apply { setPadding(0, dp(4), 0, dp(4)) }

  private fun toggle(titleRes: Int, hintRes: Int?, key: String, onChanged: () -> Unit): View {
    val box = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dp(6), 0, dp(6))
    }
    val title = getString(titleRes)
    val sw = Switch(this).apply {
      text = title
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      isChecked = Prefs.get(this@MainActivity, key)
      setOnCheckedChangeListener { _, checked ->
        Prefs.set(this@MainActivity, key, checked)
        EventLog.add(
          this@MainActivity,
          getString(R.string.log_option_changed, title, getString(if (checked) R.string.on else R.string.off)),
        )
        onChanged()
      }
    }
    box.addView(sw, LinearLayout.LayoutParams(MATCH, WRAP))
    if (hintRes != null) box.addView(label(getString(hintRes), 13f, dim = true))
    return box
  }

  private companion object {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /** 개발자 옵션 '모든 앱의 실시간 정보 보기' (Live notifications for all apps). */
    const val SAMSUNG_NOWBAR_ALL_APPS = "enable_notification_nowbar_test"

    /** Settings.ACTION_TURN_SCREEN_ON_SETTINGS (SDK 36 jar에 상수가 없어서 문자열로, 폰에서 확인). */
    const val ACTION_TURN_SCREEN_ON_SETTINGS = "android.settings.TURN_SCREEN_ON_SETTINGS"
  }
}
