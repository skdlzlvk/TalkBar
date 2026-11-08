package com.siryul.talkbar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * 모아둔 알림(카톡 등 고른 앱)을 Live Update(승격 알림) 하나로 올린다. One UI 9는 이걸 Now Bar(잠금화면·AOD)에 띄운다.
 *
 * Live Update 조건 (developer.android.com/develop/ui/views/notifications/live-update):
 * 기본/BigText 스타일, ongoing, contentTitle 있음, 커스텀 뷰 없음, colorized 아님,
 * 묶음 요약 아님, 채널 중요도 MIN 아님, POST_PROMOTED_NOTIFICATIONS 권한, 승격 요청 extras.
 */
object NowBarNotifier {
  const val CHANNEL_ID = "nowbar_live"
  const val NOTIFICATION_ID = 1001

  /** Notification.EXTRA_REQUEST_PROMOTED_ONGOING. SDK 36 jar에 상수가 없어서 문자열로 (폰 프레임워크에서 확인). */
  const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

  /** Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS (폰 프레임워크에서 확인). */
  const val ACTION_PROMOTION_SETTINGS = "android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"

  /** Now Bar 둘째 줄(= shortCriticalText) 최대 글자 수. 넘치면 카드가 알아서 줄임표 처리한다. */
  private const val SECOND_LINE_MAX = 40

  /**
   * 삼성 OngoingActivity의 primaryInfo. One UI 9 SystemUI가 contentTitle보다 먼저 읽고, Live Update면 이걸
   * 접힌 Now Bar 카드 첫 줄로 쓴다. 펼친 카드(알림 본문)는 그대로 contentTitle을 쓴다.
   */
  private const val EXTRA_SEC_PRIMARY_INFO = "android.ongoingActivityNoti.primaryInfo"

  private const val MAX_LINES = 6

  /** 화면에 보여줄 마지막 상태 문구. 아직 없으면 null. */
  @Volatile
  var lastPosted: String? = null

  /** 시스템이 내 알림을 Live Update로 승격했는가. 아직 모르면 null. */
  @Volatile
  var promoted: Boolean? = null

  /** 마지막으로 올린 내용. 같으면 다시 올리지 않는다 (알림 끈 방 메시지 등으로 Now Bar 카드가 괜히 다시 그려지지 않게). */
  private var lastSignature: String? = null

  fun statusText(ctx: Context) = lastPosted ?: ctx.getString(R.string.status_never)

  fun verdictText(ctx: Context) = when (promoted) {
    true -> ctx.getString(R.string.verdict_promoted)
    false -> ctx.getString(R.string.verdict_not_promoted)
    null -> ctx.getString(R.string.verdict_unknown)
  }

