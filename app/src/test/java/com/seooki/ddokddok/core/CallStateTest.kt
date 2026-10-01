package com.seooki.ddokddok.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {

    @Test
    fun `전화 수신과 통화는 통화 중이다`() {
        assertTrue(CallState.isInCall(CallState.MODE_RINGTONE) { false })
        assertTrue(CallState.isInCall(CallState.MODE_IN_CALL) { false })
        assertTrue(CallState.isInCall(CallState.MODE_CALL_SCREENING) { false })
        assertTrue(CallState.isInCall(CallState.MODE_CALL_REDIRECT) { false })
    }

    @Test
    fun `통신 모드는 통화 음성이 나올 때만 통화 중이다`() {
        assertTrue(CallState.isInCall(CallState.MODE_IN_COMMUNICATION) { true })
        assertFalse(CallState.isInCall(CallState.MODE_IN_COMMUNICATION) { false })
        assertFalse(CallState.isInCall(CallState.MODE_COMMUNICATION_REDIRECT) { false })
    }

    @Test
    fun `평소에는 재생 목록을 묻지 않는다`() {
        assertFalse(CallState.isInCall(CallState.MODE_NORMAL) { error("통신 모드가 아니면 묻지 않는다") })
        assertTrue(CallState.isInCall(CallState.MODE_IN_CALL) { error("전화 통화는 묻지 않는다") })
    }
}
