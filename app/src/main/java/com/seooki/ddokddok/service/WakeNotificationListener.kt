package com.seooki.ddokddok.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.appGraph

/**
 * 새 알림을 받아 화면 켜기 판단으로 넘긴다. 콜백은 메인 스레드에서 온다.
 * 프로세스가 죽어도 연결은 시스템이 다시 맺어 준다.
 */
class WakeNotificationListener : NotificationListenerService() {
    private val controller get() = applicationContext.appGraph.controller

    override fun onListenerConnected() {
        // 이미 떠 있는 알림은 새 알림이 아니므로 기억해 둔다. 알림 내용을 불러오지 않도록 키만 받는다.
        val keys = try {
            currentRanking?.orderedKeys
        } catch (e: SecurityException) {
            null
        }
        controller.onListenerConnected(keys.orEmpty())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        controller.onNotificationPosted(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        controller.onNotificationRemoved(sbn.key)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        controller.onRankingUpdate(rankingMap)
    }
}
