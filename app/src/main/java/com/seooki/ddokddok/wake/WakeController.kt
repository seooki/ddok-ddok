package com.seooki.ddokddok.wake

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.seooki.ddokddok.core.Decision
import com.seooki.ddokddok.core.DeviceFacts
import com.seooki.ddokddok.core.DndRecheck
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.MenuKeyHealth
import com.seooki.ddokddok.core.NotificationFacts
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.core.WakePolicy
import com.seooki.ddokddok.core.WakeResult
import com.seooki.ddokddok.core.WakeSettings
import com.seooki.ddokddok.core.WakeTiming
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.data.SettingsRepository
import com.seooki.ddokddok.data.WakeEvent
import com.seooki.ddokddok.data.WakeEventLog
import com.seooki.ddokddok.data.toOutcome
import com.seooki.ddokddok.system.StatusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val statusNotifier: StatusNotifier,
    private val scope: CoroutineScope,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    /** 센서 확인과 화면 켜기 사이에 CPU가 잠들지 않게 잡아 둔다. */
    private val pipelineLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, PIPELINE_LOCK_TAG)
        .apply { setReferenceCounted(false) }

    /** 알림 키별 마지막 게시 시각. 업데이트와 시스템의 재전송을 가려낸다. 오래 안 쓴 것부터 버린다. */
    private val postTimes = object : LinkedHashMap<String, Long>(SEEN_KEYS_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > SEEN_KEYS_MAX
    }
    private val dndRecheck = DndRecheck()

    /** 방해 금지 모드로 건너뛴 알림의 키와 그 시각. 곧 풀려서 켤 수도 있어서 잠시 뒤에 기록한다. */
    private val pendingDndRecords = HashMap<String, Long>()
    private val menuKeyHealth = MenuKeyHealth()
    private val lastWakeByApp = HashMap<String, Long>()
    private var lastWakeElapsedMs: Long? = null
    private var inFlight: Job? = null
    private var autoOff: Job? = null

    // 화면이 꺼지거나 잠금이 풀리면 자동 끄기를 취소한다. 둘 다 시스템만 보낼 수 있는 보호된 방송이고,
    // 잠금 해제(USER_PRESENT)는 SystemUI가 보내서 NOT_EXPORTED로는 받지 못한다.
    private val sessionEnded = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            autoOff?.cancel()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, sessionEnded, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    /** 연결될 때마다 처음부터 다시 기억한다. 알림 내용이 아니라 키만 받아 온다. */
    fun onListenerConnected(activeKeys: Array<out String>) {
        postTimes.clear()
        activeKeys.forEach { postTimes[it] = UNKNOWN_POST_TIME }
    }

    fun onNotificationRemoved(key: String) {
        postTimes.remove(key)
        dndRecheck.forget(key)
    }

    fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val previousPostTime = postTimes.put(sbn.key, sbn.postTime)
        val current = settings.current
        if (!current.enabled) return
        // 가장 흔한 경우(화면이 켜져 있음)는 다른 시스템 호출 없이 끝낸다.
        if (powerManager.isInteractive) return
        val facts = NotificationFactsReader.read(sbn, rankingMap, previousPostTime, System.currentTimeMillis())
        evaluateAndWake(sbn.key, facts, current)
    }

    /**
     * 방해 금지 모드에 막혔던 알림이 곧바로 풀렸으면 다시 판단한다. 연락처 예외처럼 시스템이 확인을 늦게 마치는
     * 경우인데, 이때 리스너에는 순위 갱신만 온다.
     */
    fun onRankingUpdate(rankingMap: RankingMap?) {
        if (rankingMap == null) return
        val now = SystemClock.elapsedRealtime()
        val pending = dndRecheck.pendingKeys(now)
        if (pending.isEmpty()) return
        // 방해 금지 모드 자체가 끝나서 풀린 것이면 시스템도 뒤늦게 소리를 내지 않는다.
        if (notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            pending.forEach(dndRecheck::forget)
            return
        }
        val ranking = Ranking()
        for (key in pending) {
            if (!rankingMap.getRanking(key, ranking) || !ranking.matchesInterruptionFilter()) continue
            val facts = dndRecheck.take(key, now) ?: continue
            // 결국 소리를 낸 알림이니 방해 금지로 건너뛴 기록 대신 이번 판단을 남긴다.
            pendingDndRecords.remove(key)
            evaluateAndWake(key, facts.copy(passesDnd = true), settings.current)
        }
    }

    private fun evaluateAndWake(key: String, facts: NotificationFacts, current: WakeSettings) {
        val device = DeviceFacts(
            screenOn = powerManager.isInteractive,
            inCall = audioManager.mode != AudioManager.MODE_NORMAL,
        )
        val decision = WakePolicy.evaluate(facts, device, current, context.packageName, timing(facts.packageName))
        when (decision) {
            is Decision.Skip -> {
                if (decision.reason == SkipReason.DND) {
                    skipForDnd(key, facts)
                } else {
                    record(facts.packageName, EventOutcome.Skipped(decision.reason))
                }
            }
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

    /** 방해 금지 예외가 곧 확인될 수 있어서, 그 시간이 지나도록 풀리지 않았을 때만 기록한다. */
    private fun skipForDnd(key: String, facts: NotificationFacts) {
        dndRecheck.remember(key, facts, SystemClock.elapsedRealtime())
        pendingDndRecords[key] = System.currentTimeMillis()
        scope.launch {
            delay(DndRecheck.WINDOW_MS)
            pendingDndRecords.remove(key)?.let { at ->
                record(facts.packageName, EventOutcome.Skipped(SkipReason.DND), at)
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
            waker.awaitDisplaySettled()
            // 센서를 보는 사이 사용자가 직접 화면을 켰으면 할 일이 없다.
            if (waker.isScreenOn) return
            val method = WakePolicy.chooseMethod(current, keyReachesLockScreen = waker.isDisplayOn)
            val result = waker.wake(method, current.fallbackToWakeLock)
            afterWake(packageName, method, result, current)
            record(packageName, result.toOutcome())
        } finally {
            if (pipelineLock.isHeld) pipelineLock.release()
        }
    }

    private fun afterWake(packageName: String, method: WakeMethod, result: WakeResult, current: WakeSettings) {
        if (result is WakeResult.Woke) {
            val now = SystemClock.elapsedRealtime()
            lastWakeElapsedMs = now
            lastWakeByApp[packageName] = now
            scheduleAutoOff(current.autoOffSeconds)
        }
        val menuKeyFailure = when (result) {
            is WakeResult.Woke -> result.primaryFailure
            is WakeResult.Failed -> result.reason
        }
        if (method == WakeMethod.MENU_KEY && menuKeyFailure == FailReason.ACCESSIBILITY_OFF) {
            statusNotifier.showAccessibilityProblem(fellBack = result is WakeResult.Woke)
        }
        if (menuKeyHealth.record(method, result)) {
            settings.update { it.copy(method = WakeMethod.WAKE_LOCK) }
            statusNotifier.showSwitchedToWakeLock()
        }
    }

    /**
     * 알림으로 켠 화면을 [seconds]초 뒤 전원 버튼처럼 끈다. 그사이 화면이 꺼졌거나 잠금을 풀었으면 취소된다.
     * 잠금화면을 보고만 있는지는 알 수 없어서 기본값은 꺼져 있다.
     */
    private fun scheduleAutoOff(seconds: Int) {
        autoOff?.cancel()
        if (seconds <= 0) return
        // 켠 직후의 잠김 상태를 기억해 두고, 끄기 전에 그사이 얼굴·지문으로 풀렸는지 본다.
        val lockedAtWake = keyguardManager.isDeviceLocked
        autoOff = scope.launch {
            delay(seconds * 1_000L)
            if (shouldAutoOff(lockedAtWake)) waker.lockScreen()
        }
    }

    private fun shouldAutoOff(lockedAtWake: Boolean): Boolean {
        val current = settings.current
        // 그사이 앱을 끄거나 쉬게 했거나 자동 끄기를 없앴으면 끄지 않는다.
        if (!current.enabled || current.isSnoozed(System.currentTimeMillis()) || current.autoOffSeconds <= 0) {
            return false
        }
        if (!powerManager.isInteractive || !keyguardManager.isKeyguardLocked) return false
        // '잠금화면 유지' 옵션을 켜 두면 얼굴을 인식해도 잠금화면이 남는다. 켠 뒤에 잠김이 풀렸으면 보고 있는 것이다.
        // 처음부터 풀려 있었다면(Smart Lock 등) 인식 여부를 알 수 없으니 그대로 끈다.
        return !(lockedAtWake && !keyguardManager.isDeviceLocked)
    }

    private fun timing(packageName: String): WakeTiming {
        val now = LocalTime.now()
        return WakeTiming(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            nowEpochMs = System.currentTimeMillis(),
            minuteOfDay = now.hour * 60 + now.minute,
            lastWakeElapsedMs = lastWakeElapsedMs,
            lastAppWakeElapsedMs = lastWakeByApp[packageName],
        )
    }

    private fun record(packageName: String, outcome: EventOutcome, atEpochMs: Long = System.currentTimeMillis()) {
        if (outcome is EventOutcome.Skipped && !outcome.reason.recorded) return
        events.add(WakeEvent(atEpochMs, packageName, outcome))
    }

    private companion object {
        const val PIPELINE_LOCK_TAG = "ddokddok:pipeline"
        const val PIPELINE_TIMEOUT_MS = 4_000L
        const val SEEN_KEYS_MAX = 256

        /** 연결할 때 이미 떠 있던 알림. 게시 시각은 모르므로 재전송 판단에는 쓰지 않는다. */
        const val UNKNOWN_POST_TIME = Long.MIN_VALUE
    }
}
