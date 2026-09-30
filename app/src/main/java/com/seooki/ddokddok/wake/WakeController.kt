package com.seooki.ddokddok.wake

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.core.Decision
import com.seooki.ddokddok.core.DeviceFacts
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.WakePolicy
import com.seooki.ddokddok.core.WakeResult
import com.seooki.ddokddok.core.WakeSettings
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.data.SettingsRepository
import com.seooki.ddokddok.data.WakeEvent
import com.seooki.ddokddok.data.WakeEventLog
import com.seooki.ddokddok.data.toOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 알림 하나가 오면 규칙 판단 → (필요하면) 센서 확인 → 화면 켜기 → 기록 순으로 처리한다.
 * 메인 스레드에서만 쓴다. 대부분의 알림은 메모리 안의 계산만으로 끝나고,
 * CPU 깨우기와 센서는 실제로 화면을 켤 때만 잠깐 쓴다.
 */
class WakeController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val events: WakeEventLog,
    private val waker: ScreenWaker,
    private val posture: PostureSampler,
    private val scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** 센서 확인과 화면 켜기 사이에 CPU가 잠들지 않게 잡아 둔다. */
    private val pipelineLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PIPELINE_LOCK_TAG)
        .apply { setReferenceCounted(false) }

    /** 이미 본 알림 키. '한 번만 알림' 업데이트를 가려낸다. 오래 안 쓴 것부터 버린다. */
    private val seenKeys = object : LinkedHashMap<String, Unit>(SEEN_KEYS_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > SEEN_KEYS_MAX
    }

    private var lastWakeElapsedMs: Long? = null
    private var inFlight: Job? = null

    /** 이미 떠 있는 알림은 새 알림이 아니므로 기억해 둔다. */
    fun onListenerConnected(active: Array<out StatusBarNotification>) {
        active.forEach { seenKeys[it.key] = Unit }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        seenKeys.remove(sbn.key)
    }

    fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val isUpdate = seenKeys.put(sbn.key, Unit) != null
        val current = settings.current
        if (!current.enabled) return

        val facts = NotificationFactsReader.read(sbn, rankingMap, isUpdate)
        val device = DeviceFacts(
            screenOn = powerManager.isInteractive,
            inCall = audioManager.mode != AudioManager.MODE_NORMAL,
        )
        val now = LocalTime.now()
        val decision = WakePolicy.evaluate(
            notification = facts,
            device = device,
            settings = current,
            ownPackage = context.packageName,
            nowElapsedMs = SystemClock.elapsedRealtime(),
            lastWakeElapsedMs = lastWakeElapsedMs,
            minuteOfDay = now.hour * 60 + now.minute,
        )
        when (decision) {
            is Decision.Skip -> record(facts.packageName, EventOutcome.Skipped(decision.reason))
            Decision.Wake -> {
                // 요약·개별 알림이 한꺼번에 오면 한 번만 켠다.
                if (inFlight?.isActive == true) {
                    record(facts.packageName, EventOutcome.Skipped(SkipReason.BUSY))
                    return
                }
                inFlight = scope.launch { wake(facts.packageName, current) }
            }
        }
    }

    private suspend fun wake(packageName: String, current: WakeSettings) {
        pipelineLock.acquire(PIPELINE_TIMEOUT_MS)
        try {
            if (current.skipWhenFaceDown || current.skipWhenInPocket) {
                val facts = posture.sample(current.skipWhenFaceDown, current.skipWhenInPocket)
                WakePolicy.evaluatePosture(facts, current)?.let { reason ->
                    record(packageName, EventOutcome.Skipped(reason))
                    return
                }
            }
            // 센서를 보는 사이 사용자가 직접 화면을 켰으면 할 일이 없다.
            if (waker.isScreenOn) return
            val method = WakePolicy.chooseMethod(current, displayDozing = waker.isDozing)
            val result = waker.wake(method, current.fallbackToWakeLock)
            if (result is WakeResult.Woke) lastWakeElapsedMs = SystemClock.elapsedRealtime()
            record(packageName, result.toOutcome())
        } finally {
            if (pipelineLock.isHeld) pipelineLock.release()
        }
    }

    private fun record(packageName: String, outcome: EventOutcome) {
        if (outcome is EventOutcome.Skipped && !outcome.reason.recorded) return
        events.add(WakeEvent(System.currentTimeMillis(), packageName, outcome))
    }

    private companion object {
        const val PIPELINE_LOCK_TAG = "ddokddok:pipeline"
        const val PIPELINE_TIMEOUT_MS = 3_000L
        const val SEEN_KEYS_MAX = 256
    }
}
