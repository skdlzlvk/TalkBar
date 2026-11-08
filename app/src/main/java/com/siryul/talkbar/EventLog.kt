package com.siryul.talkbar

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Glimpse의 로그 버퍼처럼 무슨 일이 있었는지 앱 안에서 볼 수 있게 남기는 기록.
 * 메시지 본문은 남기지 않는다 (보낸 사람·방 이름과 글자 수만).
 */
object EventLog {
  private const val MAX_LINES = 300
  private const val FILE_NAME = "events.log"
  private val lines = ArrayDeque<String>()
  private var loaded = false
  private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT)

  @Volatile
  var listener: (() -> Unit)? = null

  @Synchronized
  fun add(ctx: Context, message: String) {
    load(ctx)
    val clean = message.replace('\n', ' ')
    lines.addLast("${timeFormat.format(Date())}  $clean")
    while (lines.size > MAX_LINES) lines.removeFirst()
    save(ctx)
    Log.i("TalkBar", clean)
    listener?.invoke()
  }

  @Synchronized
  fun all(ctx: Context): List<String> {
    load(ctx)
    return lines.toList()
  }

  @Synchronized
  fun clear(ctx: Context) {
    loaded = true
    lines.clear()
    save(ctx)
    listener?.invoke()
  }

  private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

  private fun load(ctx: Context) {
    if (loaded) return
    loaded = true
    val f = file(ctx)
    if (f.exists()) f.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) }
  }

  private fun save(ctx: Context) {
    runCatching { file(ctx).writeText(lines.joinToString("\n")) }
  }
}
