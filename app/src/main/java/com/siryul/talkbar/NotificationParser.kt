package com.siryul.talkbar

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification

/** 알림 하나(대화방 하나, 또는 일반 알림 하나)에서 뽑은 내용. */
data class ChatItem(
  val key: String,
  /** 알림을 올린 앱. */
  val pkg: String,
  /** 그 앱 이름. 여러 앱이 섞이면 펼친 카드의 줄 앞에 붙인다. */
  val appLabel: String,
  /** 그룹방 이름. 1:1이나 일반 알림이면 null. */
  val room: String?,
  /** 보낸 사람. 일반 알림이면 알림 제목. */
  val sender: String,
  /** 마지막 메시지 내용. 일반 알림이면 알림 내용. */
  val text: String,
  val time: Long,
  /** 이 알림에 쌓인 메시지 수 (앱이 준 값 기준). */
  val count: Int,
  val isGroup: Boolean,
  /** 원래 알림의 '눌렀을 때 열 화면'. 내 알림을 눌러도 같은 곳이 열리게 재사용. */
  val contentIntent: PendingIntent?,
  /** 원래 알림의 작은 아이콘 (Now Bar 카드에 그 앱 아이콘을 보여주려고). 없으면 톡바 아이콘. */
  val icon: Icon? = null,
) {
  val displayTitle: String
    get() = if (isGroup && !room.isNullOrBlank() && room != sender) "$room · $sender" else sender

  /** 같은 알림이 그대로 다시 올라온 게 아니라 내용이 바뀌었는가. */
  fun differsFrom(old: ChatItem) = text != old.text || count != old.count || sender != old.sender || room != old.room
}

sealed interface ParseResult {
  data class Ok(val item: ChatItem) : ParseResult
  data class Skip(val reason: String, val quiet: Boolean = false) : ParseResult
}

/**
 * 알림 해석. 메시지 앱(MessagingStyle)은 보낸 사람·방·마지막 메시지를, 그 밖의 앱은 제목·내용을 뽑는다.
 *
 * 폴드8 실측(카톡 26.9.2): 대화방마다 알림 1개(tag = 방 ID) + 묶음 요약 1개.
 * 형식은 MessagingStyle이고 android.messages 배열에 메시지별 보낸 사람·내용·시각이 들어 있다.
 * 일반 방은 new_message_v1 채널(중요도 높음), 알림 끈 방은 quiet_new_message 채널(중요도 낮음)로 온다.
 * 문자·라인·텔레그램·왓츠앱·인스타 DM 같은 메시지 앱도 대부분 같은 MessagingStyle이다.
 */
object NotificationParser {
  const val KAKAO = "com.kakao.talk"
  const val SHELL = "com.android.shell"

  // Notification.MessagingStyle.Message 번들 키
  private const val KEY_TEXT = "text"
  private const val KEY_TIME = "time"
  private const val KEY_SENDER = "sender"
  private const val KEY_SENDER_PERSON = "sender_person"

  /**
   * Now Bar에 안 띄우는 종류. 음악·내비·통화·진행률·운동처럼 계속 떠 있는 알림(대부분 Now Bar가 따로 보여줌),
   * 시스템 상태, 광고·추천.
   */
  private val SKIP_CATEGORIES = setOf(
    Notification.CATEGORY_TRANSPORT,
    Notification.CATEGORY_NAVIGATION,
    Notification.CATEGORY_CALL,
    Notification.CATEGORY_PROGRESS,
    Notification.CATEGORY_SERVICE,
    Notification.CATEGORY_SYSTEM,
    Notification.CATEGORY_STOPWATCH,
    Notification.CATEGORY_LOCATION_SHARING,
    Notification.CATEGORY_WORKOUT,
    Notification.CATEGORY_PROMO,
    Notification.CATEGORY_RECOMMENDATION,
  )

  /** 내용을 보여줘도 괜찮은 구조 정보 키 (나머지 글자 값은 글자 수만 표시). */
  private val SHOW_VALUE_KEYS = setOf(
    Notification.EXTRA_TEMPLATE,
    "androidx.core.app.extra.COMPAT_TEMPLATE",
  )

  /** 앱 이름 캐시. 언어가 바뀌면 이름도 바뀌니까 언어를 키에 넣는다. */
  private val labels = HashMap<String, String>()

