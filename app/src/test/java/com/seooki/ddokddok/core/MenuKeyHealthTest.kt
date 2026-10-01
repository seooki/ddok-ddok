package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuKeyHealthTest {

    private val noResponseThenFallback = WakeResult.Woke(WakeMethod.WAKE_LOCK, primaryFailure = FailReason.NO_RESPONSE)

    @Test
    fun `메뉴 키가 연달아 세 번 반응하지 않고 기본 방식으로 켜지면 바꾸라고 알린다`() {
        val health = MenuKeyHealth()
        assertFalse(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
        assertFalse(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
        assertTrue(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
        // 알린 뒤에는 처음부터 다시 센다.
        assertFalse(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
    }

    @Test
    fun `기본 방식으로도 켜지지 않았으면 메뉴 키 탓으로 세지 않는다`() {
        val health = MenuKeyHealth()
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        // 폰이 화면 켜기를 막았거나 대체 방식을 꺼 둔 경우. 세지도, 처음부터 다시 세지도 않는다.
        repeat(5) { assertFalse(health.record(WakeMethod.MENU_KEY, WakeResult.Failed(FailReason.NO_RESPONSE), displayWasOff = true)) }
        assertTrue(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
    }

    @Test
    fun `한 번이라도 켜지면 다시 센다`() {
        val health = MenuKeyHealth()
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        health.record(WakeMethod.MENU_KEY, WakeResult.Woke(WakeMethod.MENU_KEY), displayWasOff = true)
        assertFalse(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
    }

    @Test
    fun `접근성 꺼짐과 기본 방식 결과는 세지 않는다`() {
        val health = MenuKeyHealth()
        val accessibilityOff = WakeResult.Woke(WakeMethod.WAKE_LOCK, primaryFailure = FailReason.ACCESSIBILITY_OFF)
        repeat(5) { assertFalse(health.record(WakeMethod.MENU_KEY, accessibilityOff, displayWasOff = true)) }
        repeat(5) { assertFalse(health.record(WakeMethod.WAKE_LOCK, WakeResult.Failed(FailReason.NO_RESPONSE), displayWasOff = true)) }
    }

    @Test
    fun `AOD나 알림 팝업이 떠 있을 때의 실패는 세지 않는다`() {
        val health = MenuKeyHealth()
        repeat(5) { assertFalse(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = false)) }
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true)
        assertTrue(health.record(WakeMethod.MENU_KEY, noResponseThenFallback, displayWasOff = true))
    }
}
