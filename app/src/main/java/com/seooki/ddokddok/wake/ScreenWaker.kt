package com.seooki.ddokddok.wake

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import com.seooki.ddokddok.core.DisplayKind
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.core.WakeResult
import com.seooki.ddokddok.service.AccessibilityBridge
import kotlinx.coroutines.delay

/** 화면을 켜고 실제로 켜졌는지 확인한다. 메인 스레드에서 쓴다. */
class ScreenWaker(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    val isScreenOn: Boolean get() = powerManager.isInteractive

    /** 마지막으로 켜기 신호를 보낸 시각(부팅 후 시간). 그 직후 켜진 화면은 똑똑이 켠 것이다. */
    var lastTriggerAtMs: Long? = null
        private set

    private val displayState: Int
        get() = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN

    val isDisplayOff: Boolean get() = displayState == Display.STATE_OFF

    val displayKind: DisplayKind
        get() = when (displayState) {
            Display.STATE_OFF -> DisplayKind.OFF
            Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> DisplayKind.DOZE
            Display.STATE_ON, Display.STATE_ON_SUSPEND, Display.STATE_VR -> DisplayKind.ON
            else -> DisplayKind.UNKNOWN
        }

    /**
     * 화면이 완전히 꺼지지 않은 상태(AOD, 꺼지는 중 등)인지. 시스템(PhoneWindowManager)은 화면이 STATE_OFF가
     * 아니고 잠금화면이 떠 있으면 메뉴 키를 잠금화면에 전달한다. 그러면 PIN 입력 화면이 뜰 수 있다.
     */
    val isDisplayOn: Boolean get() = displayState != Display.STATE_OFF

    /**
     * 측면 버튼을 누른 직후처럼 화면이 꺼지는 중이면, 완전히 꺼지거나 AOD로 바뀔 때까지 잠깐 기다린다.
     * 그 사이에 메뉴 키를 보내면 잠금화면에 전달되기 때문이다.
     */
    suspend fun awaitDisplaySettled(timeoutMs: Long = SETTLE_TIMEOUT_MS) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!isScreenOn && displayState !in SETTLED_STATES && SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * 화면이 완전히 꺼질 때까지 기다린다. 알림 팝업처럼 잠깐 밝아진 화면이 사라지기를 기다리는 데 쓴다.
     * 그 사이 화면이 켜지면 바로 멈춘다. 꺼졌으면 true다.
     */
    suspend fun awaitDisplayOff(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!isScreenOn && !isDisplayOff && SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
        }
        return !isScreenOn && isDisplayOff
    }

    /**
     * 다른 쪽이 먼저 켠 화면을 전원 버튼처럼 끄고 다 꺼질(또는 AOD가 될) 때까지 기다린다.
     * 끈 뒤 메뉴 키로 다시 켜면 '키를 눌러 켬'이 되어 얼굴 인식이 시작된다. 접근성 서비스가 없거나 꺼지지 않으면 false다.
     */
    suspend fun turnOffForRewake(): Boolean {
        if (!lockScreen()) return false
        val deadline = SystemClock.elapsedRealtime() + REWAKE_OFF_TIMEOUT_MS
        while (isScreenOn && SystemClock.elapsedRealtime() < deadline) delay(POLL_INTERVAL_MS)
        if (isScreenOn) return false
        awaitDisplaySettled()
        // 화면이 꺼진 직후 바로 켜면 카메라 HAL과 Keyguard가 이전 세션을 닫기 전에 새 요청이 들어가
        // 생체인증(얼굴 인식)이 시작되지 않거나 취소된다. 안정화될 시간을 잠깐 둔다.
        delay(REWAKE_STABILIZE_MS)
        return !isScreenOn
    }

    /** 화면이 완전히 꺼질 때까지 기다린다. 꺼졌으면 true다. */
    suspend fun awaitScreenOff(timeoutMs: Long = REWAKE_OFF_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (isScreenOn && SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
        }
        return !isScreenOn
    }

    /** 켜기 신호만 보낸다. 신호를 못 보냈으면 그 이유를, 보냈으면 null을 돌려준다. */
    fun trigger(method: WakeMethod): FailReason? {
        lastTriggerAtMs = SystemClock.elapsedRealtime()
        return when (method) {
            WakeMethod.MENU_KEY -> pressMenuKey()
            WakeMethod.WAKE_LOCK -> {
                acquireScreenWakeLock()
                null
            }
        }
    }

    /** [method]로 켜 보고, 안 켜지면 [allowFallback]일 때 기본 방식으로 한 번 더 켠다. */
    suspend fun wake(method: WakeMethod, allowFallback: Boolean): WakeResult {
        // 화면이 꺼지는 과도기 상태에서 메뉴 키를 보내면 시스템이 거절하므로 비대화형이 될 때까지 짧게 대기한다.
        if (method == WakeMethod.MENU_KEY && isScreenOn) {
            awaitScreenOff(300L)
        }
        // 화면이 완전히 꺼져 있으면 메뉴 키는 곧바로 켠다. AOD·알림 팝업이 떠 있으면 메뉴 키가 그쪽으로 가서
        // 켜지지 않을 때가 많으니, 짧게만 확인하고 대체 방식으로 넘어가 늦게 켜지지 않게 한다.
        val verifyMs = if (method == WakeMethod.MENU_KEY && !isDisplayOff) QUICK_VERIFY_MS else VERIFY_TIMEOUT_MS
        val triggerFailure = trigger(method)
        if (triggerFailure == null && awaitScreenOn(verifyMs)) return WakeResult.Woke(method)
        // 확인 시간을 살짝 넘겨 켜졌다면 성공으로 본다.
        if (triggerFailure == null && isScreenOn) return WakeResult.Woke(method)
        val failure = triggerFailure ?: FailReason.NO_RESPONSE
        if (allowFallback && method != WakeMethod.WAKE_LOCK && !isScreenOn) {
            lastTriggerAtMs = SystemClock.elapsedRealtime()
            acquireScreenWakeLock()
            if (awaitScreenOn()) return WakeResult.Woke(WakeMethod.WAKE_LOCK, primaryFailure = failure)
        }
        return WakeResult.Failed(failure)
    }

    /** 화면이 켜질 때까지 짧게 확인한다. 켜지는 순간 isInteractive가 바로 바뀌어서 방송을 기다리는 것보다 빠르다. */
    suspend fun awaitScreenOn(timeoutMs: Long = VERIFY_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!isScreenOn) {
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(POLL_INTERVAL_MS)
        }
        return true
    }

    /** 전원 버튼을 눌러 끈 것처럼 화면을 끈다. 접근성 서비스가 있어야 하고, 지문·얼굴 인식은 그대로 된다. */
    fun lockScreen(): Boolean = AccessibilityBridge.service.value?.lockScreen() ?: false

    private fun pressMenuKey(): FailReason? {
        if (Build.VERSION.SDK_INT < 36) return FailReason.UNSUPPORTED
        val service = AccessibilityBridge.service.value ?: return FailReason.ACCESSIBILITY_OFF
        return if (service.pressMenuKey()) null else FailReason.REJECTED
    }

    // 앱이 화면을 켜는 공개 방법은 ACQUIRE_CAUSES_WAKEUP뿐이고, 이 플래그와 함께 쓰는 잠금 종류도 deprecated다.
    @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock() {
        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            SCREEN_WAKE_LOCK_TAG,
        ).acquire(SCREEN_WAKE_LOCK_MS)
    }

    private companion object {
        const val SCREEN_WAKE_LOCK_TAG = "ddokddok:screen"
        const val SCREEN_WAKE_LOCK_MS = 1_000L
        const val VERIFY_TIMEOUT_MS = 1_000L
        const val QUICK_VERIFY_MS = 800L
        const val SETTLE_TIMEOUT_MS = 1_000L
        const val REWAKE_OFF_TIMEOUT_MS = 1_500L
        const val REWAKE_STABILIZE_MS = 250L
        const val POLL_INTERVAL_MS = 50L
        val SETTLED_STATES = setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
    }
}
