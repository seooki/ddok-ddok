package com.seooki.ddokddok.core

/** 화면을 켜지 않은 이유. [recorded]가 false인 이유는 너무 흔하거나 당연해서 기록에 남기지 않는다. */
enum class SkipReason(val recorded: Boolean) {
    DISABLED(false),
    SNOOZED(false),
    SCREEN_ON(false),
    OWN_APP(false),
    ONGOING(false),
    REPOST(false),
    GROUP_SILENT(false),
    ALERT_ONCE_UPDATE(false),
    BUSY(false),
    EXCLUDED_APP(true),
    SUSPENDED_APP(true),
    SILENT(true),

    /** 소리 없는 알림의 업데이트. 진행률처럼 자주 바뀌어서 기록하면 다른 기록을 밀어낸다. */
    SILENT_UPDATE(false),
    DND(true),
    SYSTEM_HANDLES(true),
    HIDDEN_ON_LOCKSCREEN(true),
    IN_CALL(true),
    QUIET_HOURS(true),
    COOLDOWN(true),
    APP_COOLDOWN(true),

    /** 알림과 함께 다른 기능(삼성 알림 팝업 등)이 화면을 먼저 켰고, 다시 켜지 않았다. */
    WOKEN_BY_OTHER(true),
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
        timing: WakeTiming,
    ): Decision {
        val reason = skipReason(notification, device, settings, ownPackage, timing)
        return if (reason == null) Decision.Wake else Decision.Skip(reason)
    }

    /**
     * 실제로 쓸 방식. 화면이 완전히 꺼지지 않은 상태(AOD 등)에서는 메뉴 키가 잠금화면까지 전달되고,
     * 잠금화면이 이를 잠금 해제 요청으로 받아 PIN 입력 화면을 띄울 수 있다. 그런 폰이면 그때만 기본 방식을 쓴다.
     */
    fun chooseMethod(settings: WakeSettings, keyReachesLockScreen: Boolean): WakeMethod =
        if (settings.method == WakeMethod.MENU_KEY && settings.avoidMenuKeyWhenDozing && keyReachesLockScreen) {
            WakeMethod.WAKE_LOCK
        } else {
            settings.method
        }

    /** 센서 확인 결과로 막아야 하면 그 이유를 돌려준다. */
    fun evaluatePosture(posture: PostureFacts, settings: WakeSettings): SkipReason? = when {
        settings.skipWhenFaceDown && posture.faceDown == true -> SkipReason.FACE_DOWN
        settings.skipWhenInPocket && posture.looksInPocket -> SkipReason.IN_POCKET
        else -> null
    }

    // 가장 흔한 경우(기능 꺼짐, 화면 켜짐)를 먼저 확인해서 알림마다 드는 비용을 줄인다.
    private fun skipReason(
        n: NotificationFacts,
        device: DeviceFacts,
        settings: WakeSettings,
        ownPackage: String,
        timing: WakeTiming,
    ): SkipReason? = when {
        !settings.enabled -> SkipReason.DISABLED
        settings.isSnoozed(timing.nowEpochMs) -> SkipReason.SNOOZED
        device.screenOn -> SkipReason.SCREEN_ON
        n.packageName == ownPackage -> SkipReason.OWN_APP
        n.isOngoing -> SkipReason.ONGOING
        n.isRepost -> SkipReason.REPOST
        n.packageName in settings.excludedPackages -> SkipReason.EXCLUDED_APP
        n.appSuspended -> SkipReason.SUSPENDED_APP
        n.silentFlag || (n.importance != null && n.importance < IMPORTANCE_DEFAULT) ->
            if (n.isUpdate) SkipReason.SILENT_UPDATE else SkipReason.SILENT
        settings.respectDnd && !n.passesDnd -> SkipReason.DND
        n.isGroupSummary && n.groupAlert == GroupAlert.CHILDREN -> SkipReason.GROUP_SILENT
        n.isGroupChild && n.groupAlert == GroupAlert.SUMMARY -> SkipReason.GROUP_SILENT
        n.isUpdate && n.onlyAlertOnce -> SkipReason.ALERT_ONCE_UPDATE
        n.wakesScreenItself -> SkipReason.SYSTEM_HANDLES
        n.hiddenOnLockscreen -> SkipReason.HIDDEN_ON_LOCKSCREEN
        device.inCall -> SkipReason.IN_CALL
        settings.quietHours.isActiveAt(timing.minuteOfDay) -> SkipReason.QUIET_HOURS
        isWithin(timing.nowElapsedMs, timing.lastWakeElapsedMs, settings.cooldownSeconds) -> SkipReason.COOLDOWN
        isWithin(timing.nowElapsedMs, timing.lastAppWakeElapsedMs, settings.perAppCooldownSeconds) ->
            SkipReason.APP_COOLDOWN
        else -> null
    }

    private fun isWithin(nowElapsedMs: Long, lastElapsedMs: Long?, seconds: Int): Boolean =
        seconds > 0 && lastElapsedMs != null && nowElapsedMs - lastElapsedMs < seconds * 1_000L
}
