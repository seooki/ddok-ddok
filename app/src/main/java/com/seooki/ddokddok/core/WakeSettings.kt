package com.seooki.ddokddok.core

/** 하루 중 화면을 켜지 않을 시간대. 분 단위(0..1439)이며 23:00~07:00처럼 자정을 넘는 구간도 된다. */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
) {
    fun isActiveAt(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}

data class WakeSettings(
    val enabled: Boolean = true,
    val method: WakeMethod = WakeMethod.MENU_KEY,
    val fallbackToWakeLock: Boolean = true,
    /** AOD가 떠 있을 때는 전원 버튼 방식 대신 기본 방식을 쓴다. PIN 입력 화면이 뜨는 폰을 위한 설정이다. */
    val avoidMenuKeyWhenDozing: Boolean = false,
    val respectDnd: Boolean = true,
    val skipWhenFaceDown: Boolean = true,
    val skipWhenInPocket: Boolean = false,
    val cooldownSeconds: Int = 5,
    val quietHours: QuietHours = QuietHours(),
    val excludedPackages: Set<String> = emptySet(),
    val testResults: Map<WakeMethod, TestResult> = emptyMap(),
) {
    fun testResult(method: WakeMethod): TestResult = testResults[method] ?: TestResult.UNTESTED

    companion object {
        val COOLDOWN_CHOICES = listOf(0, 5, 15, 30, 60)
    }
}
