package com.seooki.ddokddok.ui.conditions

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.core.WakeSettings
import com.seooki.ddokddok.ui.components.ChoiceDialog
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.SectionHeader
import com.seooki.ddokddok.ui.components.SettingSwitchRow
import com.seooki.ddokddok.ui.components.TimeDialog
import com.seooki.ddokddok.ui.components.ValueRow
import com.seooki.ddokddok.ui.components.screenContentPadding
import com.seooki.ddokddok.ui.formatMinuteOfDay

private enum class QuietEdge { START, END }

@Composable
fun ConditionsScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val repository = graph.settings
    val settings by repository.settings.collectAsStateWithLifecycle()
    val hasProximitySensor = graph.hasProximitySensor
    var showCooldownDialog by rememberSaveable { mutableStateOf(false) }
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
                        if (hasProximitySensor) R.string.cond_pocket_body else R.string.cond_pocket_unavailable,
                    ),
                    checked = settings.skipWhenInPocket && hasProximitySensor,
                    onCheckedChange = { on -> repository.update { it.copy(skipWhenInPocket = on) } },
                    enabled = hasProximitySensor,
                )
            }
            item {
                ValueRow(
                    title = stringResource(R.string.cond_cooldown_title),
                    value = cooldownLabel(settings.cooldownSeconds),
                    summary = stringResource(R.string.cond_cooldown_body),
                    onClick = { showCooldownDialog = true },
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
        }
    }

    if (showCooldownDialog) {
        ChoiceDialog(
            title = stringResource(R.string.cond_cooldown_title),
            options = WakeSettings.COOLDOWN_CHOICES,
            selected = settings.cooldownSeconds,
            label = { cooldownLabel(it) },
            onSelect = { seconds ->
                repository.update { it.copy(cooldownSeconds = seconds) }
                showCooldownDialog = false
            },
            onDismiss = { showCooldownDialog = false },
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

@Composable
private fun cooldownLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.cond_cooldown_none) else stringResource(R.string.cond_cooldown_seconds, seconds)
