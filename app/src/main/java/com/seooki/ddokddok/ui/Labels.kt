package com.seooki.ddokddok.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seooki.ddokddok.R
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.SkipReason
import com.seooki.ddokddok.core.TestFollowUp
import com.seooki.ddokddok.core.TestResult
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.data.WakeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@StringRes
fun WakeMethod.titleRes(): Int = when (this) {
    WakeMethod.MENU_KEY -> R.string.method_menu_key_title
    WakeMethod.WAKE_LOCK -> R.string.method_wake_lock_title
}

@StringRes
fun TestResult.labelRes(): Int = when (this) {
    TestResult.UNTESTED -> R.string.result_untested
    TestResult.FACE_UNLOCK_WORKED -> R.string.result_face_unlock
    TestResult.SCREEN_ONLY -> R.string.result_screen_only
    TestResult.DID_NOT_WAKE -> R.string.result_no_wake
    TestResult.PIN_SCREEN -> R.string.result_pin_screen
}

@StringRes
fun TestFollowUp.messageRes(): Int = when (this) {
    TestFollowUp.METHOD_CONFIRMED -> R.string.test_follow_up_confirmed
    TestFollowUp.SWITCHED_TO_WAKE_LOCK -> R.string.test_follow_up_switched
    TestFollowUp.AVOID_MENU_KEY_WHEN_DOZING -> R.string.test_follow_up_avoid_dozing
}

@StringRes
fun SkipReason.labelRes(): Int = when (this) {
    SkipReason.DISABLED -> R.string.skip_disabled
    SkipReason.SCREEN_ON -> R.string.skip_screen_on
    SkipReason.OWN_APP -> R.string.skip_own_app
    SkipReason.ONGOING -> R.string.skip_ongoing
    SkipReason.GROUP_SILENT -> R.string.skip_group_silent
    SkipReason.ALERT_ONCE_UPDATE -> R.string.skip_alert_once_update
    SkipReason.BUSY -> R.string.skip_busy
    SkipReason.EXCLUDED_APP -> R.string.skip_excluded_app
    SkipReason.SILENT -> R.string.skip_silent
    SkipReason.DND -> R.string.skip_dnd
    SkipReason.SYSTEM_HANDLES -> R.string.skip_system_handles
    SkipReason.HIDDEN_ON_LOCKSCREEN -> R.string.skip_hidden_on_lockscreen
    SkipReason.IN_CALL -> R.string.skip_in_call
    SkipReason.QUIET_HOURS -> R.string.skip_quiet_hours
    SkipReason.COOLDOWN -> R.string.skip_cooldown
    SkipReason.FACE_DOWN -> R.string.skip_face_down
    SkipReason.IN_POCKET -> R.string.skip_in_pocket
}

@StringRes
fun FailReason.labelRes(): Int = when (this) {
    FailReason.ACCESSIBILITY_OFF -> R.string.fail_accessibility_off
    FailReason.UNSUPPORTED -> R.string.fail_unsupported
    FailReason.REJECTED -> R.string.fail_rejected
    FailReason.NO_RESPONSE -> R.string.fail_no_response
}

@Composable
fun outcomeText(outcome: EventOutcome): String = when (outcome) {
    is EventOutcome.Woke -> if (outcome.usedFallback) {
        stringResource(R.string.history_woke_fallback)
    } else {
        stringResource(R.string.history_woke, stringResource(outcome.method.titleRes()))
    }
    is EventOutcome.Skipped -> stringResource(R.string.history_skipped, stringResource(outcome.reason.labelRes()))
    is EventOutcome.Failed -> stringResource(R.string.history_failed, stringResource(outcome.reason.labelRes()))
}

/** "오후 11:00"처럼 기기 언어에 맞춘 시각. */
fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "오늘 오후 3:12", "어제 오전 9:01", "9월 28일 오후 3:12" */
@Composable
fun formatEventTime(timeMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timeMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (dateTime.toLocalDate()) {
        today -> stringResource(R.string.history_today)
        today.minusDays(1) -> stringResource(R.string.history_yesterday)
        else -> dateTime.format(DateTimeFormatter.ofPattern(stringResource(R.string.history_date_pattern)))
    }
    val time = dateTime.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return "$day $time"
}

fun List<WakeEvent>.countToday(predicate: (EventOutcome) -> Boolean): Int {
    val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return count { it.timeMillis >= startOfToday && predicate(it.outcome) }
}
