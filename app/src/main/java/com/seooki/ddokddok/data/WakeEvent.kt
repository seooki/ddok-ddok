package com.seooki.ddokddok.data

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
)

fun WakeResult.toOutcome(): EventOutcome = when (this) {
    is WakeResult.Woke -> EventOutcome.Woke(method, usedFallback)
    is WakeResult.Failed -> EventOutcome.Failed(reason)
}
