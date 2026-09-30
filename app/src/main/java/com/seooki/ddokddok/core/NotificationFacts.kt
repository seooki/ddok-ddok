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
    /** 채널 중요도(NotificationManager.IMPORTANCE_*). 알 수 없으면 null. */
    val importance: Int? = null,
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
)
