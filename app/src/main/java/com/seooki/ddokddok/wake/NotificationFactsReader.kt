package com.seooki.ddokddok.wake

import android.app.Notification
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.core.GroupAlert
import com.seooki.ddokddok.core.NotificationFacts

/** 알림에서 판단에 필요한 정보만 뽑는다. 제목·본문이 들어 있는 extras는 읽지 않는다. */
object NotificationFactsReader {

    fun read(sbn: StatusBarNotification, rankingMap: RankingMap?, isUpdate: Boolean): NotificationFacts {
        val notification = sbn.notification
        val flags = notification.flags
        val ranking = Ranking()
        val ranked = rankingMap?.getRanking(sbn.key, ranking) == true
        val isSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0
        return NotificationFacts(
            packageName = sbn.packageName,
            isOngoing = flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isGroupSummary = isSummary,
            isGroupChild = notification.group != null && !isSummary,
            groupAlert = when (notification.groupAlertBehavior) {
                Notification.GROUP_ALERT_SUMMARY -> GroupAlert.SUMMARY
                Notification.GROUP_ALERT_CHILDREN -> GroupAlert.CHILDREN
                else -> GroupAlert.ALL
            },
            onlyAlertOnce = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
            isUpdate = isUpdate,
            importance = if (ranked) ranking.importance else null,
            passesDnd = !ranked || ranking.matchesInterruptionFilter(),
            wakesScreenItself = notification.fullScreenIntent != null ||
                notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_ALARM,
            hiddenOnLockscreen = notification.visibility == Notification.VISIBILITY_SECRET ||
                (ranked && ranking.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET),
        )
    }
}
