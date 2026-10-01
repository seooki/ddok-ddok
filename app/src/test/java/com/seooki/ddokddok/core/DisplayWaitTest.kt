package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayWaitTest {
    private val arrival = 50_000L

    @Test
    fun `화면이 꺼져 있으면 기다리지 않는다`() {
        assertFalse(DisplayWait.shouldWait(offAtArrival = true, offNow = true, leftOffAtMs = null, arrivalMs = arrival))
        assertFalse(DisplayWait.shouldWait(offAtArrival = false, offNow = true, leftOffAtMs = arrival - 100, arrivalMs = arrival))
    }

    @Test
    fun `알림을 받은 뒤 밝아졌으면 팝업이 사라지길 기다린다`() {
        assertTrue(DisplayWait.shouldWait(offAtArrival = true, offNow = false, leftOffAtMs = arrival + 150, arrivalMs = arrival))
        // 화면 상태 변화를 받지 못하는 기기에서도 받은 순간과 지금을 견줘서 안다.
        assertTrue(DisplayWait.shouldWait(offAtArrival = true, offNow = false, leftOffAtMs = null, arrivalMs = arrival))
    }

    @Test
    fun `알림 직전에 밝아졌으면 그 알림 때문이다`() {
        assertTrue(DisplayWait.shouldWait(offAtArrival = false, offNow = false, leftOffAtMs = arrival - 300, arrivalMs = arrival))
    }

    @Test
    fun `원래 켜져 있던 AOD는 기다려도 꺼지지 않으니 기다리지 않는다`() {
        assertFalse(
            DisplayWait.shouldWait(offAtArrival = false, offNow = false, leftOffAtMs = arrival - 60_000, arrivalMs = arrival),
        )
        assertFalse(DisplayWait.shouldWait(offAtArrival = false, offNow = false, leftOffAtMs = null, arrivalMs = arrival))
    }
}
