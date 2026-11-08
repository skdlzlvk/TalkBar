package com.siryul.talkbar

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference

/**
 * 고른 앱(기본은 카톡)의 알림을 받아 ChatStore에 모으고 Now Bar 알림을 갱신한다. 새 알림이면 화면도 켠다.
 * 이름이 Kakao…인 건 처음 카톡 전용이던 때 그대로다. 클래스 이름을 바꾸면 사용자가 준 '알림 접근'이 풀려서 두었다.
 */
class KakaoListenerService : NotificationListenerService() {

  companion object {
    /** 이보다 오래된 알림은 '새 알림'으로 보지 않는다 (앱이 다시 켜진 뒤 옛 알림이 다시 올라올 때 화면이 켜지지 않게). */
    private const val FRESH_MS = 2 * 60_000L

    @Volatile
    var connected = false
      private set

    /** 마지막으로 받은 알림의 구조 (내용은 글자 수만). 파서 조정용. */
    @Volatile
    var lastStructure: String? = null

    private var ref: WeakReference<KakaoListenerService>? = null
    val instance: KakaoListenerService? get() = ref?.get()
  }

  override fun onListenerConnected() {
    connected = true
    ref = WeakReference(this)
    EventLog.add(this, getString(R.string.log_listener_connected))
    resync()
  }

  override fun onListenerDisconnected() {
    connected = false
    ref = null
    // Glimpse FAQ: 시스템이 리스너를 끊는 경우가 있다 → 다시 붙여달라고 요청.
    EventLog.add(this, getString(R.string.log_listener_disconnected))
    requestRebind(ComponentName(this, KakaoListenerService::class.java))
  }

  override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
    if (sbn.packageName == packageName) {
      checkOwn(sbn)
      return
    }
    if (!isSource(sbn.packageName)) return

    val ranking = rankingOf(sbn.key, rankingMap)
    when (val result = NotificationParser.parse(this, sbn, ranking?.importance, Prefs.skipMuted(this))) {
      is ParseResult.Ok -> {
        val item = result.item
        val wasShown = ChatStore.contains(item.key)
        val isNew = ChatStore.put(item)
        EventLog.add(
          this,
          getString(
            R.string.log_received,
            item.appLabel,
            item.displayTitle,
            resources.getQuantityString(R.plurals.chars_count, item.text.length, item.text.length),
            sbn.notification.channelId.toString(),
          ),
        )
        // '한 번만 알림'으로 고쳐 올린 알림은 원래 앱도 소리를 내지 않으니 화면도 켜지 않는다.
        val silentUpdate = wasShown && sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        if (isNew && !silentUpdate && System.currentTimeMillis() - item.time < FRESH_MS) {
          ScreenWaker.onNewMessage(this, alerting = isAlerting(ranking))
        }
      }
      is ParseResult.Skip -> {
        ChatStore.remove(sbn.key)
        if (!result.quiet) EventLog.add(this, getString(R.string.log_skipped, result.reason))
      }
    }
    if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
      lastStructure = NotificationParser.describe(this, sbn, ranking?.importance)
    }
    NowBarNotifier.refresh(this)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
    if (sbn.packageName == packageName) return
    if (ChatStore.remove(sbn.key)) {
      EventLog.add(this, getString(R.string.log_removed, reasonLabel(reason)))
      NowBarNotifier.refresh(this)
    }
  }

  /** 지금 떠 있는 알림을 다시 읽어 상태를 맞춘다. 리스너가 붙었을 때·옵션이나 앱 선택을 바꿨을 때. 화면은 켜지 않는다. */
  fun resync() {
    val active = runCatching { activeNotifications }.getOrNull()
    if (active == null) {
      EventLog.add(this, getString(R.string.log_cant_read_active))
      return
    }
    val rankings = runCatching { currentRanking }.getOrNull()
    val skipMuted = Prefs.skipMuted(this)
    val items = active
      .filter { isSource(it.packageName) }
      .mapNotNull { sbn ->
        (NotificationParser.parse(this, sbn, rankingOf(sbn.key, rankings)?.importance, skipMuted) as? ParseResult.Ok)?.item
      }
    ChatStore.replaceSourceItems(items)
    EventLog.add(this, resources.getQuantityString(R.plurals.log_resynced, items.size, items.size))
    NowBarNotifier.refresh(this)
  }

  private fun isSource(pkg: String) = pkg != packageName &&
    (pkg in Prefs.sources(this) || (pkg == NotificationParser.SHELL && Prefs.acceptShell(this)))

  private fun rankingOf(key: String, map: RankingMap?): Ranking? {
    if (map == null) return null
    val r = Ranking()
    return if (map.getRanking(key, r)) r else null
  }

  /** 소리·진동으로 알리는 알림인가. 알림 끈 방(중요도 낮음)이나 방해 금지에 걸린 알림이면 false. */
  private fun isAlerting(ranking: Ranking?): Boolean {
    if (ranking == null) return true
    return ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT && ranking.matchesInterruptionFilter()
  }

  /** 내가 올린 알림을 시스템이 Live Update로 승격했는지 확인. */
  private fun checkOwn(sbn: StatusBarNotification) {
    if (sbn.id != NowBarNotifier.NOTIFICATION_ID) return
    val promoted = sbn.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
    if (promoted != NowBarNotifier.promoted) {
      NowBarNotifier.promoted = promoted
      EventLog.add(this, getString(R.string.log_verdict, NowBarNotifier.verdictText(this)))
    }
  }

  private fun reasonLabel(reason: Int) = when (reason) {
    REASON_CLICK -> getString(R.string.reason_click)
    REASON_CANCEL -> getString(R.string.reason_cancel)
    REASON_CANCEL_ALL -> getString(R.string.reason_cancel_all)
    REASON_APP_CANCEL -> getString(R.string.reason_app_cancel)
    REASON_APP_CANCEL_ALL -> getString(R.string.reason_app_cancel_all)
    REASON_GROUP_SUMMARY_CANCELED -> getString(R.string.reason_group_summary)
    REASON_TIMEOUT -> getString(R.string.reason_timeout)
    else -> getString(R.string.reason_other, reason)
  }
}
