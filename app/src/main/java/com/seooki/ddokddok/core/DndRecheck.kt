package com.seooki.ddokddok.core

/**
 * 방해 금지 모드에 막혔던 알림을 잠깐 기억한다.
 *
 * '즐겨찾기 연락처 허용' 같은 예외는 시스템이 연락처 확인을 알림 게시 뒤에 마친다. 그래서 처음엔 막힌 채로
 * 리스너에 전달되고, 확인이 끝나면 막힘을 풀면서 게시 후 [windowMs] 안이면 그제야 소리를 낸다
 * (NotificationRecord.MAX_SOUND_DELAY_MS). 이때 리스너에는 순위 갱신만 오므로 여기서 다시 판단할 후보를 들고 있는다.
 */
class DndRecheck(private val windowMs: Long = WINDOW_MS) {

    private class Entry(val facts: NotificationFacts, val postedElapsedMs: Long)

    private val entries = LinkedHashMap<String, Entry>()

    fun remember(key: String, facts: NotificationFacts, nowElapsedMs: Long) {
        prune(nowElapsedMs)
        entries[key] = Entry(facts, nowElapsedMs)
    }

    /** 아직 시간 안에 있는 알림 키. */
    fun pendingKeys(nowElapsedMs: Long): List<String> {
        prune(nowElapsedMs)
        return entries.keys.toList()
    }

    /** 다시 판단할 알림 정보를 꺼낸다. 시간이 지났거나 없으면 null이다. */
    fun take(key: String, nowElapsedMs: Long): NotificationFacts? {
        prune(nowElapsedMs)
        return entries.remove(key)?.facts
    }

    fun forget(key: String) {
        entries.remove(key)
    }

    private fun prune(nowElapsedMs: Long) {
        entries.values.removeAll { nowElapsedMs - it.postedElapsedMs > windowMs }
    }

    companion object {
        const val WINDOW_MS = 2_000L
    }
}
