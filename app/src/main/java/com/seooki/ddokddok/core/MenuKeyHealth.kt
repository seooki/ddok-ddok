package com.seooki.ddokddok.core

/**
 * 전원 버튼 방식이 연달아 화면을 켜지 못하는지 센다. OS 업데이트 등으로 메뉴 키가 막히면 알림마다
 * 대체 방식으로 늦게 켜지므로, [threshold]번 연속이면 기본 방식으로 바꾸도록 알린다.
 *
 * 메뉴 키는 반응이 없는데 기본 방식으로는 켜진 경우만 센다. 둘 다 켜지지 않았으면 폰이 화면 켜기를 막은 것이라
 * 메뉴 키 탓이 아니고, 대체 방식을 꺼 두었으면 비교할 근거가 없다. 접근성 서비스가 꺼진 경우는 따로 안내한다.
 */
class MenuKeyHealth(private val threshold: Int = THRESHOLD) {
    private var consecutiveNoResponse = 0

    /** [attempted]로 켠 결과를 기록한다. 기본 방식으로 바꿔야 할 때 true를 돌려준다. */
    fun record(attempted: WakeMethod, result: WakeResult): Boolean {
        if (attempted != WakeMethod.MENU_KEY || result !is WakeResult.Woke) return false
        when (result.primaryFailure) {
            null -> consecutiveNoResponse = 0
            FailReason.NO_RESPONSE -> consecutiveNoResponse++
            else -> return false
        }
        if (consecutiveNoResponse < threshold) return false
        consecutiveNoResponse = 0
        return true
    }

    companion object {
        const val THRESHOLD = 3
    }
}
