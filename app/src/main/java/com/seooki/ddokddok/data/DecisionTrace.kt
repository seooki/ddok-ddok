package com.seooki.ddokddok.data

/**
 * 최근 판단을 '최근 기록'에 남기지 않는 것까지 메모리에만 잠깐 담아 둔다. 진단 정보에 넣어서
 * '알림이 울렸는데 화면이 안 켜짐' 같은 문제의 원인을 가리는 데 쓴다.
 * 어느 앱의 알림이었는지(패키지 이름)와 판단만 남기고 알림 내용은 남기지 않으며, 앱이 다시 시작되면 사라진다.
 * 메인 스레드에서 쓴다.
 */
class DecisionTrace(private val capacity: Int = CAPACITY) {

    data class Entry(val timeMillis: Long, val packageName: String, val decision: String)

    private val entries = ArrayDeque<Entry>(capacity)

    fun add(packageName: String, decision: String, timeMillis: Long = System.currentTimeMillis()) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(Entry(timeMillis, packageName, decision))
    }

    /** 오래된 것부터 최신 순. */
    fun snapshot(): List<Entry> = entries.toList()

    companion object {
        const val CAPACITY = 60
    }
}