  fun parse(ctx: Context, sbn: StatusBarNotification, importance: Int?, skipMuted: Boolean): ParseResult {
    val n = sbn.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
      return ParseResult.Skip(ctx.getString(R.string.skip_group_summary), quiet = true)
    }
    if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) {
      return ParseResult.Skip(ctx.getString(R.string.skip_ongoing))
    }
    val ex = n.extras
    if (n.category in SKIP_CATEGORIES || ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
      return ParseResult.Skip(ctx.getString(R.string.skip_category, n.category.toString()))
    }
    if (skipMuted && importance != null && importance in 1 until NotificationManager.IMPORTANCE_DEFAULT) {
      return ParseResult.Skip(ctx.getString(R.string.skip_muted, n.channelId.toString(), importance))
    }

    val appLabel = labelOf(ctx, sbn.packageName)
    val messages = ex.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
      ?.filterIsInstance<Bundle>()
      .orEmpty()
    val title = ex.getCharSequence(Notification.EXTRA_TITLE).clean()
    val count = when {
      n.number > 0 -> n.number
      messages.isNotEmpty() -> messages.size
      else -> 1
    }

    val item = if (messages.isNotEmpty()) {
      // 메시지 앱: 마지막 메시지의 보낸 사람·내용·시각.
      val last = messages.last()
      val lastText = last.getCharSequence(KEY_TEXT).clean()
      val lastSender = (last.getParcelable(KEY_SENDER_PERSON, Person::class.java)?.name
        ?: last.getCharSequence(KEY_SENDER)).clean()
      val body = lastText ?: ex.getCharSequence(Notification.EXTRA_TEXT).clean()
      if (body == null && title == null) return ParseResult.Skip(ctx.getString(R.string.skip_empty))
      val isGroup = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
      val room = if (isGroup) ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).clean() ?: title else null
      val lastTime = last.getLong(KEY_TIME, 0L)
      ChatItem(
        key = sbn.key,
        pkg = sbn.packageName,
        appLabel = appLabel,
        room = room,
        sender = lastSender ?: title ?: appLabel,
        text = body.orEmpty(),
        time = sequenceOf(lastTime, n.`when`, sbn.postTime).firstOrNull { it > 0 } ?: System.currentTimeMillis(),
        count = count,
        isGroup = isGroup,
        contentIntent = n.contentIntent,
        icon = iconOf(ctx, n),
      )
    } else {
      // 일반 알림: 제목과 (펼친) 내용. 시각은 올라온 시각 (when은 일정 알림처럼 미래일 수 있어서).
      val bigTitle = ex.getCharSequence(Notification.EXTRA_TITLE_BIG).clean() ?: title
      val body = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT)).clean()
        ?: ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull().clean()
      if (body == null && bigTitle == null) return ParseResult.Skip(ctx.getString(R.string.skip_empty))
      ChatItem(
        key = sbn.key,
        pkg = sbn.packageName,
        appLabel = appLabel,
        room = null,
        sender = bigTitle ?: appLabel,
        text = body.orEmpty(),
        time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
        count = count,
        isGroup = false,
        contentIntent = n.contentIntent,
        icon = iconOf(ctx, n),
      )
    }
    return ParseResult.Ok(item)
  }

  /** 앱 이름. 못 읽으면 패키지 이름. */
  fun labelOf(ctx: Context, pkg: String): String = installedLabel(ctx, pkg) ?: pkg

  /** 설치된 앱의 이름. 안 깔린 앱이면 null (나중에 깔 수도 있으니 실패는 기억하지 않는다). */
  fun installedLabel(ctx: Context, pkg: String): String? = synchronized(labels) {
    val key = "${ctx.resources.configuration.locales[0]}|$pkg"
    labels[key] ?: runCatching {
      val pm = ctx.packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrNull()?.also { labels[key] = it }
  }

  /**
   * 원래 알림의 작은 아이콘을 비트맵으로 옮긴다. 다른 앱의 리소스 번호를 내 알림에 넘기는 대신
   * 그림 자체를 넣어서, 시스템이 그 앱 리소스를 따로 찾지 않아도 되게. 실패하면 null (톡바 아이콘).
   */
  private fun iconOf(ctx: Context, n: Notification): Icon? = runCatching {
    val drawable = n.smallIcon?.loadDrawable(ctx) ?: return@runCatching null
    val size = (24 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(24)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(Canvas(bitmap))
    Icon.createWithBitmap(bitmap)
  }.getOrNull()

  /** 알림 구조를 사람이 읽을 수 있게. 글자 값은 글자 수만 (dumpsys의 가림 방식과 같게). */
  @Suppress("DEPRECATION")
  fun describe(ctx: Context, sbn: StatusBarNotification, importance: Int?): String {
    val n = sbn.notification
    val sb = StringBuilder()
    sb.append("pkg=${sbn.packageName} id=${sbn.id}\n")
    sb.append("channel=${n.channelId} importance=${importance ?: "?"} category=${n.category} ")
    sb.append("flags=0x${Integer.toHexString(n.flags)} number=${n.number}\n")
    val ex = n.extras
    for (k in ex.keySet().sorted()) {
      val v = ex.get(k)
      val desc = when {
        v == null -> "null"
        k in SHOW_VALUE_KEYS -> v.toString()
        v is CharSequence -> "${v.javaClass.simpleName}[${ctx.resources.getQuantityString(R.plurals.chars_count, v.length, v.length)}]"
        v is Boolean || v is Int || v is Long -> v.toString()
        v is Array<*> -> "${v.javaClass.componentType?.simpleName}[${v.size}]"
        else -> v.javaClass.simpleName
      }
      sb.append("  $k = $desc\n")
    }
    return sb.toString().trimEnd()
  }

  private fun CharSequence?.clean(): String? = this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
