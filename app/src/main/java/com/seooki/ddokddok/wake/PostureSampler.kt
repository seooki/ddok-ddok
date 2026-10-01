package com.seooki.ddokddok.wake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.seooki.ddokddok.core.PostureFacts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min

/**
 * 화면을 켜기 직전에만 센서를 잠깐 켜서 폰이 엎어져 있는지, 주머니·가방 속인지 본다.
 * 상시로 센서를 켜 두지 않으므로 배터리를 거의 쓰지 않는다.
 */
class PostureSampler(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** 주머니 감지에 쓸 센서(근접 또는 조도+가속도)가 있는지. */
    val canDetectPocket: Boolean get() = proximity != null || (light != null && accelerometer != null)

    suspend fun sample(checkFaceDown: Boolean, checkPocket: Boolean): PostureFacts = coroutineScope {
        val z = if ((checkFaceDown || checkPocket) && accelerometer != null) {
            async { readOnce(accelerometer) { it.values[2] } }
        } else {
            null
        }
        val near = if (checkPocket && proximity != null) {
            val nearLimit = min(proximity.maximumRange, NEAR_CM)
            async { readOnce(proximity) { it.values[0] < nearLimit } }
        } else {
            null
        }
        val lux = if (checkPocket && light != null) async { readOnce(light) { it.values[0] } } else null
        val zValue = z?.await()
        PostureFacts(
            faceDown = if (checkFaceDown) zValue?.let { it < FACE_DOWN_Z } else null,
            near = near?.await(),
            dark = lux?.await()?.let { it < DARK_LUX },
            flat = if (checkPocket) zValue?.let { abs(it) > FLAT_Z } else null,
        )
    }

    /** 첫 측정값 하나만 받고 바로 센서를 끈다. 시간 안에 값이 없으면 null(막지 않음)이다. */
    private suspend fun <T> readOnce(sensor: Sensor, interpret: (SensorEvent) -> T): T? {
        val manager = sensorManager ?: return null
        return withTimeoutOrNull(SAMPLE_TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(interpret(event))
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                }
                continuation.invokeOnCancellation { manager.unregisterListener(listener) }
                if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                    continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        /** 화면이 바닥을 향하면 z축 가속도가 -9.8에 가까워진다. 45도쯤 기운 것까지 엎어진 것으로 본다. */
        const val FACE_DOWN_Z = -7f

        /** 앞이든 뒤든 이만큼 눕혀져 있으면 책상 위처럼 놓인 것으로 본다. 주머니 속은 대개 세워져 있다. */
        const val FLAT_Z = 7f
        const val NEAR_CM = 5f
        const val DARK_LUX = 3f
        const val SAMPLE_TIMEOUT_MS = 400L
    }
}
