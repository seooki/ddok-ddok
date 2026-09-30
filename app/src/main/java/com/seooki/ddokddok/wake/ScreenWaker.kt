package com.seooki.ddokddok.wake

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
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

    /** AOD처럼 화면이 저전력으로 켜져 있는 상태인지. 이때는 메뉴 키가 잠금화면까지 전달된다. */
    val isDozing: Boolean
        get() = when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.state) {
            Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> true
            else -> false
        }

    /** 켜기 신호만 보낸다. 신호를 못 보냈으면 그 이유를, 보냈으면 null을 돌려준다. */
    fun trigger(method: WakeMethod): FailReason? = when (method) {
        WakeMethod.MENU_KEY -> pressMenuKey()
        WakeMethod.WAKE_LOCK -> {
            acquireScreenWakeLock()
            null
        }
    }

    /** [method]로 켜 보고, 안 켜지면 [allowFallback]일 때 기본 방식으로 한 번 더 켠다. */
    suspend fun wake(method: WakeMethod, allowFallback: Boolean): WakeResult {
        val failure = trigger(method)
        if (failure == null && awaitScreenOn()) return WakeResult.Woke(method, usedFallback = false)
        if (allowFallback && method != WakeMethod.WAKE_LOCK && !isScreenOn) {
            acquireScreenWakeLock()
            if (awaitScreenOn()) return WakeResult.Woke(WakeMethod.WAKE_LOCK, usedFallback = true)
        }
        return WakeResult.Failed(failure ?: FailReason.NO_RESPONSE)
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
        const val POLL_INTERVAL_MS = 50L
    }
}
