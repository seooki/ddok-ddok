package com.seooki.ddokddok.core

/** 화면을 켜지 않은 이유. [recorded]가 false인 이유는 너무 흔하거나 당연해서 기록에 남기지 않는다. */
enum class SkipReason(val recorded: Boolean) {
    DISABLED(false),
    SCREEN_ON(false),
    OWN_APP(false),
    ONGOING(false),
    GROUP_SILENT(false),
    ALERT_ONCE_UPDATE(false),
    BUSY(false),
    EXCLUDED_APP(true),
    SILENT(true),
    DND(true),
    SYSTEM_HANDLES(true),
    HIDDEN_ON_LOCKSCREEN(true),
    IN_CALL(true),
    QUIET_HOURS(true),
    COOLDOWN(true),
    FACE_DOWN(true),
    IN_POCKET(true),
}

sealed interface Decision {
    data object Wake : Decision
    data class Skip(val reason: SkipReason) : Decision
}

/**
 * 알림 하나로 화면을 켤지 정한다. 기준은 아이폰처럼 '소리나 진동이 나는 새 알림'에만 반응하는 것이다.
 * 안드로이드 API에 의존하지 않아서 단위 테스트로 검증한다.
 */
object WakePolicy {
    /** NotificationManager.IMPORTANCE_DEFAULT. 이보다 낮은 채널은 소리가 나지 않는다. */
    const val IMPORTANCE_DEFAULT = 3

    fun evaluate(
        notification: NotificationFacts,
        device: DeviceFacts,
        settings: WakeSettings,
        ownPackage: String,
        nowElapsedMs: Long,
        lastWakeElapsedMs: Long?,
        minuteOfDay: Int,
    ): Decision {
        val reason = skipReason(notification, device, settings, ownPackage, nowElapsedMs, lastWakeElapsedMs, minuteOfDay)
        return if (reason == null) Decision.Wake else Decision.Skip(reason)
    }

    /**
     * 실제로 쓸 방식. AOD가 떠 있으면 메뉴 키가 잠금화면까지 전달되고, 잠금화면이 이를 잠금 해제 요청으로 받아
     * PIN 입력 화면을 띄울 수 있다. 그런 폰이면 AOD일 때만 기본 방식을 쓴다.
     */
    fun chooseMethod(settings: WakeSettings, displayDozing: Boolean): WakeMethod =
        if (settings.method == WakeMethod.MENU_KEY && settings.avoidMenuKeyWhenDozing && displayDozing) {
            WakeMethod.WAKE_LOCK
        } else {
            settings.method
        }

    /** 센서 확인 결과로 막아야 하면 그 이유를 돌려준다. */
    fun evaluatePosture(posture: PostureFacts, settings: WakeSettings): SkipReason? = when {
        settings.skipWhenFaceDown && posture.faceDown == true -> SkipReason.FACE_DOWN
        settings.skipWhenInPocket && posture.near == true -> SkipReason.IN_POCKET
        else -> null
    }

    // 가장 흔한 경우(기능 꺼짐, 화면 켜짐)를 먼저 확인해서 알림마다 드는 비용을 줄인다.
    private fun skipReason(
        n: NotificationFacts,
        device: DeviceFacts,
        settings: WakeSettings,
        ownPackage: String,
        nowElapsedMs: Long,
        lastWakeElapsedMs: Long?,
        minuteOfDay: Int,
    ): SkipReason? = when {
        !settings.enabled -> SkipReason.DISABLED
        device.screenOn -> SkipReason.SCREEN_ON
        n.packageName == ownPackage -> SkipReason.OWN_APP
        n.isOngoing -> SkipReason.ONGOING
        n.packageName in settings.excludedPackages -> SkipReason.EXCLUDED_APP
        n.importance != null && n.importance < IMPORTANCE_DEFAULT -> SkipReason.SILENT
        settings.respectDnd && !n.passesDnd -> SkipReason.DND
        n.isGroupSummary && n.groupAlert == GroupAlert.CHILDREN -> SkipReason.GROUP_SILENT
        n.isGroupChild && n.groupAlert == GroupAlert.SUMMARY -> SkipReason.GROUP_SILENT
        n.isUpdate && n.onlyAlertOnce -> SkipReason.ALERT_ONCE_UPDATE
        n.wakesScreenItself -> SkipReason.SYSTEM_HANDLES
        n.hiddenOnLockscreen -> SkipReason.HIDDEN_ON_LOCKSCREEN
        device.inCall -> SkipReason.IN_CALL
        settings.quietHours.isActiveAt(minuteOfDay) -> SkipReason.QUIET_HOURS
        isCoolingDown(settings, nowElapsedMs, lastWakeElapsedMs) -> SkipReason.COOLDOWN
        else -> null
    }

    private fun isCoolingDown(settings: WakeSettings, nowElapsedMs: Long, lastWakeElapsedMs: Long?): Boolean =
        settings.cooldownSeconds > 0 &&
            lastWakeElapsedMs != null &&
            nowElapsedMs - lastWakeElapsedMs < settings.cooldownSeconds * 1_000L
}
