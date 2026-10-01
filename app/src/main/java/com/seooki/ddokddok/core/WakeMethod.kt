package com.seooki.ddokddok.core

/** 화면을 켜는 방식. [key]는 설정 저장에 쓰므로 바꾸면 안 된다. */
enum class WakeMethod(val key: String) {
    /**
     * 접근성 서비스로 메뉴 키 신호를 보낸다(Android 16+). 화면이 꺼져 있으면 시스템이 이를
     * '키를 눌러 켬'으로 기록해서, 전원 버튼으로 켰을 때처럼 얼굴 인식이 바로 시작된다.
     */
    MENU_KEY("menu_key"),

    /** WakeLock으로 켠다. 대부분의 폰에서 켜지지만 '앱이 켬'으로 기록돼 얼굴 인식은 시작되지 않는다. */
    WAKE_LOCK("wake_lock");

    companion object {
        fun fromKey(key: String?): WakeMethod? = entries.firstOrNull { it.key == key }
    }
}

/** 화면 켜기 테스트에서 사용자가 직접 확인한 결과. [key]는 설정 저장에 쓴다. */
enum class TestResult(val key: String) {
    UNTESTED("untested"),
    FACE_UNLOCK_WORKED("face_unlock"),
    SCREEN_ONLY("screen_only"),
    DID_NOT_WAKE("no_wake"),

    /** 화면은 켜졌지만 잠금화면 대신 PIN 입력 화면이 떴다. 메뉴 키가 잠금화면까지 전달된 경우다. */
    PIN_SCREEN("pin_screen");

    companion object {
        fun fromKey(key: String?): TestResult = entries.firstOrNull { it.key == key } ?: UNTESTED
    }
}

enum class FailReason { ACCESSIBILITY_OFF, UNSUPPORTED, REJECTED, NO_RESPONSE }

sealed interface WakeResult {
    /** [primaryFailure]가 있으면 처음 방식이 실패해서 [method](기본 방식)로 대신 켠 것이다. */
    data class Woke(val method: WakeMethod, val primaryFailure: FailReason? = null) : WakeResult {
        val usedFallback: Boolean get() = primaryFailure != null
    }

    data class Failed(val reason: FailReason) : WakeResult
}
