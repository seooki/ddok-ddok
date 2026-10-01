package com.seooki.ddokddok.data

import com.seooki.ddokddok.core.DisplayKind
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.core.WakeResult

sealed interface EventOutcome {
    data class Woke(val method: WakeMethod, val usedFallback: Boolean) : EventOutcome
    data class Skipped(val reason: SkipReason) : EventOutcome
    data class Failed(val reason: FailReason) : EventOutcome
}

/** 화면 켜기 판단 한 건. 어느 앱의 알림이었는지만 남기고 알림 내용은 남기지 않는다. */
data class WakeEvent(
    val timeMillis: Long,
    val packageName: String,
    val outcome: EventOutcome,
    /** 화면을 켜려 했을 때의 상황. 얼굴 인식이 안 될 때 원인을 가리는 데 쓴다. 판단만 하고 끝났으면 null이다. */
    val detail: WakeDetail? = null,
)

data class WakeDetail(
    /** 켜기 신호를 보낼 때 화면 상태. */
    val display: DisplayKind,
    /** 알림 팝업처럼 잠깐 밝아진 화면이 사라지기를 기다린 시간. */
    val waitedMs: Long = 0,
    /** 다른 쪽이 먼저 켠 화면을 껐다가 다시 켰는지. */
    val rewoke: Boolean = false,
)

fun WakeResult.toOutcome(): EventOutcome = when (this) {
    is WakeResult.Woke -> EventOutcome.Woke(method, usedFallback)
    is WakeResult.Failed -> EventOutcome.Failed(reason)
}
