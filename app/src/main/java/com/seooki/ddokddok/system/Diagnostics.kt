package com.seooki.ddokddok.system

import android.content.Context
import android.os.Build
import com.seooki.ddokddok.BuildConfig
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.data.WakeEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 문제가 생겼을 때 붙여 넣을 진단 정보. 기기·권한·설정·최근 기록을 담고, 알림 내용은 담지 않는다.
 * 기록에는 어느 앱의 알림이었는지(패키지 이름)가 들어간다.
 */
object Diagnostics {

    fun report(context: Context): String {
        val graph = context.appGraph
        val settings = graph.settings.current
        val status = SystemSetup.status(context)
        val events = graph.events.events.value
        val now = System.currentTimeMillis()
        return buildString {
            appendLine("똑똑 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(
                "기기: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                    " / ${oneUiVersion() ?: "One UI 아님"} / ${Build.DISPLAY}",
            )
            appendLine(
                "권한: 알림접근=${yn(status.notificationAccess)} 접근성=${yn(status.accessibilityEnabled)}" +
                    "(연결 ${yn(status.accessibilityConnected)}) 배터리제한없음=${yn(status.batteryUnrestricted)}" +
                    " 알림허용=${yn(status.notificationsAllowed)}",
            )
            appendLine(
                "방식: ${settings.method.key} 대체=${yn(settings.fallbackToWakeLock)}" +
                    " AOD예외=${yn(settings.avoidMenuKeyWhenDozing)}" +
                    " 테스트=${settings.testResults.entries.joinToString(",") { "${it.key.key}:${it.value.key}" }}",
            )
            appendLine(
                "조건: 켜짐=${yn(settings.enabled)} 쉬는중=${yn(settings.isSnoozed(now))}" +
                    " 방해금지=${yn(settings.respectDnd)} 엎어둠=${yn(settings.skipWhenFaceDown)}" +
                    " 주머니=${yn(settings.skipWhenInPocket)} 간격=${settings.cooldownSeconds}s" +
                    " 앱간격=${settings.perAppCooldownSeconds}s 자동끄기=${settings.autoOffSeconds}s" +
                    " 조용한시간=${if (settings.quietHours.isEffective) "${settings.quietHours.startMinute}-${settings.quietHours.endMinute}" else "없음"}" +
                    " 제외앱=${settings.excludedPackages.size}",
            )
            appendLine("기록 ${events.size}건: ${summarize(events)}")
            appendLine("최근 기록(최신순):")
            events.takeLast(RECENT).asReversed().forEach { appendLine("  ${time(it.timeMillis)} ${it.packageName} ${code(it.outcome)}") }
        }
    }

    private fun summarize(events: List<WakeEvent>): String {
        val counts = events.groupingBy { code(it.outcome) }.eachCount()
        return counts.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key}=${it.value}" }
    }

    private fun code(outcome: EventOutcome): String = when (outcome) {
        is EventOutcome.Woke -> "켬:${outcome.method.key}${if (outcome.usedFallback) "(대체)" else ""}"
        is EventOutcome.Skipped -> "안켬:${outcome.reason.name}"
        is EventOutcome.Failed -> "실패:${outcome.reason.name}"
    }

    /** 삼성 기기에만 있는 One UI 버전 값(예: 170500 → One UI 8.5). */
    private fun oneUiVersion(): String? = runCatching {
        val sem = Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) - 90_000
        "One UI ${sem / 10_000}.${sem % 10_000 / 100}"
    }.getOrNull()

    private fun time(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

    private fun yn(value: Boolean) = if (value) "예" else "아니요"

    private const val RECENT = 30
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
}
