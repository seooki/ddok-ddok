package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignWakeTest {
    private val posted = 100_000L

    @Test
    fun `알림 게시 직후 다른 쪽이 켠 화면이다`() {
        assertTrue(ForeignWake.detect(screenOnSinceMs = posted + 250, postedMs = posted, nowMs = posted + 400, lastOwnTriggerMs = null))
        // 시계 오차로 게시보다 살짝 앞서 기록된 경우
        assertTrue(ForeignWake.detect(screenOnSinceMs = posted - 200, postedMs = posted, nowMs = posted + 300, lastOwnTriggerMs = null))
    }

    @Test
    fun `알림 전부터 켜져 있던 화면은 사람이 보고 있는 것이다`() {
        assertFalse(ForeignWake.detect(screenOnSinceMs = posted - 5_000, postedMs = posted, nowMs = posted + 300, lastOwnTriggerMs = null))
        // 앱이 시작되기 전부터 켜져 있었으면 부팅 시각(0)으로 둔다.
        assertFalse(ForeignWake.detect(screenOnSinceMs = 0, postedMs = posted, nowMs = posted + 300, lastOwnTriggerMs = null))
    }

    @Test
    fun `알림을 듣고 사람이 켤 만한 시간 뒤에 켜진 것은 아니다`() {
        assertFalse(
            ForeignWake.detect(screenOnSinceMs = posted + 2_500, postedMs = posted, nowMs = posted + 2_600, lastOwnTriggerMs = null),
        )
    }

    @Test
    fun `똑똑이 방금 켠 화면은 아니다`() {
        assertFalse(
            ForeignWake.detect(screenOnSinceMs = posted + 250, postedMs = posted, nowMs = posted + 400, lastOwnTriggerMs = posted + 100),
        )
        assertTrue(
            ForeignWake.detect(
                screenOnSinceMs = posted + 250,
                postedMs = posted,
                nowMs = posted + 400,
                lastOwnTriggerMs = posted - ForeignWake.OWN_WAKE_MS,
            ),
        )
    }
}
