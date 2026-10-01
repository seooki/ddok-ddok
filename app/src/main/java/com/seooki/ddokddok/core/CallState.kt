package com.seooki.ddokddok.core

/**
 * 통화 중인지 가린다. 오디오 모드만 보면 음성 채팅·회의 앱이나 게임이 백그라운드에서 통신 모드를 잡고 있을 때도
 * 통화 중이 되어, 그동안 모든 알림에 화면을 켜지 않게 된다. 그래서 통신 모드는 실제로 통화 음성이 재생되고 있을
 * 때만 통화로 본다. 전화 수신·통화·통화 선별 모드는 그대로 통화다.
 */
object CallState {
    // AudioManager.MODE_* 값. 판단 규칙을 안드로이드 API 없이 단위 테스트하려고 숫자로 둔다.
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3
    const val MODE_CALL_SCREENING = 4
    const val MODE_CALL_REDIRECT = 5
    const val MODE_COMMUNICATION_REDIRECT = 6

    /** [voiceCallAudioPlaying]은 통신 모드일 때만 묻는다(재생 중인 오디오 목록을 읽어야 해서). */
    fun isInCall(audioMode: Int, voiceCallAudioPlaying: () -> Boolean): Boolean = when (audioMode) {
        MODE_RINGTONE, MODE_IN_CALL, MODE_CALL_SCREENING, MODE_CALL_REDIRECT -> true
        MODE_IN_COMMUNICATION, MODE_COMMUNICATION_REDIRECT -> voiceCallAudioPlaying()
        else -> false
    }
}
