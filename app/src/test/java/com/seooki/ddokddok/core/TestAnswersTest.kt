package com.seooki.ddokddok.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAnswersTest {

    private val menuKey = WakeSettings(method = WakeMethod.MENU_KEY)
    private val faceVerified = menuKey.copy(testResults = mapOf(WakeMethod.MENU_KEY to TestResult.FACE_UNLOCK_WORKED))

    @Test
    fun `얼굴 인식이 되면 그 방식을 쓴다`() {
        val (next, followUp) = TestAnswers.apply(
            menuKey.copy(method = WakeMethod.WAKE_LOCK),
            WakeMethod.MENU_KEY,
            TestResult.FACE_UNLOCK_WORKED,
            displayWasOn = false,
        )
        assertEquals(WakeMethod.MENU_KEY, next.method)
        assertEquals(TestResult.FACE_UNLOCK_WORKED, next.testResult(WakeMethod.MENU_KEY))
        assertEquals(TestFollowUp.METHOD_CONFIRMED, followUp)
    }

    @Test
    fun `화면이 꺼진 상태에서 전원 버튼 방식으로 안 켜지면 기본 방식으로 바꾼다`() {
        val (next, followUp) = TestAnswers.apply(menuKey, WakeMethod.MENU_KEY, TestResult.DID_NOT_WAKE, displayWasOn = false)
        assertEquals(WakeMethod.WAKE_LOCK, next.method)
        assertEquals(TestResult.DID_NOT_WAKE, next.testResult(WakeMethod.MENU_KEY))
        assertEquals(TestFollowUp.SWITCHED_TO_WAKE_LOCK, followUp)
    }

    @Test
    fun `AOD에서 PIN 화면이 뜨면 AOD일 때만 피하고 이전 결과는 지킨다`() {
        val (next, followUp) = TestAnswers.apply(faceVerified, WakeMethod.MENU_KEY, TestResult.PIN_SCREEN, displayWasOn = true)
        assertTrue(next.avoidMenuKeyWhenDozing)
        assertEquals(WakeMethod.MENU_KEY, next.method)
        assertEquals(TestResult.FACE_UNLOCK_WORKED, next.testResult(WakeMethod.MENU_KEY))
        assertEquals(TestFollowUp.AVOID_MENU_KEY_WHEN_DOZING, followUp)
    }

    @Test
    fun `AOD에서 안 켜졌을 때도 AOD일 때만 피하고 이전 결과는 지킨다`() {
        val (next, followUp) = TestAnswers.apply(faceVerified, WakeMethod.MENU_KEY, TestResult.DID_NOT_WAKE, displayWasOn = true)
        assertTrue(next.avoidMenuKeyWhenDozing)
        assertEquals(WakeMethod.MENU_KEY, next.method)
        assertEquals(TestResult.FACE_UNLOCK_WORKED, next.testResult(WakeMethod.MENU_KEY))
        assertEquals(TestFollowUp.AVOID_MENU_KEY_WHEN_DOZING, followUp)
    }

    @Test
    fun `화면이 꺼진 상태에서도 PIN 화면이 뜨면 전원 버튼 방식을 쓰지 않는다`() {
        val (next, followUp) = TestAnswers.apply(menuKey, WakeMethod.MENU_KEY, TestResult.PIN_SCREEN, displayWasOn = false)
        assertEquals(WakeMethod.WAKE_LOCK, next.method)
        assertEquals(TestFollowUp.SWITCHED_TO_WAKE_LOCK, followUp)
    }

    @Test
    fun `AOD에서도 문제없이 얼굴 인식이 되면 AOD 예외를 푼다`() {
        val avoiding = menuKey.copy(avoidMenuKeyWhenDozing = true)
        val (next, _) = TestAnswers.apply(avoiding, WakeMethod.MENU_KEY, TestResult.FACE_UNLOCK_WORKED, displayWasOn = true)
        assertFalse(next.avoidMenuKeyWhenDozing)
    }

    @Test
    fun `화면만 켜진 결과는 기록만 하고 방식은 그대로 둔다`() {
        val (next, followUp) = TestAnswers.apply(menuKey, WakeMethod.MENU_KEY, TestResult.SCREEN_ONLY, displayWasOn = false)
        assertEquals(WakeMethod.MENU_KEY, next.method)
        assertEquals(TestResult.SCREEN_ONLY, next.testResult(WakeMethod.MENU_KEY))
        assertNull(followUp)
    }
}
