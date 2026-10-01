package com.seooki.ddokddok.ui.method

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.core.FailReason
import com.seooki.ddokddok.core.TestFollowUp
import com.seooki.ddokddok.core.TestResult
import com.seooki.ddokddok.core.WakeMethod
import com.seooki.ddokddok.service.AccessibilityBridge
import com.seooki.ddokddok.system.SystemSetup
import com.seooki.ddokddok.ui.components.BadgeTone
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.SectionHeader
import com.seooki.ddokddok.ui.components.SettingSwitchRow
import com.seooki.ddokddok.ui.components.StatusBadge
import com.seooki.ddokddok.ui.components.screenContentPadding
import com.seooki.ddokddok.ui.labelRes
import com.seooki.ddokddok.ui.messageRes
import com.seooki.ddokddok.ui.titleRes
import com.seooki.ddokddok.wake.WakeTestRunner
import com.seooki.ddokddok.wake.WakeTestRunner.State
import java.util.Locale

@Composable
fun WakeMethodScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.appGraph
    val repository = graph.settings
    val settings by repository.settings.collectAsStateWithLifecycle()
    val testState by graph.wakeTest.state.collectAsStateWithLifecycle()
    val followUp by graph.wakeTest.followUp.collectAsStateWithLifecycle()
    val accessibilityService by AccessibilityBridge.service.collectAsStateWithLifecycle()
    val menuKeySupported = Build.VERSION.SDK_INT >= 36
    val canTest = settings.method != WakeMethod.MENU_KEY || accessibilityService != null

    DetailScaffold(title = R.string.method_screen_title, onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MethodOption(
                        title = stringResource(WakeMethod.MENU_KEY.titleRes()),
                        body = stringResource(
                            if (menuKeySupported) R.string.method_menu_key_body else R.string.method_menu_key_unsupported,
                        ),
                        result = settings.testResult(WakeMethod.MENU_KEY),
                        selected = settings.method == WakeMethod.MENU_KEY,
                        enabled = menuKeySupported,
                        recommended = true,
                        onSelect = { repository.update { it.copy(method = WakeMethod.MENU_KEY) } },
                    )
                    MethodOption(
                        title = stringResource(WakeMethod.WAKE_LOCK.titleRes()),
                        body = stringResource(R.string.method_wake_lock_body),
                        result = settings.testResult(WakeMethod.WAKE_LOCK),
                        selected = settings.method == WakeMethod.WAKE_LOCK,
                        enabled = true,
                        recommended = false,
                        onSelect = { repository.update { it.copy(method = WakeMethod.WAKE_LOCK) } },
                    )
                }
            }
            if (settings.method == WakeMethod.MENU_KEY && menuKeySupported) {
                item {
                    OutlinedCard {
                        SettingSwitchRow(
                            title = stringResource(R.string.method_fallback_title),
                            summary = stringResource(R.string.method_fallback_body),
                            checked = settings.fallbackToWakeLock,
                            onCheckedChange = { on -> repository.update { it.copy(fallbackToWakeLock = on) } },
                        )
                        SettingSwitchRow(
                            title = stringResource(R.string.method_avoid_dozing_title),
                            summary = stringResource(R.string.method_avoid_dozing_body),
                            checked = settings.avoidMenuKeyWhenDozing,
                            onCheckedChange = { on -> repository.update { it.copy(avoidMenuKeyWhenDozing = on) } },
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.test_section_title)) }
            item {
                TestPanel(
                    state = testState,
                    followUp = followUp,
                    selectedMethod = settings.method,
                    canTest = canTest,
                    onStart = { graph.wakeTest.start(settings.method) },
                    onCancel = graph.wakeTest::cancel,
                    onAnswer = graph.wakeTest::answer,
                    onOpenAccessibility = { SystemSetup.openAccessibility(context) },
                )
            }
            item { FaceUnlockTip(onOpenSecurity = { SystemSetup.openSecuritySettings(context) }) }
        }
    }
}

/** 삼성 얼굴 인식을 아이폰처럼 쓰는 방법. 인식 뒤에도 잠금화면에 머물러야 알림을 보고 밀어서 들어가는 흐름이 된다. */
@Composable
private fun FaceUnlockTip(onOpenSecurity: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.method_tip_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.method_tip_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSecurity) { Text(stringResource(R.string.method_tip_action)) }
        }
    }
}

@Composable
private fun MethodOption(
    title: String,
    body: String,
    result: TestResult,
    selected: Boolean,
    enabled: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primary else colors.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.outlinedShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Row(Modifier.padding(16.dp)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(stringResource(R.string.method_recommended), BadgeTone.Positive)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                StatusBadge(
                    text = stringResource(result.labelRes()),
                    tone = when (result) {
                        TestResult.FACE_UNLOCK_WORKED -> BadgeTone.Positive
                        TestResult.UNTESTED -> BadgeTone.Neutral
                        TestResult.SCREEN_ONLY, TestResult.DID_NOT_WAKE, TestResult.PIN_SCREEN -> BadgeTone.Warning
                    },
                )
            }
        }
    }
}

@Composable
private fun TestPanel(
    state: State,
    followUp: TestFollowUp?,
    selectedMethod: WakeMethod,
    canTest: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onAnswer: (TestResult) -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                State.Idle -> {
                    if (followUp != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(followUp.messageRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.test_idle_body, stringResource(selectedMethod.titleRes())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!canTest) {
                        Text(
                            stringResource(R.string.test_needs_accessibility),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.setup_action_turn_on))
                        }
                    }
                    Button(onClick = onStart, enabled = canTest, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.test_start))
                    }
                }
                is State.WaitingForScreenOff -> {
                    Text(
                        stringResource(R.string.test_waiting, WakeTestRunner.COUNTDOWN_SECONDS),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_cancel))
                    }
                }
                is State.CountingDown -> {
                    Text(stringResource(R.string.test_counting), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_cancel))
                    }
                }
                is State.Observing -> {
                    Text(stringResource(R.string.test_observing), style = MaterialTheme.typography.titleMedium)
                }
                is State.Interrupted -> {
                    Text(stringResource(R.string.test_interrupted), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onStart, enabled = canTest, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_retry))
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_close))
                    }
                }
                is State.AwaitingAnswer -> {
                    Text(observationText(state.observation), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.test_question), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onAnswer(TestResult.FACE_UNLOCK_WORKED) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_answer_face))
                    }
                    OutlinedButton(onClick = { onAnswer(TestResult.SCREEN_ONLY) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_answer_screen_only))
                    }
                    OutlinedButton(onClick = { onAnswer(TestResult.PIN_SCREEN) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_answer_pin_screen))
                    }
                    OutlinedButton(onClick = { onAnswer(TestResult.DID_NOT_WAKE) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_answer_no_wake))
                    }
                }
            }
        }
    }
}

@Composable
private fun observationText(observation: WakeTestRunner.Observation): String {
    val screenOnAfter = observation.screenOnAfterMs
    if (screenOnAfter == null) {
        val reason = stringResource((observation.triggerFailure ?: FailReason.NO_RESPONSE).labelRes())
        return stringResource(R.string.test_observation_not_woke, reason)
    }
    val parts = buildList {
        if (observation.displayWasOn) add(stringResource(R.string.test_observation_dozing))
        add(stringResource(R.string.test_observation_woke, seconds(screenOnAfter)))
        observation.unlockedAfterMs?.let { add(stringResource(R.string.test_observation_unlocked, seconds(it))) }
    }
    return parts.joinToString(" ")
}

private fun seconds(ms: Long): String = String.format(Locale.getDefault(), "%.1f", ms / 1_000.0)
