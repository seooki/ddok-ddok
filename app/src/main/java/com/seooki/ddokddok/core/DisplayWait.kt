package com.seooki.ddokddok.core

/** 켜기 신호를 보낼 때 화면 상태. */
enum class DisplayKind(val key: String) {
    OFF("off"),

    /** AOD(도즈). 삼성 알림 팝업·엣지 라이팅도 화면이 꺼진 채로 여기서 그린다. */
    DOZE("doze"),

    /** 꺼지는 중이거나, 화면이 꺼진 상태로 밝게 무언가를 보여 주는 중. */
    ON("on"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromKey(key: String?): DisplayKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 화면이 완전히 꺼져 있지 않을 때(AOD, 삼성 알림 팝업·엣지 라이팅) 메뉴 키를 보내면 잠금화면이나 팝업 쪽으로
 * 전달되어 전원 버튼처럼 켜지지 않을 수 있다. 알림 때문에 방금 밝아진 것이면 꺼질 때까지 잠깐 기다렸다가 켠다.
 * AOD를 늘 켜 두어 원래 밝던 것이면 기다려도 꺼지지 않으니 바로 켠다.
 *
 * 시각은 모두 부팅 후 시간(elapsedRealtime)이다.
 */
object DisplayWait {
    /** 알림을 받기 전 이 시간 안에 밝아졌으면 그 알림 때문으로 본다. */
    const val RECENT_MS = 2_000L

    /**
     * 기다리는 최대 시간. 짧은 팝업·엣지 라이팅은 이 안에 사라진다. 그보다 오래 떠 있는 AOD는 기다려도 소용없고,
     * 그동안 화면이 켜지지 않은 것처럼 보여서 길게 잡지 않는다.
     */
    const val MAX_WAIT_MS = 3_000L

    /**
     * [offAtArrival]은 알림을 받았을 때 화면이 완전히 꺼져 있었는지, [offNow]는 지금 꺼져 있는지,
     * [leftOffAtMs]는 화면이 마지막으로 꺼짐에서 벗어난 시각(모르면 null)이다.
     */
    fun shouldWait(offAtArrival: Boolean, offNow: Boolean, leftOffAtMs: Long?, arrivalMs: Long): Boolean = when {
        offNow -> false
        offAtArrival -> true
        else -> leftOffAtMs != null && arrivalMs - leftOffAtMs <= RECENT_MS
    }
}

/**
 * 알림 뒤에 밝아진 화면(AOD)이 몇 초 안에 다시 꺼지는 폰인지 배운다. AOD를 늘 켜 두거나 새 알림이 있는 동안
 * 계속 띄우는 설정이면 기다려도 꺼지지 않으니, 연달아 그러면 기다리지 않는다. 설정을 바꿨을 수 있어서
 * 가끔은 다시 기다려 본다. 메인 스레드에서 쓴다.
 */
class AodWaitLearner {
    private var timeoutsInRow = 0
    private var skipsSinceProbe = 0

    /** 이번에 기다려 볼지. 기다리지 않기로 배운 뒤에도 [PROBE_EVERY]번에 한 번은 다시 기다려 본다. */
    fun shouldWait(): Boolean {
        if (timeoutsInRow < LEARN_AFTER) return true
        if (skipsSinceProbe >= PROBE_EVERY) {
            skipsSinceProbe = 0
            return true
        }
        skipsSinceProbe++
        return false
    }

    /** 기다린 결과. [wentOff]는 시간 안에 화면이 완전히 꺼졌는지다. */
    fun record(wentOff: Boolean) {
        timeoutsInRow = if (wentOff) 0 else timeoutsInRow + 1
        if (wentOff) skipsSinceProbe = 0
    }

    /** 진단 정보에 넣을 상태. */
    val summary: String get() = "연속미꺼짐=$timeoutsInRow 건너뜀=$skipsSinceProbe"

    companion object {
        const val LEARN_AFTER = 2
        const val PROBE_EVERY = 10
    }
}
