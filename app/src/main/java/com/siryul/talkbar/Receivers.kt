package com.siryul.talkbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** 사용자가 Now Bar 알림을 밀어서 지우면, 새 메시지가 올 때까지 다시 띄우지 않는다 (구글 가이드). */
class DismissReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    ChatStore.dismissVisible()
    EventLog.add(context, context.getString(R.string.log_dismissed))
    NowBarNotifier.refresh(context)
  }
}

/** 카톡 없이 Now Bar 표시와 화면 켜기를 확인하는 테스트 항목. */
object TestItem {
  /** '몇 초 뒤 테스트'의 기다리는 시간. 버튼 누르고 화면을 끌 시간. */
  const val DELAY_SECONDS = 5

  private val handler = Handler(Looper.getMainLooper())

  /** room을 주면 그 방 이름으로 따로 쌓인다 (단톡방처럼 '방 · 보낸 사람'으로 표시). */
  fun show(ctx: Context, sender: String? = null, text: String? = null, room: String? = null) {
    val roomName = room?.takeIf { it.isNotBlank() }
    val key = if (roomName == null) ChatStore.TEST_KEY else ChatStore.testKey(roomName)
    // 같은 글자로 다시 눌러도 '새 알림'이 되게 (그래야 화면 켜기도 매번 시험된다).
    ChatStore.remove(key)
    val isNew = ChatStore.put(
      ChatItem(
        key = key,
        pkg = ctx.packageName,
        appLabel = ctx.getString(R.string.app_name),
        room = roomName,
        sender = sender?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.test_sender),
        text = text?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.test_text),
        time = System.currentTimeMillis(),
        count = 1,
        isGroup = roomName != null,
        contentIntent = null,
      ),
    )
    EventLog.add(ctx, ctx.getString(R.string.log_test_shown))
    NowBarNotifier.refresh(ctx)
    // 진짜 카톡처럼 화면도 켠다 (화면이 이미 켜져 있으면 아무 일 없음).
    if (isNew) ScreenWaker.onNewMessage(ctx, alerting = true)
  }

  /** [DELAY_SECONDS]초 뒤에 테스트 알림을 띄운다. 그 사이 화면을 끄면 화면 켜기를 확인할 수 있다. */
  fun showLater(ctx: Context) {
    val app = ctx.applicationContext
    val delayMs = DELAY_SECONDS * 1000L
    // 화면이 꺼지면 CPU가 잠들어 타이머가 밀릴 수 있어서, 기다리는 동안만 깨워둔다 (시간이 지나면 저절로 풀림).
    val cpu = app.getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TalkBar:test")
      .apply { acquire(delayMs + 3_000L) }
    handler.removeCallbacksAndMessages(null)
    EventLog.add(app, app.getString(R.string.log_test_scheduled, DELAY_SECONDS))
    handler.postDelayed({
      show(app)
      if (cpu.isHeld) cpu.release()
    }, delayMs)
  }

  fun clear(ctx: Context) {
    handler.removeCallbacksAndMessages(null)
    if (ChatStore.removeTests()) EventLog.add(ctx, ctx.getString(R.string.log_test_cleared))
    NowBarNotifier.refresh(ctx)
  }
}
