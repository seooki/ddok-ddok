package com.seooki.ddokddok.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager

/**
 * 시나리오 이름을 받아 그에 맞는 실제 알림을 올린다.
 *
 * adb shell am broadcast -f 0x20 -n com.seooki.ddokddok.notifier/.PostReceiver --es case alert
 */
class PostReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannels(manager)
        when (intent.getStringExtra("case")) {
            "alert" -> manager.notify(1, base(context, CHANNEL_DEFAULT, "alert").build())
            "low" -> manager.notify(2, base(context, CHANNEL_LOW, "low importance").build())
            "min" -> manager.notify(3, base(context, CHANNEL_MIN, "min importance").build())
            "ongoing" -> manager.notify(4, base(context, CHANNEL_DEFAULT, "ongoing").setOngoing(true).build())
            // 같은 id로 두 번 보내면 두 번째는 업데이트다.
            "alert_once" -> manager.notify(5, base(context, CHANNEL_DEFAULT, "alert once").setOnlyAlertOnce(true).build())
            "update" -> manager.notify(
                6,
                base(context, CHANNEL_DEFAULT, "message ${System.currentTimeMillis() % 10_000}").build(),
            )
            "group_children" -> postGroup(manager, context, "g_children", Notification.GROUP_ALERT_CHILDREN, 10)
            "group_summary" -> postGroup(manager, context, "g_summary", Notification.GROUP_ALERT_SUMMARY, 20)
            "fullscreen" -> manager.notify(
                30,
                base(context, CHANNEL_HIGH, "full screen").setFullScreenIntent(fullScreenIntent(context), true).build(),
            )
            "call" -> manager.notify(31, base(context, CHANNEL_HIGH, "call").setCategory(Notification.CATEGORY_CALL).build())
            "secret" -> manager.notify(
                32,
                base(context, CHANNEL_DEFAULT, "secret").setVisibility(Notification.VISIBILITY_SECRET).build(),
            )
            "messaging" -> manager.notify(33, messaging(context).build())
            // AndroidX NotificationCompat.setSilent(true)가 그룹 없는 알림에 하는 것과 같다. Android 16은 이를 FLAG_SILENT로 바꾼다.
            "silent" -> manager.notify(
                34,
                base(context, CHANNEL_DEFAULT, "silent")
                    .setGroup(SILENT_GROUP)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                    .build(),
            )
            // 요약 표시는 있지만 그룹이 없으면 시스템은 일반 알림으로 보고 소리를 낸다.
            "summary_nogroup" -> manager.notify(
                35,
                base(context, CHANNEL_DEFAULT, "summary without group")
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    .build(),
            )
            // 알림을 올리면서 스스로 화면을 켜는 앱(또는 삼성 알림 팝업)을 흉내 낸다. '앱이 켬'으로 기록되어 얼굴 인식이 시작되지 않는다.
            "self_wake" -> {
                manager.notify(36, base(context, CHANNEL_DEFAULT, "self wake").build())
                wakeScreen(context)
            }
            // 음성 채팅·회의 앱이 백그라운드에서 통신 모드를 잡고 있는 상황(통화 음성은 나오지 않음)을 흉내 낸다.
            "comm_mode_on" -> context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
            "comm_mode_off" -> context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
            "cancel_all" -> manager.cancelAll()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context) {
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "notifier:self_wake")
            .acquire(SELF_WAKE_MS)
    }

    private fun postGroup(manager: NotificationManager, context: Context, group: String, alert: Int, firstId: Int) {
        manager.notify(
            firstId,
            base(context, CHANNEL_DEFAULT, "$group child").setGroup(group).setGroupAlertBehavior(alert).build(),
        )
        manager.notify(
            firstId + 1,
            base(context, CHANNEL_DEFAULT, "$group summary")
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(alert)
                .build(),
        )
    }

    private fun messaging(context: Context): Notification.Builder {
        val me = Person.Builder().setName("me").build()
        val friend = Person.Builder().setName("friend").build()
        val style = Notification.MessagingStyle(me).addMessage("hello", System.currentTimeMillis(), friend)
        return base(context, CHANNEL_HIGH, "chat").setStyle(style).setCategory(Notification.CATEGORY_MESSAGE)
    }

    private fun fullScreenIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, FullScreenActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun base(context: Context, channel: String, title: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("ddok notifier")

    private fun createChannels(manager: NotificationManager) {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_HIGH, "high", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_DEFAULT, "default", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_LOW, "low", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_MIN, "min", NotificationManager.IMPORTANCE_MIN),
            ),
        )
    }

    private companion object {
        const val CHANNEL_HIGH = "high"
        const val CHANNEL_DEFAULT = "default"
        const val CHANNEL_LOW = "low"
        const val CHANNEL_MIN = "min"
        const val SILENT_GROUP = "silent"
        const val SELF_WAKE_MS = 3_000L
    }
}
