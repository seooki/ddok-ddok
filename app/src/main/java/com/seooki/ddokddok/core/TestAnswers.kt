package com.seooki.ddokddok.core

/** 테스트 답변을 반영하면서 앱이 스스로 바꾼 것. 사용자에게 알려 준다. */
enum class TestFollowUp {
    /** 얼굴 인식이 확인된 방식을 쓰기로 했다. */
    METHOD_CONFIRMED,

    /** 전원 버튼 방식이 이 폰에서 쓸 수 없어 기본 방식으로 바꿨다. */
    SWITCHED_TO_WAKE_LOCK,

    /** AOD에서 PIN 입력 화면이 떠서, AOD일 때만 기본 방식을 쓰기로 했다. */
    AVOID_MENU_KEY_WHEN_DOZING,
}

object TestAnswers {

    /**
     * 화면 켜기 테스트의 답을 설정에 반영한다.
     *
     * PIN 입력 화면은 메뉴 키가 잠금화면까지 전달될 때만 뜬다. 화면이 완전히 꺼져 있으면 시스템이 키를 삼키므로
     * AOD 상태에서 뜬 경우에는 AOD일 때만 기본 방식을 쓰고, 화면이 꺼진 상태의 결과는 덮어쓰지 않는다.
     */
    fun apply(
        settings: WakeSettings,
        method: WakeMethod,
        result: TestResult,
        wasDozing: Boolean,
    ): Pair<WakeSettings, TestFollowUp?> {
        val results = settings.testResults + (method to result)
        val menuKey = method == WakeMethod.MENU_KEY
        return when {
            result == TestResult.FACE_UNLOCK_WORKED -> {
                // AOD에서도 PIN 화면 없이 얼굴 인식이 됐다면 AOD 예외를 풀어 준다.
                val avoid = if (menuKey && wasDozing) false else settings.avoidMenuKeyWhenDozing
                settings.copy(testResults = results, method = method, avoidMenuKeyWhenDozing = avoid) to
                    TestFollowUp.METHOD_CONFIRMED
            }
            menuKey && result == TestResult.PIN_SCREEN && wasDozing ->
                settings.copy(avoidMenuKeyWhenDozing = true) to TestFollowUp.AVOID_MENU_KEY_WHEN_DOZING
            menuKey && (result == TestResult.DID_NOT_WAKE || result == TestResult.PIN_SCREEN) ->
                settings.copy(testResults = results, method = WakeMethod.WAKE_LOCK) to TestFollowUp.SWITCHED_TO_WAKE_LOCK
            else -> settings.copy(testResults = results) to null
        }
    }
}
