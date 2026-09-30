package com.seooki.ddokddok.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            "cancel_all" -> manager.cancelAll()
        }
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
    }
}
