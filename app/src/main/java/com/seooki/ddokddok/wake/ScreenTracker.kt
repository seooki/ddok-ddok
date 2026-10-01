package com.seooki.ddokddok.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import androidx.core.content.ContextCompat

/**
 * 화면이 언제 켜지고 언제 밝아졌는지 기억한다. 알림과 함께 다른 쪽이 화면을 켰는지,
 * AOD·알림 팝업이 방금 떴는지 가리는 데 쓴다. 화면 상태가 바뀔 때만 불리므로 배터리를 거의 쓰지 않는다.
 * 메인 스레드에서 쓴다.
 */
class ScreenTracker(context: Context, private val onScreenOffOrUnlock: () -> Unit) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    /** 마지막으로 화면이 켜진 시각. 앱이 시작될 때 이미 켜져 있었으면 오래전(0)으로 둔다. */
    private var screenOnAtMs = 0L

    /** 화면이 꺼진 뒤 켜졌다는 방송을 아직 받지 못했는지. */
    private var awaitingScreenOn = !powerManager.isInteractive
    private var lastDisplayState = displayState()

    /** 화면이 완전히 꺼진 상태(STATE_OFF)에서 마지막으로 벗어난 시각. 모르면 null이다. */
    var leftOffAtMs: Long? = null
        private set

    // 화면 꺼짐·켜짐과 잠금 해제는 시스템만 보낼 수 있는 보호된 방송이다.
    // 잠금 해제(USER_PRESENT)는 SystemUI가 보내서 NOT_EXPORTED로는 받지 못한다.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnAtMs = SystemClock.elapsedRealtime()
                    awaitingScreenOn = false
                }
                Intent.ACTION_SCREEN_OFF -> {
                    awaitingScreenOn = true
                    onScreenOffOrUnlock()
                }
                Intent.ACTION_USER_PRESENT -> onScreenOffOrUnlock()
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val state = displayState()
            if (lastDisplayState == Display.STATE_OFF && state != Display.STATE_OFF) {
                leftOffAtMs = SystemClock.elapsedRealtime()
            }
            lastDisplayState = state
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registerDisplayListener(context)
    }

    /** 지금 켜져 있는 화면이 켜진 시각. 켜졌다는 방송이 아직 오지 않았으면 방금 켜진 것이다. */
    fun screenOnSinceMs(nowMs: Long): Long = if (awaitingScreenOn) nowMs else screenOnAtMs

    private fun displayState(): Int =
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: Display.STATE_UNKNOWN

    /**
     * Android 16부터는 화면 상태 변화를 따로 구독해야 받을 수 있다(기기 설정에 따라 다름). 그 기능이 없는 기기에서는
     * 예전 방식으로 등록하고, 그때는 일반 변화와 함께 온다. 둘 다 못 받으면 알림을 받은 순간의 상태만으로 판단한다.
     */
    private fun registerDisplayListener(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                displayManager.registerDisplayListener(
                    ContextCompat.getMainExecutor(context),
                    DisplayManager.EVENT_TYPE_DISPLAY_CHANGED or DisplayManager.EVENT_TYPE_DISPLAY_STATE,
                    displayListener,
                )
                return
            } catch (e: RuntimeException) {
                // 아래 예전 방식으로 등록한다.
            } catch (e: LinkageError) {
                // 아래 예전 방식으로 등록한다.
            }
        }
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }
}
