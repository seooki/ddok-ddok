package com.seooki.ddokddok.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.appGraph

/** 새 알림을 받아 화면 켜기 판단으로 넘긴다. 콜백은 메인 스레드에서 온다. */
class WakeNotificationListener : NotificationListenerService() {
    private val controller get() = applicationContext.appGraph.controller

    override fun onListenerConnected() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        }
        controller.onListenerConnected(active.orEmpty())
    }

    // 시스템이 연결을 끊었으면(앱 업데이트, 제조사 절전 등) 다시 연결을 요청한다. 사용자가 권한을 끈 경우엔 효과가 없다.
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, WakeNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        controller.onNotificationPosted(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        controller.onNotificationRemoved(sbn)
    }
}
