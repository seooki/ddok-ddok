package com.seooki.ddokddok.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DndRecheckTest {

    private val facts = NotificationFacts(packageName = "com.kakao.talk", importance = 4, passesDnd = false)

    @Test
    fun `2초 안에 풀린 알림은 다시 판단할 수 있다`() {
        val recheck = DndRecheck()
        recheck.remember("k1", facts, nowElapsedMs = 1_000)
        assertEquals(listOf("k1"), recheck.pendingKeys(nowElapsedMs = 2_500))
        assertEquals(facts, recheck.take("k1", nowElapsedMs = 3_000))
        assertNull(recheck.take("k1", nowElapsedMs = 3_000))
    }

    @Test
    fun `2초가 지나면 버린다`() {
        val recheck = DndRecheck()
        recheck.remember("k1", facts, nowElapsedMs = 1_000)
        assertEquals(emptyList<String>(), recheck.pendingKeys(nowElapsedMs = 3_001))
        assertNull(recheck.take("k1", nowElapsedMs = 3_001))
    }

    @Test
    fun `알림이 지워지면 잊는다`() {
        val recheck = DndRecheck()
        recheck.remember("k1", facts, nowElapsedMs = 1_000)
        recheck.forget("k1")
        assertNull(recheck.take("k1", nowElapsedMs = 1_500))
    }
}
