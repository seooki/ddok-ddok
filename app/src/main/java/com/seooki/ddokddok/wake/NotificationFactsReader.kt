package com.seooki.ddokddok.wake

import android.app.Notification
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.core.GroupAlert
import com.seooki.ddokddok.core.NotificationFacts
import com.seooki.ddokddok.core.Repost

/** 알림에서 판단에 필요한 정보만 뽑는다. 제목·본문이 들어 있는 extras는 읽지 않는다. */
object NotificationFactsReader {

    /**
     * Notification.FLAG_SILENT(숨은 API). Android 16은 무음 알림(setSilent)의 "silent" 그룹을 지우고
     * 이 표시로 바꿔서 리스너에 넘긴다(Notification.fixSilentGroup). 그보다 낮은 버전에서는 쓰지 않는 비트다.
     */
    private const val FLAG_SILENT = 0x00020000

    /** [previousPostTime]은 같은 키로 이전에 받은 게시 시각이다. 처음 보는 알림이면 null이다. */
    fun read(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
        previousPostTime: Long?,
        nowEpochMs: Long,
    ): NotificationFacts {
        val notification = sbn.notification
        val flags = notification.flags
        val ranking = Ranking()
        val ranked = rankingMap?.getRanking(sbn.key, ranking) == true
        // 시스템과 같게, 그룹이 있어야 요약 알림으로 본다.
        val hasGroup = notification.group != null
        val isSummary = hasGroup && flags and Notification.FLAG_GROUP_SUMMARY != 0
        return NotificationFacts(
            packageName = sbn.packageName,
            isOngoing = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isGroupSummary = isSummary,
            isGroupChild = hasGroup && !isSummary,
            groupAlert = when (notification.groupAlertBehavior) {
                Notification.GROUP_ALERT_SUMMARY -> GroupAlert.SUMMARY
                Notification.GROUP_ALERT_CHILDREN -> GroupAlert.CHILDREN
                else -> GroupAlert.ALL
            },
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            isUpdate = previousPostTime != null,
            isRepost = Repost.detect(sbn.postTime, previousPostTime, nowEpochMs),
            postAgeMs = (nowEpochMs - sbn.postTime).coerceAtLeast(0),
            importance = if (ranked) ranking.importance else null,
            silentFlag = flags and FLAG_SILENT != 0,
            appSuspended = ranked && ranking.isSuspended,
            passesDnd = !ranked || ranking.matchesInterruptionFilter(),
            wakesScreenItself = notification.fullScreenIntent != null ||
                notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_ALARM,
            hiddenOnLockscreen = notification.visibility == Notification.VISIBILITY_SECRET ||
                (ranked && ranking.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET),
        )
    }
}
