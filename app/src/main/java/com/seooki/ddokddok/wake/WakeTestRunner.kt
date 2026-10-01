package com.seooki.ddokddok.wake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.TestAnswers
import com.seooki.ddokddok.core.TestFollowUp
import com.seooki.ddokddok.core.TestResult
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 화면 켜기 테스트. 사용자가 화면을 끄면 잠시 뒤 고른 방식으로 켜 보고,
 * 화면이 켜진 시점과 잠금이 풀린 시점을 잰다. 얼굴 인식이 됐는지는 앱이 알 수 없어서 마지막에 사용자에게 묻는다.
 * 테스트는 앱 전체 범위에서 돌아서 화면이 꺼져도 이어진다.
 */
class WakeTestRunner(
    private val context: Context,
    private val waker: ScreenWaker,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data class WaitingForScreenOff(val method: WakeMethod) : State
        data class CountingDown(val method: WakeMethod) : State
        data class Observing(val method: WakeMethod) : State
        data class Interrupted(val method: WakeMethod) : State
        data class AwaitingAnswer(val method: WakeMethod, val observation: Observation) : State
    }

    data class Observation(
        val triggerFailure: FailReason?,
        val screenOnAfterMs: Long?,
        val unlockedAfterMs: Long?,
        /** 켜기 직전 화면이 완전히 꺼지지 않았는지(AOD 등). 이때는 PIN 입력 화면이 뜰 수 있어서 결과를 따로 다룬다. */
        val displayWasOn: Boolean,
    )

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val _state = MutableStateFlow<State>(State.Idle)
    private val _followUp = MutableStateFlow<TestFollowUp?>(null)
    private var job: Job? = null

    val state: StateFlow<State> = _state.asStateFlow()

    /** 마지막 답변을 반영하며 앱이 바꾼 것. 다음 테스트를 시작하면 지운다. */
    val followUp: StateFlow<TestFollowUp?> = _followUp.asStateFlow()

    fun start(method: WakeMethod) {
        job?.cancel()
        _followUp.value = null
        job = scope.launch { run(method) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    /** 사용자가 본 결과를 설정에 반영한다. 규칙은 [TestAnswers]에 있다. */
    fun answer(result: TestResult) {
        val answered = _state.value as? State.AwaitingAnswer ?: return
        var followUp: TestFollowUp? = null
        settings.update { current ->
            val (next, change) = TestAnswers.apply(current, answered.method, result, answered.observation.displayWasOn)
            followUp = change
            next
        }
        _followUp.value = followUp
        _state.value = State.Idle
    }

    // 방송 수신기와 CPU 잠금은 테스트마다 새로 만들어서, 이전 테스트를 정리하다 새 테스트 것을 건드리는 일이 없게 한다.
    private suspend fun run(method: WakeMethod) {
        val screenOff = Channel<Unit>(Channel.CONFLATED)
        val unlocked = Channel<Long>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOff.trySend(Unit)
                    Intent.ACTION_USER_PRESENT -> unlocked.trySend(SystemClock.elapsedRealtime())
                }
            }
        }
        val countdownLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, COUNTDOWN_LOCK_TAG)
        register(receiver)
        try {
            _state.value = State.WaitingForScreenOff(method)
            if (withTimeoutOrNull(WAIT_FOR_SCREEN_OFF_MS) { screenOff.receive() } == null) {
                _state.value = State.Idle
                return
            }

            // 화면이 꺼지면 CPU가 잠들어 기다리는 시간이 늘어날 수 있어서 카운트다운 동안 깨워 둔다.
            countdownLock.acquire(COUNTDOWN_MS + LOCK_MARGIN_MS)
            _state.value = State.CountingDown(method)
            delay(COUNTDOWN_MS)
            if (waker.isScreenOn) {
                _state.value = State.Interrupted(method)
                return
            }

            unlocked.tryReceive()
            val displayWasOn = waker.isDisplayOn
            val firedAt = SystemClock.elapsedRealtime()
            val failure = waker.trigger(method)
            _state.value = State.Observing(method)
            val woke = failure == null && waker.awaitScreenOn(SCREEN_ON_TIMEOUT_MS)
            val screenOnAfter = if (woke) SystemClock.elapsedRealtime() - firedAt else null
            val unlockedAt = if (woke) withTimeoutOrNull(UNLOCK_WAIT_MS) { unlocked.receive() } else null
            _state.value = State.AwaitingAnswer(
                method,
                Observation(
                    triggerFailure = failure,
                    screenOnAfterMs = screenOnAfter,
                    unlockedAfterMs = unlockedAt?.minus(firedAt),
                    displayWasOn = displayWasOn,
                ),
            )
        } finally {
            context.unregisterReceiver(receiver)
            if (countdownLock.isHeld) countdownLock.release()
        }
    }

    private fun register(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // 둘 다 시스템만 보낼 수 있는 보호된 방송이라 열어 둬도 다른 앱이 흉내 낼 수 없다.
        // 잠금 해제(USER_PRESENT)는 system이 아니라 SystemUI가 보내서 NOT_EXPORTED로 등록하면 받지 못한다.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    companion object {
        const val COUNTDOWN_SECONDS = 6
        private const val COUNTDOWN_MS = COUNTDOWN_SECONDS * 1_000L
        private const val LOCK_MARGIN_MS = 3_000L
        private const val WAIT_FOR_SCREEN_OFF_MS = 60_000L
        private const val SCREEN_ON_TIMEOUT_MS = 3_000L
        private const val UNLOCK_WAIT_MS = 15_000L
        private const val COUNTDOWN_LOCK_TAG = "ddokddok:test"
    }
}