  fun refresh(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val items = ChatStore.visible()
    if (!Prefs.enabled(ctx) || items.isEmpty()) {
      nm.cancel(NOTIFICATION_ID)
      lastSignature = null
      lastPosted = ctx.getString(if (Prefs.enabled(ctx)) R.string.status_nothing else R.string.status_off)
      return
    }
    if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      lastPosted = ctx.getString(R.string.status_no_permission)
      return
    }
    val signature = signatureOf(ctx, items)
    val stillShown = nm.activeNotifications.any { it.id == NOTIFICATION_ID }
    if (signature == lastSignature && stillShown) return
    ensureChannel(ctx, nm)
    val notification = build(ctx, items)
    nm.notify(NOTIFICATION_ID, notification)
    lastSignature = signature
    lastPosted = ctx.getString(
      R.string.status_showing,
      ctx.resources.getQuantityString(R.plurals.items_count, items.size, items.size),
      ctx.getString(if (notification.hasPromotableCharacteristics()) R.string.shape_ok else R.string.shape_bad),
      ctx.getString(if (nm.canPostPromotedNotifications()) R.string.promotion_allowed else R.string.promotion_off),
    )
  }

  /**
   * 앱을 2개 이상 골랐는가 (깔린 앱만 센다). 그러면 접힌 카드 첫 줄에 앱 이름을 붙인다.
   * 카드 아이콘은 어느 앱 알림이든 톡바 아이콘이라 (삼성이 정함, 2026-11-09 실측) 글자로 앱을 알려준다.
   */
  private fun manyAppsChosen(ctx: Context) =
    Prefs.sources(ctx).count { NotificationParser.installedLabel(ctx, it) != null } > 1

  private fun signatureOf(ctx: Context, items: List<ChatItem>): String = buildString {
    // 언어를 바꾸면 문구가 달라지니까 언어도 넣는다.
    append(ctx.resources.configuration.locales[0]).append('/')
    append(Prefs.showContent(ctx)).append('/').append(manyAppsChosen(ctx)).append('/').append(items.size)
    for (it in items.take(MAX_LINES)) {
      append('|').append(it.key).append('@').append(it.time).append('#').append(it.count)
        .append(':').append(it.displayTitle.hashCode()).append(':').append(it.text.hashCode())
    }
  }

  /** 채널을 만들거나, 이미 있으면 이름·설명만 지금 언어로 바꾼다 (중요도·소리는 처음 만든 값이 유지된다). */
  private fun ensureChannel(ctx: Context, nm: NotificationManager) {
    // 중요도 DEFAULT(소리 없음): '무음 알림'으로 분류돼 AOD에서 빠지는 걸 피하려고. 소리·진동은 원래 앱이 낸다.
    val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
      description = ctx.getString(R.string.channel_description)
      setSound(null, null)
      enableVibration(false)
      enableLights(false)
      setShowBadge(false)
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    nm.createNotificationChannel(channel)
  }

  private fun build(ctx: Context, items: List<ChatItem>): Notification {
    val res = ctx.resources
    val showContent = Prefs.showContent(ctx)
    val top = items.first()
    fun body(item: ChatItem) =
      if (showContent) item.text.ifBlank { ctx.getString(R.string.new_message) }
      else res.getQuantityString(R.plurals.new_messages_count, item.count, item.count)

    val text = body(top)
    // 여러 앱이 섞여 있으면 펼친 카드의 줄마다 앱 이름을 붙인다.
    val manyApps = items.distinctBy { it.pkg }.size > 1
    val bigText = if (items.size == 1) text
    else items.take(MAX_LINES).joinToString("\n") {
      if (manyApps) "${it.appLabel} · ${it.displayTitle}: ${body(it)}" else "${it.displayTitle}: ${body(it)}"
    }
    // 알림 머리줄 "톡바 • (여기) • 시각". 하나면 어느 앱 알림인지 보여준다.
    val subText = when {
      items.size > 1 -> res.getQuantityString(R.plurals.items_count, items.size, items.size)
      top.pkg == ctx.packageName -> null
      top.count > 1 -> "${top.appLabel} · ${res.getQuantityString(R.plurals.messages_count, top.count, top.count)}"
      else -> top.appLabel
    }
    // 삼성 Now Bar 카드는 첫 줄 = contentTitle, 둘째 줄 = shortCriticalText 로 채운다.
    // One UI 9 SystemUI가 Live Update 알림이면 삼성 전용 nowbarSecondaryInfo extras도 이 값으로 덮어쓴다 (2026-11-08 실측).
    // 그래서 칩 글자 자리에 메시지를 넣는다. 상태 표시줄 칩에도 같은 글자가 나온다.
    val secondLine = text.replace('\n', ' ').take(SECOND_LINE_MAX)
    // 앱을 2개 이상 골랐으면 첫 줄 앞에 앱 이름을 붙인다 ("카카오톡 · 엄마"). 테스트 항목은 빼고.
    val cardTitle =
      if (manyAppsChosen(ctx) && top.pkg != ctx.packageName) "${top.appLabel} · ${top.displayTitle}" else top.displayTitle
    // 접힌 카드엔 최신 방 하나만 보이니까, 다른 방이 더 있으면 첫 줄에 개수를 붙인다. 카드를 누르면 전체 목록이 펼쳐진다.
    val firstLine =
      if (items.size > 1) ctx.getString(R.string.first_line_more, cardTitle, items.size - 1) else cardTitle

    val builder = Notification.Builder(ctx, CHANNEL_ID)
    // 상태 표시줄 칩·알림창 아이콘은 가장 최근 알림을 올린 앱의 아이콘 (못 읽었으면 톡바 아이콘).
    // 잠금화면·AOD의 Now Bar 카드는 삼성이 늘 톡바 앱 아이콘으로 그린다. 작은 아이콘·큰 아이콘 둘 다 안 먹었다 (2026-11-09 실측).
    if (top.icon != null) builder.setSmallIcon(top.icon) else builder.setSmallIcon(R.drawable.ic_stat_chat)
    return builder
      .setColor(ctx.getColor(R.color.accent))
      .setContentTitle(top.displayTitle)
      .setContentText(text)
      .setStyle(Notification.BigTextStyle().bigText(bigText))
      .setSubText(subText)
      .setWhen(top.time)
      .setShowWhen(true)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setContentIntent(top.contentIntent ?: openIntent(ctx, top))
      .setDeleteIntent(
        PendingIntent.getBroadcast(
          ctx, 1, Intent(ctx, DismissReceiver::class.java),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
      )
      .setShortCriticalText(secondLine)
      .addExtras(
        Bundle().apply {
          putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
          putCharSequence(EXTRA_SEC_PRIMARY_INFO, firstLine)
        },
      )
      .build()
  }

  private fun openIntent(ctx: Context, item: ChatItem): PendingIntent {
    val app = if (ChatStore.isTest(item.key)) null
    else ctx.packageManager.getLaunchIntentForPackage(item.pkg)
    val intent = (app ?: Intent(ctx, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(ctx, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }
}
