package com.seooki.ddokddok.ui.conditions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.core.WakeSettings
import com.seooki.ddokddok.service.AccessibilityBridge
import com.seooki.ddokddok.ui.components.ChoiceDialog
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.SectionHeader
import com.seooki.ddokddok.ui.components.SettingSwitchRow
import com.seooki.ddokddok.ui.components.TimeDialog
import com.seooki.ddokddok.ui.components.ValueRow
import com.seooki.ddokddok.ui.components.screenContentPadding
import com.seooki.ddokddok.ui.durationLabel
import com.seooki.ddokddok.ui.formatMinuteOfDay

private enum class QuietEdge { START, END }

private enum class ChoiceKind { COOLDOWN, PER_APP_COOLDOWN, AUTO_OFF }

@Composable
fun ConditionsScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val repository = graph.settings
    val settings by repository.settings.collectAsStateWithLifecycle()
    val accessibilityService by AccessibilityBridge.service.collectAsStateWithLifecycle()
    val canDetectPocket = graph.canDetectPocket
    var openChoice by rememberSaveable { mutableStateOf<ChoiceKind?>(null) }
    var editingEdge by rememberSaveable { mutableStateOf<QuietEdge?>(null) }

    DetailScaffold(title = R.string.conditions_title, onBack = onBack) { padding ->
        LazyColumn(contentPadding = screenContentPadding(padding)) {
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.cond_dnd_title),
                    summary = stringResource(R.string.cond_dnd_body),
                    checked = settings.respectDnd,
                    onCheckedChange = { on -> repository.update { it.copy(respectDnd = on) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.cond_face_down_title),
                    summary = stringResource(R.string.cond_face_down_body),
                    checked = settings.skipWhenFaceDown,
                    onCheckedChange = { on -> repository.update { it.copy(skipWhenFaceDown = on) } },
                )
            }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.cond_pocket_title),
                    summary = stringResource(
                        if (canDetectPocket) R.string.cond_pocket_body else R.string.cond_pocket_unavailable,
                    ),
                    checked = settings.skipWhenInPocket && canDetectPocket,
                    onCheckedChange = { on -> repository.update { it.copy(skipWhenInPocket = on) } },
                    enabled = canDetectPocket,
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.cond_cooldown_title),
                    value = durationLabel(settings.cooldownSeconds, R.string.cond_cooldown_none),
                    summary = stringResource(R.string.cond_cooldown_body),
                    onClick = { openChoice = ChoiceKind.COOLDOWN },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.cond_app_cooldown_title),
                    value = durationLabel(settings.perAppCooldownSeconds, R.string.cond_cooldown_none),
                    summary = stringResource(R.string.cond_app_cooldown_body),
                    onClick = { openChoice = ChoiceKind.PER_APP_COOLDOWN },
                )
            }

            item { SectionHeader(stringResource(R.string.cond_after_wake_section)) }
            item {
                val available = accessibilityService != null
                ValueRow(
                    title = stringResource(R.string.cond_auto_off_title),
                    value = durationLabel(settings.autoOffSeconds, R.string.cond_auto_off_none),
                    summary = stringResource(
                        if (available) R.string.cond_auto_off_body else R.string.cond_auto_off_unavailable,
                    ),
                    enabled = available,
                    onClick = { openChoice = ChoiceKind.AUTO_OFF },
                )
            }

            item { SectionHeader(stringResource(R.string.cond_quiet_section)) }
            item {
                SettingSwitchRow(
                    title = stringResource(R.string.cond_quiet_title),
                    summary = stringResource(R.string.cond_quiet_body),
                    checked = settings.quietHours.enabled,
                    onCheckedChange = { on -> repository.update { it.copy(quietHours = it.quietHours.copy(enabled = on)) } },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.cond_quiet_start),
                    value = formatMinuteOfDay(settings.quietHours.startMinute),
                    enabled = settings.quietHours.enabled,
                    onClick = { editingEdge = QuietEdge.START },
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.cond_quiet_end),
                    value = formatMinuteOfDay(settings.quietHours.endMinute),
                    enabled = settings.quietHours.enabled,
                    onClick = { editingEdge = QuietEdge.END },
                )
            }
            if (settings.quietHours.enabled && !settings.quietHours.isEffective) {
                item {
                    Text(
                        text = stringResource(R.string.cond_quiet_same_time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    openChoice?.let { kind ->
        val (title, options, selected, zeroLabel) = when (kind) {
            ChoiceKind.COOLDOWN -> ChoiceSpec(
                R.string.cond_cooldown_title, WakeSettings.COOLDOWN_CHOICES, settings.cooldownSeconds, R.string.cond_cooldown_none,
            )
            ChoiceKind.PER_APP_COOLDOWN -> ChoiceSpec(
                R.string.cond_app_cooldown_title,
                WakeSettings.PER_APP_COOLDOWN_CHOICES,
                settings.perAppCooldownSeconds,
                R.string.cond_cooldown_none,
            )
            ChoiceKind.AUTO_OFF -> ChoiceSpec(
                R.string.cond_auto_off_title, WakeSettings.AUTO_OFF_CHOICES, settings.autoOffSeconds, R.string.cond_auto_off_none,
            )
        }
        ChoiceDialog(
            title = stringResource(title),
            options = options,
            selected = selected,
            label = { durationLabel(it, zeroLabel) },
            onSelect = { seconds ->
                repository.update {
                    when (kind) {
                        ChoiceKind.COOLDOWN -> it.copy(cooldownSeconds = seconds)
                        ChoiceKind.PER_APP_COOLDOWN -> it.copy(perAppCooldownSeconds = seconds)
                        ChoiceKind.AUTO_OFF -> it.copy(autoOffSeconds = seconds)
                    }
                }
                openChoice = null
            },
            onDismiss = { openChoice = null },
        )
    }

    editingEdge?.let { edge ->
        val quiet = settings.quietHours
        TimeDialog(
            title = stringResource(if (edge == QuietEdge.START) R.string.cond_quiet_start else R.string.cond_quiet_end),
            initialMinute = if (edge == QuietEdge.START) quiet.startMinute else quiet.endMinute,
            onConfirm = { minute ->
                repository.update {
                    val hours = if (edge == QuietEdge.START) {
                        it.quietHours.copy(startMinute = minute)
                    } else {
                        it.quietHours.copy(endMinute = minute)
                    }
                    it.copy(quietHours = hours)
                }
                editingEdge = null
            },
            onDismiss = { editingEdge = null },
        )
    }
}

private data class ChoiceSpec(val title: Int, val options: List<Int>, val selected: Int, val zeroLabel: Int)
