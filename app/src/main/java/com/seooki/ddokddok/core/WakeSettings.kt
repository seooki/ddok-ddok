package com.seooki.ddokddok.core

/** 하루 중 화면을 켜지 않을 시간대. 분 단위(0..1439)이며 23:00~07:00처럼 자정을 넘는 구간도 된다. */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinute: Int = 23 * 60,
    val endMinute: Int = 7 * 60,
) {
    /** 켜져 있고 구간 길이가 0이 아니어야 실제로 적용된다. */
    val isEffective: Boolean get() = enabled && startMinute != endMinute

    fun isActiveAt(minuteOfDay: Int): Boolean {
        if (!isEffective) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}

data class WakeSettings(
    val enabled: Boolean = true,
    /** 이 시각(epoch ms)까지 잠시 쉰다. 0이면 쉬지 않는다. */
    val snoozeUntilEpochMs: Long = 0,
    val method: WakeMethod = WakeMethod.MENU_KEY,
    val fallbackToWakeLock: Boolean = true,
    /** AOD가 떠 있을 때는 전원 버튼 방식 대신 기본 방식을 쓴다. PIN 입력 화면이 뜨는 폰을 위한 설정이다. */
    val avoidMenuKeyWhenDozing: Boolean = false,
    /**
     * 알림과 함께 다른 기능(삼성 알림 팝업 등)이 화면을 먼저 켜면, 껐다가 전원 버튼 방식으로 다시 켠다.
     * 그렇게 켜진 화면은 얼굴 인식이 시작되지 않기 때문이다.
     */
    val rewakeWhenOthersWake: Boolean = true,
    val respectDnd: Boolean = true,
    val skipWhenFaceDown: Boolean = true,
    val skipWhenInPocket: Boolean = false,
    val cooldownSeconds: Int = 5,
    /** 같은 앱 알림은 이 시간 안에 한 번만 켠다. 단톡방처럼 몰아서 오는 알림용. 0이면 쓰지 않는다. */
    val perAppCooldownSeconds: Int = 0,
    /** 알림으로 켠 화면을 이 시간 뒤 끈다. 0이면 시스템에 맡긴다(전원 버튼으로 켰을 때와 같음). */
    val autoOffSeconds: Int = 0,
    val quietHours: QuietHours = QuietHours(),
    val excludedPackages: Set<String> = emptySet(),
    val testResults: Map<WakeMethod, TestResult> = emptyMap(),
) {
    fun testResult(method: WakeMethod): TestResult = testResults[method] ?: TestResult.UNTESTED

    fun isSnoozed(nowEpochMs: Long): Boolean = nowEpochMs < snoozeUntilEpochMs

    companion object {
        val COOLDOWN_CHOICES = listOf(0, 5, 15, 30, 60)
        val PER_APP_COOLDOWN_CHOICES = listOf(0, 30, 60, 300)
        val AUTO_OFF_CHOICES = listOf(0, 5, 10, 15, 30)
        const val SNOOZE_MS = 60 * 60 * 1_000L
    }
}
