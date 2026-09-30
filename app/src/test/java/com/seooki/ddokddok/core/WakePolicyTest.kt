package com.seooki.ddokddok.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePolicyTest {

    private val chat = NotificationFacts(packageName = "com.kakao.talk", importance = 4)
    private val screenOff = DeviceFacts(screenOn = false, inCall = false)
    private val defaults = WakeSettings(cooldownSeconds = 0)

    private fun evaluate(
        notification: NotificationFacts = chat,
        device: DeviceFacts = screenOff,
        settings: WakeSettings = defaults,
        nowElapsedMs: Long = 100_000L,
        lastWakeElapsedMs: Long? = null,
        minuteOfDay: Int = 12 * 60,
    ) = WakePolicy.evaluate(notification, device, settings, OWN_PACKAGE, nowElapsedMs, lastWakeElapsedMs, minuteOfDay)

    private fun skip(reason: SkipReason) = Decision.Skip(reason)

    @Test
    fun `소리 나는 새 알림이면 켠다`() {
        assertEquals(Decision.Wake, evaluate())
    }

    @Test
    fun `기능이 꺼져 있으면 켜지 않는다`() {
        assertEquals(skip(SkipReason.DISABLED), evaluate(settings = defaults.copy(enabled = false)))
    }

    @Test
    fun `화면이 켜져 있으면 켜지 않는다`() {
        assertEquals(skip(SkipReason.SCREEN_ON), evaluate(device = screenOff.copy(screenOn = true)))
    }

    @Test
    fun `자기 앱 알림으로는 켜지 않는다`() {
        assertEquals(skip(SkipReason.OWN_APP), evaluate(notification = chat.copy(packageName = OWN_PACKAGE)))
    }

    @Test
    fun `진행 중 알림은 켜지 않는다`() {
        assertEquals(skip(SkipReason.ONGOING), evaluate(notification = chat.copy(isOngoing = true)))
    }

    @Test
    fun `사용자가 끈 앱은 켜지 않는다`() {
        val settings = defaults.copy(excludedPackages = setOf(chat.packageName))
        assertEquals(skip(SkipReason.EXCLUDED_APP), evaluate(settings = settings))
    }

    @Test
    fun `소리 없는 채널은 켜지 않고 중요도를 모르면 막지 않는다`() {
        assertEquals(skip(SkipReason.SILENT), evaluate(notification = chat.copy(importance = 2)))
        assertEquals(Decision.Wake, evaluate(notification = chat.copy(importance = WakePolicy.IMPORTANCE_DEFAULT)))
        assertEquals(Decision.Wake, evaluate(notification = chat.copy(importance = null)))
    }

    @Test
    fun `방해 금지에 막힌 알림은 설정을 따른다`() {
        val blocked = chat.copy(passesDnd = false)
        assertEquals(skip(SkipReason.DND), evaluate(notification = blocked))
        assertEquals(Decision.Wake, evaluate(notification = blocked, settings = defaults.copy(respectDnd = false)))
    }

    @Test
    fun `묶음 알림은 소리를 내는 쪽에서만 켠다`() {
        val summary = chat.copy(isGroupSummary = true)
        val child = chat.copy(isGroupChild = true)
        assertEquals(skip(SkipReason.GROUP_SILENT), evaluate(notification = summary.copy(groupAlert = GroupAlert.CHILDREN)))
        assertEquals(skip(SkipReason.GROUP_SILENT), evaluate(notification = child.copy(groupAlert = GroupAlert.SUMMARY)))
        assertEquals(Decision.Wake, evaluate(notification = summary.copy(groupAlert = GroupAlert.SUMMARY)))
        assertEquals(Decision.Wake, evaluate(notification = child.copy(groupAlert = GroupAlert.CHILDREN)))
        assertEquals(Decision.Wake, evaluate(notification = child.copy(groupAlert = GroupAlert.ALL)))
    }

    @Test
    fun `한 번만 울리는 알림은 처음에만 켠다`() {
        val alertOnce = chat.copy(onlyAlertOnce = true)
        assertEquals(Decision.Wake, evaluate(notification = alertOnce))
        assertEquals(skip(SkipReason.ALERT_ONCE_UPDATE), evaluate(notification = alertOnce.copy(isUpdate = true)))
    }

    @Test
    fun `메시지가 쌓이며 갱신되는 알림은 매번 켠다`() {
        assertEquals(Decision.Wake, evaluate(notification = chat.copy(isUpdate = true)))
    }

    @Test
    fun `전화·알람은 시스템에 맡긴다`() {
        assertEquals(skip(SkipReason.SYSTEM_HANDLES), evaluate(notification = chat.copy(wakesScreenItself = true)))
    }

    @Test
    fun `잠금화면에 안 보이는 알림은 켜지 않는다`() {
        assertEquals(skip(SkipReason.HIDDEN_ON_LOCKSCREEN), evaluate(notification = chat.copy(hiddenOnLockscreen = true)))
    }

    @Test
    fun `통화 중에는 켜지 않는다`() {
        assertEquals(skip(SkipReason.IN_CALL), evaluate(device = screenOff.copy(inCall = true)))
    }

    @Test
    fun `방해하지 않을 시간에는 켜지 않는다`() {
        val settings = defaults.copy(quietHours = QuietHours(enabled = true, startMinute = 23 * 60, endMinute = 7 * 60))
        assertEquals(skip(SkipReason.QUIET_HOURS), evaluate(settings = settings, minuteOfDay = 2 * 60))
        assertEquals(Decision.Wake, evaluate(settings = settings, minuteOfDay = 12 * 60))
    }

    @Test
    fun `간격 안에서는 한 번만 켠다`() {
        val settings = defaults.copy(cooldownSeconds = 5)
        assertEquals(skip(SkipReason.COOLDOWN), evaluate(settings = settings, nowElapsedMs = 104_999, lastWakeElapsedMs = 100_000))
        assertEquals(Decision.Wake, evaluate(settings = settings, nowElapsedMs = 105_000, lastWakeElapsedMs = 100_000))
        assertEquals(Decision.Wake, evaluate(settings = settings, lastWakeElapsedMs = null))
        assertEquals(Decision.Wake, evaluate(settings = defaults, nowElapsedMs = 100_001, lastWakeElapsedMs = 100_000))
    }

    @Test
    fun `엎어져 있거나 주머니 속이면 설정에 따라 막는다`() {
        val settings = WakeSettings(skipWhenFaceDown = true, skipWhenInPocket = true)
        assertEquals(SkipReason.FACE_DOWN, WakePolicy.evaluatePosture(PostureFacts(faceDown = true), settings))
        assertEquals(SkipReason.IN_POCKET, WakePolicy.evaluatePosture(PostureFacts(near = true), settings))
        assertNull(WakePolicy.evaluatePosture(PostureFacts(faceDown = false, near = false), settings))
        assertNull(WakePolicy.evaluatePosture(PostureFacts(), settings))
        assertNull(WakePolicy.evaluatePosture(PostureFacts(faceDown = true, near = true), WakeSettings(skipWhenFaceDown = false, skipWhenInPocket = false)))
    }

    private companion object {
        const val OWN_PACKAGE = "com.seooki.ddokddok"
    }
}
