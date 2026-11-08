package com.siryul.talkbar

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import java.text.Collator

/**
 * Now Bar에 띄울 앱 고르기. 설치된 앱(런처에 보이는 앱) 목록에서 스위치로 켜고 끈다.
 * 순서: 이미 켠 앱 → 설치된 메시지 앱 → 나머지 이름순. 켜고 끌 때마다 떠 있는 알림을 다시 읽는다.
 */
class AppPickerActivity : Activity() {

  private data class AppRow(val pkg: String, val label: String)

  private val rows = mutableListOf<AppRow>()
  private var shown: List<AppRow> = emptyList()
  private val icons = HashMap<String, Drawable>()
  private var query = ""

  private lateinit var adapter: BaseAdapter
  private lateinit var status: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    setContentView(root)
    // 화면 끝까지 그려지므로 상태 표시줄·내비게이션 바·키보드만큼 여백.
    root.setOnApplyWindowInsetsListener { _, insets ->
      val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
      val ime = insets.getInsets(WindowInsets.Type.ime())
      root.setPadding(dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, maxOf(bars.bottom, ime.bottom) + dp(8))
      insets
    }

    val top = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    top.addView(
      TextView(this).apply {
        text = getString(R.string.apps_title)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTypeface(typeface, Typeface.BOLD)
      },
      LinearLayout.LayoutParams(0, WRAP, 1f),
    )
    top.addView(Button(this).apply {
      text = getString(R.string.btn_done)
      isAllCaps = false
      setOnClickListener { finish() }
    })
    root.addView(top)
    root.addView(
      TextView(this).apply {
        text = getString(R.string.apps_hint)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        alpha = 0.7f
        setPadding(0, dp(4), 0, dp(8))
      },
    )
    root.addView(
      EditText(this).apply {
        hint = getString(R.string.apps_search)
        isSingleLine = true
        addTextChangedListener(object : TextWatcher {
          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
          override fun afterTextChanged(s: Editable?) {
            query = s?.toString().orEmpty()
            filter()
          }
        })
      },
      LinearLayout.LayoutParams(MATCH, WRAP),
    )
    status = TextView(this).apply {
      text = getString(R.string.apps_loading)
      setPadding(0, dp(12), 0, dp(12))
    }
    root.addView(status)

    adapter = object : BaseAdapter() {
      override fun getCount() = shown.size
      override fun getItem(position: Int) = shown[position]
      override fun getItemId(position: Int) = position.toLong()
      override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
        bindRow((convertView as? LinearLayout) ?: newRow(), shown[position])
    }
    root.addView(ListView(this).apply { adapter = this@AppPickerActivity.adapter }, LinearLayout.LayoutParams(MATCH, 0, 1f))

    Thread { load() }.start()
  }

  /** 런처에 보이는 앱 목록 (매니페스트 <queries>의 MAIN/LAUNCHER로 보이는 범위). 시간이 걸려서 따로 돌린다. */
  private fun load() {
    val pm = packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
      .distinctBy { it.activityInfo.packageName }
      .filter { it.activityInfo.packageName != packageName }
      .map { AppRow(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    val selected = Prefs.sources(this)
    val collator = Collator.getInstance()
    val sorted = apps.sortedWith(
      compareBy<AppRow>({ it.pkg !in selected }, { it.pkg !in MESSENGERS }).thenComparator { a, b ->
        collator.compare(a.label, b.label)
      },
    )
    runOnUiThread {
      if (isDestroyed) return@runOnUiThread
      rows.clear()
      rows += sorted
      status.visibility = View.GONE
      filter()
    }
  }

  private fun filter() {
    val q = query.trim()
    shown = if (q.isEmpty()) rows.toList()
    else rows.filter { it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
    adapter.notifyDataSetChanged()
  }

  private fun newRow() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, dp(8), 0, dp(8))
    addView(ImageView(this@AppPickerActivity), LinearLayout.LayoutParams(dp(40), dp(40)))
    addView(
      TextView(this@AppPickerActivity).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dp(14), 0, dp(8), 0)
      },
      LinearLayout.LayoutParams(0, WRAP, 1f),
    )
    // 줄 전체를 눌러도 켜지게, 스위치 자체는 포커스를 받지 않게.
    addView(Switch(this@AppPickerActivity).apply { isFocusable = false })
  }

  private fun bindRow(row: LinearLayout, app: AppRow): View {
    val icon = row.getChildAt(0) as ImageView
    val label = row.getChildAt(1) as TextView
    val sw = row.getChildAt(2) as Switch
    icon.setImageDrawable(
      icons.getOrPut(app.pkg) {
        runCatching { packageManager.getApplicationIcon(app.pkg) }.getOrElse { packageManager.defaultActivityIcon }
      },
    )
    label.text = app.label
    sw.setOnCheckedChangeListener(null)
    sw.isChecked = app.pkg in Prefs.sources(this)
    sw.setOnCheckedChangeListener { _, checked -> toggle(app, checked) }
    row.setOnClickListener { sw.toggle() }
    return row
  }

  private fun toggle(app: AppRow, on: Boolean) {
    Prefs.setSource(this, app.pkg, on)
    EventLog.add(this, getString(R.string.log_app_toggled, app.label, getString(if (on) R.string.on else R.string.off)))
    // 켠 앱의 지금 떠 있는 알림을 바로 가져오고, 끈 앱의 알림은 Now Bar에서 뺀다.
    KakaoListenerService.instance?.resync() ?: NowBarNotifier.refresh(this)
  }

  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

  private companion object {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /** 목록 위쪽에 먼저 보여줄 메시지 앱 (설치된 것만 목록에 나온다). */
    val MESSENGERS = setOf(
      NotificationParser.KAKAO,
      "com.samsung.android.messaging", // 삼성 메시지
      "com.google.android.apps.messaging", // Google 메시지
      "jp.naver.line.android",
      "org.telegram.messenger",
      "com.whatsapp",
      "com.instagram.android",
      "com.facebook.orca", // Messenger
      "com.discord",
      "com.Slack",
      "com.microsoft.teams",
      "com.nhn.android.band",
      "com.google.android.gm", // Gmail
      "com.samsung.android.email.provider",
    )
  }
}
