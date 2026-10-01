package com.seooki.ddokddok.core

/** 알림 묶음에서 어느 쪽이 소리를 내는지. Notification.GROUP_ALERT_*와 같은 뜻이다. */
enum class GroupAlert { ALL, SUMMARY, CHILDREN }

/** 판단에 필요한 알림 정보. 제목·본문 같은 알림 내용은 일부러 담지 않는다. */
data class NotificationFacts(
    val packageName: String,
    val isOngoing: Boolean = false,
    val isGroupSummary: Boolean = false,
    val isGroupChild: Boolean = false,
    val groupAlert: GroupAlert = GroupAlert.ALL,
    val onlyAlertOnce: Boolean = false,
    val isUpdate: Boolean = false,
    /** 시스템이 이미 있던 알림을 다시 보낸 것([Repost]). 소리를 내지 않는다. */
    val isRepost: Boolean = false,
    /** 게시된 뒤 똑똑이 받기까지 걸린 시간. 보통 1초 안쪽이고, 똑똑이 잠시 멈춰 있었으면 길어진다. */
    val postAgeMs: Long = 0,
    /** 채널 중요도(NotificationManager.IMPORTANCE_*). 알 수 없으면 null. */
    val importance: Int? = null,
    /** Android 16부터 시스템이 무음 알림(setSilent)에 붙이는 표시. */
    val silentFlag: Boolean = false,
    /** 사용이 일시정지된 앱(앱 타이머 등)의 알림. 시스템이 소리도 내지 않고 잠금화면에도 보여 주지 않는다. */
    val appSuspended: Boolean = false,
    /** 방해 금지 모드를 통과했는지. 방해 금지가 꺼져 있으면 항상 true다. */
    val passesDnd: Boolean = true,
    /** 전화·알람처럼 시스템이 직접 화면을 켜는 알림인지. */
    val wakesScreenItself: Boolean = false,
    val hiddenOnLockscreen: Boolean = false,
)

data class DeviceFacts(
    val screenOn: Boolean,
    val inCall: Boolean,
)

/** 센서로 확인한 자세. 센서가 없거나 응답이 없으면 null로 두고, 그때는 막지 않는다. */
data class PostureFacts(
    val faceDown: Boolean? = null,
    val near: Boolean? = null,
    /** 주변이 아주 어두운지(조도 센서). */
    val dark: Boolean? = null,
    /** 바닥에 눕혀 놓은 자세인지(가속도 센서). 주머니 속이면 대개 세워져 있다. */
    val flat: Boolean? = null,
) {
    /** 근접 센서가 가까움을 알리거나, 어두운데 세워져 있으면 주머니·가방 속으로 본다. */
    val looksInPocket: Boolean get() = near == true || (dark == true && flat == false)
}

/** 판단에 필요한 시각. 경과 시간은 부팅 후 시간(elapsedRealtime)이라 시계를 바꿔도 흔들리지 않는다. */
data class WakeTiming(
    val nowElapsedMs: Long,
    val nowEpochMs: Long,
    val minuteOfDay: Int,
    val lastWakeElapsedMs: Long? = null,
    val lastAppWakeElapsedMs: Long? = null,
)
