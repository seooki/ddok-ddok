package com.seooki.ddokddok.data

import android.content.Context
import androidx.core.content.edit
import com.seooki.ddokddok.core.QuietHours
import com.seooki.ddokddok.core.TestResult
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.core.WakeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 설정 저장소. 알림이 올 때 디스크를 읽지 않도록 현재 값을 메모리에 들고 있고, 바뀔 때만 저장한다.
 * 메인 스레드에서만 쓴다.
 */
class SettingsRepository(context: Context, private val defaultMethod: WakeMethod) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())

    val settings: StateFlow<WakeSettings> = _settings.asStateFlow()

    val current: WakeSettings get() = _settings.value

    fun update(transform: (WakeSettings) -> WakeSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        write(next)
    }

    private fun read(): WakeSettings {
        val defaults = WakeSettings(method = defaultMethod)
        return WakeSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            snoozeUntilEpochMs = prefs.getLong(KEY_SNOOZE_UNTIL, defaults.snoozeUntilEpochMs),
            method = WakeMethod.fromKey(prefs.getString(KEY_METHOD, null)) ?: defaults.method,
            fallbackToWakeLock = prefs.getBoolean(KEY_FALLBACK, defaults.fallbackToWakeLock),
            avoidMenuKeyWhenDozing = prefs.getBoolean(KEY_AVOID_MENU_KEY_WHEN_DOZING, defaults.avoidMenuKeyWhenDozing),
            rewakeWhenOthersWake = prefs.getBoolean(KEY_REWAKE_WHEN_OTHERS_WAKE, defaults.rewakeWhenOthersWake),
            respectDnd = prefs.getBoolean(KEY_RESPECT_DND, defaults.respectDnd),
            skipWhenFaceDown = prefs.getBoolean(KEY_SKIP_FACE_DOWN, defaults.skipWhenFaceDown),
            skipWhenInPocket = prefs.getBoolean(KEY_SKIP_POCKET, defaults.skipWhenInPocket),
            cooldownSeconds = prefs.getInt(KEY_COOLDOWN, defaults.cooldownSeconds),
            perAppCooldownSeconds = prefs.getInt(KEY_PER_APP_COOLDOWN, defaults.perAppCooldownSeconds),
            autoOffSeconds = prefs.getInt(KEY_AUTO_OFF, defaults.autoOffSeconds),
            quietHours = QuietHours(
                enabled = prefs.getBoolean(KEY_QUIET_ENABLED, defaults.quietHours.enabled),
                startMinute = prefs.getInt(KEY_QUIET_START, defaults.quietHours.startMinute),
                endMinute = prefs.getInt(KEY_QUIET_END, defaults.quietHours.endMinute),
            ),
            excludedPackages = prefs.getStringSet(KEY_EXCLUDED, null)?.toSet() ?: defaults.excludedPackages,
            testResults = WakeMethod.entries.associateWith { TestResult.fromKey(prefs.getString(testKey(it), null)) },
        )
    }

    private fun write(settings: WakeSettings) = prefs.edit {
        putBoolean(KEY_ENABLED, settings.enabled)
        putLong(KEY_SNOOZE_UNTIL, settings.snoozeUntilEpochMs)
        putString(KEY_METHOD, settings.method.key)
        putBoolean(KEY_FALLBACK, settings.fallbackToWakeLock)
        putBoolean(KEY_AVOID_MENU_KEY_WHEN_DOZING, settings.avoidMenuKeyWhenDozing)
        putBoolean(KEY_REWAKE_WHEN_OTHERS_WAKE, settings.rewakeWhenOthersWake)
        putBoolean(KEY_RESPECT_DND, settings.respectDnd)
        putBoolean(KEY_SKIP_FACE_DOWN, settings.skipWhenFaceDown)
        putBoolean(KEY_SKIP_POCKET, settings.skipWhenInPocket)
        putInt(KEY_COOLDOWN, settings.cooldownSeconds)
        putInt(KEY_PER_APP_COOLDOWN, settings.perAppCooldownSeconds)
        putInt(KEY_AUTO_OFF, settings.autoOffSeconds)
        putBoolean(KEY_QUIET_ENABLED, settings.quietHours.enabled)
        putInt(KEY_QUIET_START, settings.quietHours.startMinute)
        putInt(KEY_QUIET_END, settings.quietHours.endMinute)
        putStringSet(KEY_EXCLUDED, settings.excludedPackages)
        WakeMethod.entries.forEach { putString(testKey(it), settings.testResult(it).key) }
    }

    private fun testKey(method: WakeMethod) = "test_${method.key}"

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_SNOOZE_UNTIL = "snooze_until_epoch_ms"
        const val KEY_METHOD = "method"
        const val KEY_FALLBACK = "fallback_to_wake_lock"
        const val KEY_AVOID_MENU_KEY_WHEN_DOZING = "avoid_menu_key_when_dozing"
        const val KEY_REWAKE_WHEN_OTHERS_WAKE = "rewake_when_others_wake"
        const val KEY_RESPECT_DND = "respect_dnd"
        const val KEY_SKIP_FACE_DOWN = "skip_face_down"
        const val KEY_SKIP_POCKET = "skip_in_pocket"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_PER_APP_COOLDOWN = "per_app_cooldown_seconds"
        const val KEY_AUTO_OFF = "auto_off_seconds"
        const val KEY_QUIET_ENABLED = "quiet_enabled"
        const val KEY_QUIET_START = "quiet_start_minute"
        const val KEY_QUIET_END = "quiet_end_minute"
        const val KEY_EXCLUDED = "excluded_packages"
    }
}
