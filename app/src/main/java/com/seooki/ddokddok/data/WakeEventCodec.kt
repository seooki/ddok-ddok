package com.seooki.ddokddok.data

import com.seooki.ddokddok.core.DisplayKind
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.WakeMethod

/**
 * 기록 한 건을 탭으로 나눈 한 줄로 바꾼다.
 * 시각, 패키지, 결과(W/S/F), 방식 또는 이유, 대체 여부까지가 0.1.2 형식이고, 0.1.3부터 화면 상태, 기다린 시간,
 * 다시 켰는지를 덧붙인다. 앞의 형식으로 저장된 줄도 그대로 읽는다.
 */
object WakeEventCodec {

    fun encode(event: WakeEvent): String {
        val outcome = when (val o = event.outcome) {
            is EventOutcome.Woke -> "W\t${o.method.key}\t${if (o.usedFallback) 1 else 0}"
            is EventOutcome.Skipped -> "S\t${o.reason.name}\t0"
            is EventOutcome.Failed -> "F\t${o.reason.name}\t0"
        }
        val detail = event.detail?.let { "\t${it.display.key}\t${it.waitedMs}\t${if (it.rewoke) 1 else 0}" }.orEmpty()
        return "${event.timeMillis}\t${event.packageName}\t$outcome$detail"
    }

    fun decode(line: String): WakeEvent? {
        val parts = line.split('\t')
        if (parts.size != BASE_FIELDS && parts.size != DETAIL_FIELDS) return null
        val time = parts[0].toLongOrNull() ?: return null
        val outcome = when (parts[2]) {
            "W" -> WakeMethod.fromKey(parts[3])?.let { EventOutcome.Woke(it, parts[4] == "1") }
            "S" -> SkipReason.entries.firstOrNull { it.name == parts[3] }?.let { EventOutcome.Skipped(it) }
            "F" -> FailReason.entries.firstOrNull { it.name == parts[3] }?.let { EventOutcome.Failed(it) }
            else -> null
        } ?: return null
        val detail = if (parts.size == DETAIL_FIELDS) {
            WakeDetail(
                display = DisplayKind.fromKey(parts[5]) ?: DisplayKind.UNKNOWN,
                waitedMs = parts[6].toLongOrNull() ?: 0,
                rewoke = parts[7] == "1",
            )
        } else {
            null
        }
        return WakeEvent(time, parts[1], outcome, detail)
    }

    private const val BASE_FIELDS = 5
    private const val DETAIL_FIELDS = 8
}
