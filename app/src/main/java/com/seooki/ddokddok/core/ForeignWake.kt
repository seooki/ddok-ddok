package com.seooki.ddokddok.core

/**
 * 알림과 함께 다른 쪽이 화면을 먼저 켰는지 가린다. 삼성의 '화면이 꺼져 있을 때에도 알림 팝업 보이기'나
 * 알림을 받으면 스스로 화면을 켜는 앱이 그렇다. 이렇게 켜진 화면은 '앱이 켬'으로 기록되어 얼굴 인식이 시작되지 않는다.
 *
 * 시각은 모두 부팅 후 시간(elapsedRealtime)이다.
 */
object ForeignWake {
    /** 게시 시각과 화면이 켜진 시각을 견줄 때 허용하는 오차. */
    const val EARLY_MARGIN_MS = 300L

    /** 게시 뒤 이 시간 안에 켜진 것만 알림 때문으로 본다. 사람이 알림을 듣고 직접 켜기엔 짧은 시간이다. */
    const val WINDOW_MS = 1_500L

    /** 똑똑이 켜기 신호를 보낸 직후에 켜진 화면은 똑똑이 켠 것이다. */
    const val OWN_WAKE_MS = 3_000L

    /**
     * [screenOnSinceMs]는 지금 켜져 있는 화면이 켜진 시각, [postedMs]는 알림이 게시된 시각,
     * [lastOwnTriggerMs]는 똑똑이 마지막으로 켜기 신호를 보낸 시각이다.
     */
    fun detect(screenOnSinceMs: Long, postedMs: Long, nowMs: Long, lastOwnTriggerMs: Long?): Boolean {
        if (lastOwnTriggerMs != null && nowMs - lastOwnTriggerMs < OWN_WAKE_MS) return false
        val sincePost = screenOnSinceMs - postedMs
        return sincePost >= -EARLY_MARGIN_MS && sincePost <= WINDOW_MS
    }
}
