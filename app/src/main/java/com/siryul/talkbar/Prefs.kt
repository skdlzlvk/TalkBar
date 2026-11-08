package com.siryul.talkbar

import android.content.Context

/** 앱 옵션 (켜기/끄기 스위치들)과 Now Bar에 띄울 앱 목록. */
object Prefs {
  const val KEY_ENABLED = "enabled"
  const val KEY_SHOW_CONTENT = "show_content"
  const val KEY_SKIP_MUTED = "skip_muted"
  const val KEY_WAKE_SCREEN = "wake_screen"
  const val KEY_ACCEPT_SHELL = "accept_shell"
  private const val KEY_SOURCES = "sources"

  /** 처음엔 카톡만. */
  private val DEFAULT_SOURCES = setOf(NotificationParser.KAKAO)

  private val defaults = mapOf(
    KEY_ENABLED to true,
    KEY_SHOW_CONTENT to true,
    // Glimpse의 '최소 중요도' 필터와 같은 역할. 카톡은 알림 끈 방도 무음 알림을 올린다.
    KEY_SKIP_MUTED to true,
    // 아이폰처럼 새 알림이 오면 화면을 켠다 (2026-11-08 요청).
    KEY_WAKE_SCREEN to true,
    KEY_ACCEPT_SHELL to false,
  )

  private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

  fun get(ctx: Context, key: String): Boolean = prefs(ctx).getBoolean(key, defaults[key] ?: false)

  fun set(ctx: Context, key: String, value: Boolean) {
    prefs(ctx).edit().putBoolean(key, value).apply()
  }

  fun enabled(ctx: Context) = get(ctx, KEY_ENABLED)
  fun showContent(ctx: Context) = get(ctx, KEY_SHOW_CONTENT)
  fun skipMuted(ctx: Context) = get(ctx, KEY_SKIP_MUTED)
  fun wakeScreen(ctx: Context) = get(ctx, KEY_WAKE_SCREEN)

  /** 셸(adb) 알림을 받는 앱처럼 처리. 개발 빌드에서만 의미가 있다. */
  fun acceptShell(ctx: Context) = AppInfo.isDebuggable(ctx) && get(ctx, KEY_ACCEPT_SHELL)

  /** Now Bar에 띄울 앱(패키지 이름)들. */
  fun sources(ctx: Context): Set<String> =
    prefs(ctx).getStringSet(KEY_SOURCES, null)?.toSet() ?: DEFAULT_SOURCES

  fun setSource(ctx: Context, pkg: String, enabled: Boolean) {
    val next = sources(ctx).toMutableSet().apply { if (enabled) add(pkg) else remove(pkg) }
    prefs(ctx).edit().putStringSet(KEY_SOURCES, next).apply()
  }
}
