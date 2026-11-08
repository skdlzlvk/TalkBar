package com.siryul.talkbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * adb 테스트용. 개발(debug) 빌드에만 들어간다 (src/debug). DUMP 권한으로 막혀 있어서 셸·시스템만 보낼 수 있다.
 * 따옴표 주의: 기기 셸이 한 번 더 쪼개므로 adb shell "…" 전체를 큰따옴표로 감싸고 값은 작은따옴표로.
 *
 *   adb shell "am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd test --es sender '홍길동' --es text '안녕'"
 *   adb shell "am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd test --es room '가족방' --es sender '엄마' --es text '밥'"
 *     (room을 주면 방마다 따로 쌓인다. 여러 방 표시 확인용)
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd later   (5초 뒤 테스트, 화면 켜기 확인)
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd clear   (테스트 항목 전부)
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd clear_shell   (cmd notification post로 올린 셸 알림 지우기)
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd set --es key accept_shell --ez value true
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd status
 *   adb shell am broadcast -n com.siryul.talkbar/.DebugReceiver --es cmd app --es pkg com.samsung.android.messaging --ez value true
 *     (Now Bar에 띄울 앱 켜고 끄기. 앱 고르기 화면과 같다)
 */
class DebugReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.getStringExtra("cmd")) {
      "test" -> TestItem.show(
        context, intent.getStringExtra("sender"), intent.getStringExtra("text"), intent.getStringExtra("room"),
      )
      "later" -> TestItem.showLater(context)
      "clear" -> TestItem.clear(context)
      // cmd notification엔 지우는 명령이 없어서, 셸로 올린 테스트 알림은 알림 접근 권한으로 지운다.
      "clear_shell" -> KakaoListenerService.instance?.let { svc ->
        runCatching { svc.activeNotifications }.getOrNull()
          ?.filter { it.packageName == NotificationParser.SHELL }
          ?.forEach { svc.cancelNotification(it.key) }
      }
      "resync" -> KakaoListenerService.instance?.resync()
      "app" -> {
        val pkg = intent.getStringExtra("pkg")
        if (pkg != null && intent.hasExtra("value")) {
          val on = intent.getBooleanExtra("value", false)
          Prefs.setSource(context, pkg, on)
          EventLog.add(context, context.getString(R.string.log_app_toggled, "adb $pkg", on.toString()))
          KakaoListenerService.instance?.resync() ?: NowBarNotifier.refresh(context)
        }
      }
      "set" -> {
        val key = intent.getStringExtra("key")
        if (key != null && intent.hasExtra("value")) {
          Prefs.set(context, key, intent.getBooleanExtra("value", false))
          EventLog.add(context, context.getString(R.string.log_option_changed, "adb $key", Prefs.get(context, key).toString()))
          KakaoListenerService.instance?.resync() ?: NowBarNotifier.refresh(context)
        }
      }
    }
    resultData = "listener=${KakaoListenerService.connected} | items=${ChatStore.count()} | " +
      "posted=${NowBarNotifier.statusText(context)} | verdict=${NowBarNotifier.verdictText(context)} | " +
      "wakePermission=${ScreenWaker.permissionState(context)} | apps=${Prefs.sources(context).sorted()}"
  }
}
