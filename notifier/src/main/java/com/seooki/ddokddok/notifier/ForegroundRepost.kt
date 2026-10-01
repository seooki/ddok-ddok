package com.seooki.ddokddok.notifier

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * 포그라운드 서비스를 시작하고 잠시 뒤 알림만 남긴 채 끝낸다. 이때 시스템은 서비스 표시만 뗀 같은 알림을
 * 게시 시각 그대로 다시 보내고(소리 없음), Android 16 에뮬레이터는 이어서 새 게시 시각으로 한 번 더 올리며
 * 소리를 낸다. 화면 켜기 앱이 앞의 것은 건너뛰고 뒤의 것에만 반응하는지 확인하는 데 쓴다.
 *
 * 포그라운드 서비스는 화면이 보일 때만 시작할 수 있어서 화면이 켜진 상태에서 실행한다.
 * adb shell am start -n com.seooki.ddokddok.notifier/.ForegroundRepostActivity --el detach_ms 6000
 */
class ForegroundRepostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val detachAfter = intent.getLongExtra(EXTRA_DETACH_MS, DEFAULT_DETACH_MS)
        startForegroundService(
            Intent(this, ForegroundRepostService::class.java).putExtra(EXTRA_DETACH_MS, detachAfter),
        )
        finish()
    }
}

private const val EXTRA_DETACH_MS = "detach_ms"
private const val DEFAULT_DETACH_MS = 6_000L

class ForegroundRepostService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "default", NotificationManager.IMPORTANCE_DEFAULT))
        // 시스템은 짧게 끝나는 서비스의 알림을 최대 10초 늦게 띄우므로, 바로 띄워야 '이미 있는 알림의 재전송'이 재현된다.
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("foreground work")
            .setContentText("ddok notifier")
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        handler.postDelayed({
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }, intent?.getLongExtra(EXTRA_DETACH_MS, DEFAULT_DETACH_MS) ?: DEFAULT_DETACH_MS)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    private companion object {
        const val CHANNEL = "default"
        const val NOTIFICATION_ID = 40
    }
}
