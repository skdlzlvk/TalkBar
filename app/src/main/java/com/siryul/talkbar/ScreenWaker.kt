package com.siryul.talkbar

import android.app.AppOpsManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock

/**
 * 새 카톡이 오면 화면을 켠다. 아이폰처럼 화면이 켜지고, 잠금화면에서 Now Bar 카드와 쌓인 알림을 본다.
 * 삼성 설정엔 '알림 오면 화면 켜기'가 없어서 (One UI 9 확인) 앱이 직접 켠다.
 *
 * - 안드로이드 14+: ACQUIRE_CAUSES_WAKEUP 웨이크락은 TURN_SCREEN_ON 권한과 앱별 '화면 켜기 허용'이 있어야 화면을 켠다.
 * - Glimpse에서 배운 것: 주머니 속·뒤집어 놓은 상태(근접 센서 가림)에선 켜지 않는다.
 * - 이미 화면이 켜져 있거나, 무음·방해 금지로 조용히 온 알림이면 켜지 않는다.
 */
object ScreenWaker {
  /** AppOpsManager.OPSTR_TURN_SCREEN_ON (SDK엔 숨겨져 있어서 문자열로). */
  private const val OP_TURN_SCREEN_ON = "android:turn_screen_on"

  /**
   * 화면을 켠 뒤 붙잡아 두는 시간. 놓은 뒤엔 잠금화면 자체 시간(폴드8 실측 6초)만큼 더 켜져 있어서 합쳐 9초쯤 보인다.
   * 4초 넘게 잡고 있으면 삼성 PowerManager가 'abuse wakelock'으로 표시해서 (2026-11-08 실측) 그보다 짧게.
   */
  private const val HOLD_MS = 3_000L

  /** 방금 켠 뒤 이 시간 안에는 다시 켜지 않는다 (단톡방 연속 메시지, 사용자가 바로 끈 경우). */
  private const val COOLDOWN_MS = 20_000L

  /** 근접 센서 첫 값을 기다리는 최대 시간. 안 오면 가려지지 않은 걸로 본다. */
  private const val SENSOR_TIMEOUT_MS = 400L

  private val handler = Handler(Looper.getMainLooper())
  private var lastWakeAt = -COOLDOWN_MS
  private var checking = false

  /**
   * '화면 켜기 허용' 상태. 허용이면 true, 사용자가 껐으면 false.
   * 기본값(아직 아무도 안 건드림)이면 null: 시스템 정책에 따라 켜질 수도 있다.
   */
  fun permissionState(ctx: Context): Boolean? {
    val mode = runCatching {
      ctx.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(OP_TURN_SCREEN_ON, Process.myUid(), ctx.packageName)
    }.getOrNull()
    return when (mode) {
      AppOpsManager.MODE_ALLOWED -> true
      AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
      else -> null
    }
  }

  /** 새 메시지가 왔을 때 부른다. [alerting]: 소리·진동으로 알리는 알림인가 (무음·방해 금지면 false). */
  fun onNewMessage(ctx: Context, alerting: Boolean) {
    val app = ctx.applicationContext
    if (!Prefs.enabled(app) || !Prefs.wakeScreen(app)) return
    val pm = app.getSystemService(PowerManager::class.java)
    if (pm.isInteractive) return
    if (!alerting) return skipped(app, R.string.wake_skip_not_alerting)
    if (SystemClock.elapsedRealtime() - lastWakeAt < COOLDOWN_MS) return skipped(app, R.string.wake_skip_cooldown)
    if (permissionState(app) == false) return skipped(app, R.string.wake_skip_no_permission)
    if (checking) return
    checking = true
    // AOD 중엔 CPU가 잠들 수 있어서 센서 확인하는 동안만 깨워둔다.
    val cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TalkBar:check").apply { acquire(2_000L) }
    isCovered(app) { covered ->
      checking = false
      if (covered) skipped(app, R.string.wake_skip_covered) else wake(app, pm)
      if (cpu.isHeld) cpu.release()
    }
  }

  @Suppress("DEPRECATION") // 화면을 켜는 웨이크락 종류는 이것뿐 (FULL/SCREEN_BRIGHT 모두 deprecated).
  private fun wake(ctx: Context, pm: PowerManager) {
    pm.newWakeLock(
      PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
      "TalkBar:newMessage",
    ).acquire(HOLD_MS)
    lastWakeAt = SystemClock.elapsedRealtime()
    // 시스템이 막으면 에러 없이 안 켜진다. 잠깐 뒤 실제로 켜졌는지 보고 기록.
    handler.postDelayed({
      EventLog.add(
        ctx,
        if (pm.isInteractive) ctx.getString(R.string.log_wake_on)
        else ctx.getString(R.string.log_wake_skipped, ctx.getString(R.string.wake_skip_blocked)),
      )
    }, 800L)
  }

  private fun skipped(ctx: Context, reasonRes: Int) {
    EventLog.add(ctx, ctx.getString(R.string.log_wake_skipped, ctx.getString(reasonRes)))
  }

  /** 근접 센서가 가려져 있는가 (주머니 속·뒤집힘). 센서가 없거나 값이 안 오면 false. */
  private fun isCovered(ctx: Context, done: (Boolean) -> Unit) {
    val sm = ctx.getSystemService(SensorManager::class.java)
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    if (sm == null || sensor == null) return done(false)
    var finished = false
    lateinit var listener: SensorEventListener
    val timeout = Runnable {
      if (finished) return@Runnable
      finished = true
      sm.unregisterListener(listener)
      done(false)
    }
    listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        sm.unregisterListener(this)
        // 근접 센서는 보통 가까움 = 0, 멀음 = 최대값 두 가지만 보낸다.
        done(event.values[0] < sensor.maximumRange)
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)) return done(false)
    handler.postDelayed(timeout, SENSOR_TIMEOUT_MS)
  }
}
