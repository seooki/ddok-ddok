package com.seooki.ddokddok.wake

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import com.seooki.ddokddok.core.Decision
import com.seooki.ddokddok.core.DeviceFacts
import com.seooki.ddokddok.core.DisplayKind
import com.seooki.ddokddok.core.DisplayWait
import com.seooki.ddokddok.core.DndRecheck
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.ForeignWake
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
import com.seooki.ddokddok.data.WakeDetail
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

    // 화면이 꺼지거나 잠금이 풀리면 자동 끄기를 취소한다.
    private val tracker = ScreenTracker(context) { autoOff?.cancel() }

    /** 알림을 받은 순간의 상황. 시각은 부팅 후 시간이다. */
    private class Arrival(
        val atMs: Long,
        /** 알림이 게시된 시각. */
        val postedMs: Long,
        /** 받았을 때 화면이 완전히 꺼져 있었는지. */
        val displayOff: Boolean,
        /** 받았을 때 알림과 함께 다른 쪽이 켠 화면이 켜져 있었는지. */
        val wokenByOther: Boolean,
    )

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
        val nowMs = SystemClock.elapsedRealtime()
        val nowEpochMs = System.currentTimeMillis()
        val postedMs = nowMs - (nowEpochMs - sbn.postTime).coerceAtLeast(0)
        val screenOn = powerManager.isInteractive
        // 가장 흔한 경우(사람이 화면을 보고 있음)는 시각 계산만으로 끝낸다.
        val wokenByOther = screenOn && isWokenByOther(postedMs, nowMs)
        if (screenOn && !wokenByOther) return
        val arrival = Arrival(nowMs, postedMs, displayOff = !screenOn && waker.isDisplayOff, wokenByOther = wokenByOther)
        val facts = NotificationFactsReader.read(sbn, rankingMap, previousPostTime, nowEpochMs)
        evaluateAndWake(sbn.key, facts, current, arrival)
    }

    /**
     * 알림과 함께 다른 쪽(삼성 알림 팝업, 스스로 화면을 켜는 앱)이 켠 화면이 잠긴 채 켜져 있는지.
     * 그렇게 켜진 화면은 얼굴 인식이 시작되지 않는다. 시각을 먼저 보고, 맞을 때만 잠금 상태를 묻는다.
     */
    private fun isWokenByOther(postedMs: Long, nowMs: Long): Boolean =
        ForeignWake.detect(tracker.screenOnSinceMs(nowMs), postedMs, nowMs, waker.lastTriggerAtMs) &&
            keyguardManager.isKeyguardLocked && keyguardManager.isDeviceLocked

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
            val nowMs = SystemClock.elapsedRealtime()
            val arrival = Arrival(nowMs, nowMs, displayOff = waker.isDisplayOff, wokenByOther = false)
            evaluateAndWake(key, facts.copy(passesDnd = true), settings.current, arrival)
        }
    }

    private fun evaluateAndWake(key: String, facts: NotificationFacts, current: WakeSettings, arrival: Arrival) {
        val device = DeviceFacts(
            // 알림과 함께 다른 쪽이 켠 화면은 꺼져 있던 것으로 보고 판단한다.
            screenOn = powerManager.isInteractive && !arrival.wokenByOther,
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
                inFlight = scope.launch { wake(facts.packageName, current, arrival) }
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
                record(facts.packageName, EventOutcome.Skipped(SkipReason.DND), atEpochMs = at)
            }
        }
    }

    private suspend fun wake(packageName: String, current: WakeSettings, arrival: Arrival) {
        pipelineLock.acquire(PIPELINE_TIMEOUT_MS)
        try {
            if (current.skipWhenFaceDown || current.skipWhenInPocket) {
                val facts = posture.sample(current.skipWhenFaceDown, current.skipWhenInPocket)
                WakePolicy.evaluatePosture(facts, current)?.let { reason ->
                    record(packageName, EventOutcome.Skipped(reason))
                    return
                }
            }
            // 받을 때부터 켜져 있었거나 센서를 보는 사이 켜졌다.
            var rewoke = false
            if (waker.isScreenOn) {
                if (!turnOffIfWokenByOther(packageName, current, arrival)) return
                rewoke = true
            }
            waker.awaitDisplaySettled()
            // 삼성 알림 팝업·엣지 라이팅처럼 알림 때문에 잠깐 밝아졌으면, 사라질 때까지 기다린다.
            // 이때 메뉴 키를 보내면 팝업이나 잠금화면 쪽으로 가서 전원 버튼처럼 켜지지 않는다.
            var waitedMs = 0L
            if (!rewoke && DisplayWait.shouldWait(arrival.displayOff, waker.isDisplayOff, tracker.leftOffAtMs, arrival.atMs)) {
                val startedMs = SystemClock.elapsedRealtime()
                waker.awaitDisplayOff(DisplayWait.MAX_WAIT_MS)
                waitedMs = SystemClock.elapsedRealtime() - startedMs
                if (waker.isScreenOn) {
                    if (!turnOffIfWokenByOther(packageName, current, arrival)) return
                    rewoke = true
                }
            }
            if (waker.isScreenOn) return
            val display = waker.displayKind
            val method = WakePolicy.chooseMethod(current, keyReachesLockScreen = display != DisplayKind.OFF)
            val result = waker.wake(method, current.fallbackToWakeLock)
            afterWake(packageName, method, result, current, displayWasOff = display == DisplayKind.OFF)
            record(packageName, result.toOutcome(), WakeDetail(display, waitedMs, rewoke))
        } finally {
            if (pipelineLock.isHeld) pipelineLock.release()
        }
    }

    /**
     * 화면이 켜져 있을 때 부른다. 알림과 함께 다른 쪽이 켠 화면이면 끄고 true를 돌려준다(이제 다시 켤 차례).
     * 사람이 켠 화면이면 그대로 두고 false다. 다시 켜지 않도록 했거나 끄지 못했으면 그렇게 기록하고 false다.
     */
    private suspend fun turnOffIfWokenByOther(packageName: String, current: WakeSettings, arrival: Arrival): Boolean {
        if (!isWokenByOther(arrival.postedMs, SystemClock.elapsedRealtime())) return false
        if (current.rewakeWhenOthersWake && current.method == WakeMethod.MENU_KEY && waker.turnOffForRewake()) return true
        record(packageName, EventOutcome.Skipped(SkipReason.WOKEN_BY_OTHER))
        return false
    }

    private fun afterWake(
        packageName: String,
        method: WakeMethod,
        result: WakeResult,
        current: WakeSettings,
        displayWasOff: Boolean,
    ) {
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
        if (menuKeyHealth.record(method, result, displayWasOff)) {
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

    private fun record(
        packageName: String,
        outcome: EventOutcome,
        detail: WakeDetail? = null,
        atEpochMs: Long = System.currentTimeMillis(),
    ) {
        if (outcome is EventOutcome.Skipped && !outcome.reason.recorded) return
        events.add(WakeEvent(atEpochMs, packageName, outcome, detail))
    }

    private companion object {
        const val PIPELINE_LOCK_TAG = "ddokddok:pipeline"
        /** 센서 확인, 다른 쪽이 켠 화면 끄기, 알림 팝업 기다리기(최대 6초), 켜기 확인을 모두 덮는다. */
        const val PIPELINE_TIMEOUT_MS = 13_000L
        const val SEEN_KEYS_MAX = 256

        /** 연결할 때 이미 떠 있던 알림. 게시 시각은 모르므로 재전송 판단에는 쓰지 않는다. */
        const val UNKNOWN_POST_TIME = Long.MIN_VALUE
    }
}
