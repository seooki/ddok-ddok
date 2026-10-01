package com.seooki.ddokddok.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.seooki.ddokddok.R
import com.seooki.ddokddok.ui.MainActivity

/**
 * 앱이 조용히 제 기능을 못 하게 됐을 때 알림으로 알려 준다. 사용자가 알림을 허용하지 않았으면 아무것도 하지 않는다.
 * 이 앱의 알림은 화면 켜기 판단에서 제외되므로(OWN_APP) 스스로 화면을 켜지 않는다.
 */
class StatusNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var accessibilityProblemShown = false

    /**
     * 접근성 서비스가 꺼졌거나 멈춰서 전원 버튼 방식을 못 쓸 때. 다시 연결될 때까지 한 번만 띄운다.
     * [fellBack]은 기본 방식으로 대신 켰는지다. 알림이 막혀 있으면 허용한 뒤 다음 기회에 다시 띄운다.
     */
    fun showAccessibilityProblem(fellBack: Boolean) {
        if (accessibilityProblemShown) return
        accessibilityProblemShown = if (fellBack) {
            post(ID_ACCESSIBILITY, R.string.status_accessibility_title, R.string.status_accessibility_text)
        } else {
            post(ID_ACCESSIBILITY, R.string.status_accessibility_no_wake_title, R.string.status_accessibility_no_wake_text)
        }
    }

    fun clearAccessibilityProblem() {
        accessibilityProblemShown = false
        manager.cancel(ID_ACCESSIBILITY)
    }

    /** 전원 버튼 방식이 연달아 화면을 켜지 못해서 기본 방식으로 바꿨을 때. */
    fun showSwitchedToWakeLock() {
        post(ID_SWITCHED, R.string.status_switched_title, R.string.status_switched_text)
    }

    /** 띄웠으면 true. 사용자가 알림을 막아 두었으면 false다. */
    private fun post(id: Int, @StringRes title: Int, @StringRes text: Int): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.status_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val openApp = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = context.getString(text)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(id, notification)
        return true
    }

    private companion object {
        const val CHANNEL_ID = "status"
        const val ID_ACCESSIBILITY = 1
        const val ID_SWITCHED = 2
    }
}
