package com.siryul.talkbar

/**
 * 지금 Now Bar에 보여줄 알림 목록. 기준은 '원래 앱의 알림이 아직 떠 있는가'.
 * 카톡에서 읽으면 카톡이 알림을 지우고, 그러면 여기서도 빠진다.
 */
object ChatStore {
  private const val TEST_PREFIX = "test:"
  const val TEST_KEY = "${TEST_PREFIX}local"

  fun testKey(room: String) = "${TEST_PREFIX}room:$room"

  fun isTest(key: String) = key.startsWith(TEST_PREFIX)

  private val items = LinkedHashMap<String, ChatItem>()

  /** 사용자가 Now Bar 알림을 지운 시점의 메시지 시각. 그보다 새 메시지가 오면 다시 보여준다. */
  private val dismissedAt = HashMap<String, Long>()

  @Volatile
  var listener: (() -> Unit)? = null

  /**
   * 넣거나 바꾼다. 새 알림(처음 보는 알림이거나, 더 최근이면서 내용이 바뀜)이면 true.
   * 같은 알림이 그대로 다시 올라온 거면 false.
   */
  @Synchronized
  fun put(item: ChatItem): Boolean {
    val old = items.put(item.key, item)
    dismissedAt[item.key]?.let { if (item.time > it) dismissedAt.remove(item.key) }
    changed()
    return old == null || (item.time > old.time && item.differsFrom(old))
  }

  @Synchronized
  fun contains(key: String): Boolean = key in items

  @Synchronized
  fun remove(key: String): Boolean {
    val removed = items.remove(key) != null
    dismissedAt.remove(key)
    if (removed) changed()
    return removed
  }

  /** 테스트 항목을 모두 지운다. 지운 게 있으면 true. */
  @Synchronized
  fun removeTests(): Boolean {
    val keys = items.keys.filter(::isTest)
    keys.forEach { items.remove(it); dismissedAt.remove(it) }
    if (keys.isNotEmpty()) changed()
    return keys.isNotEmpty()
  }

  /** 지금 떠 있는 카톡 알림 기준으로 통째로 맞춘다. 테스트 항목은 남긴다. */
  @Synchronized
  fun replaceSourceItems(newItems: List<ChatItem>) {
    val tests = items.values.filter { isTest(it.key) }
    items.clear()
    newItems.forEach { items[it.key] = it }
    tests.forEach { items[it.key] = it }
    dismissedAt.keys.retainAll(items.keys)
    changed()
  }

  @Synchronized
  fun dismissVisible() {
    items.values.forEach { dismissedAt[it.key] = it.time }
    changed()
  }

  /** 보여줄 항목, 최신 순. */
  @Synchronized
  fun visible(): List<ChatItem> =
    items.values.filter { it.key !in dismissedAt }.sortedByDescending { it.time }

  @Synchronized
  fun count(): Int = items.size

  private fun changed() {
    listener?.invoke()
  }
}
