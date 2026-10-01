package com.seooki.ddokddok.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seooki.ddokddok.BuildConfig
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.core.TestResult
import com.seooki.ddokddok.core.WakeSettings
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.service.AccessibilityBridge
import com.seooki.ddokddok.system.SetupStatus
import com.seooki.ddokddok.system.SystemSetup
import com.seooki.ddokddok.ui.Destinations
import com.seooki.ddokddok.ui.components.BadgeTone
import com.seooki.ddokddok.ui.components.NavigationRow
import com.seooki.ddokddok.ui.components.SectionHeader
import com.seooki.ddokddok.ui.components.StatusBadge
import com.seooki.ddokddok.ui.components.screenContentPadding
import com.seooki.ddokddok.ui.countToday
import com.seooki.ddokddok.ui.durationLabel
import com.seooki.ddokddok.ui.formatEpochTime
import com.seooki.ddokddok.ui.formatMinuteOfDay
import com.seooki.ddokddok.ui.labelRes
import com.seooki.ddokddok.ui.titleRes
import kotlinx.coroutines.delay

/** 앱을 막 열었을 때는 접근성 서비스가 연결되는 중일 수 있어서, 이만큼 지나도 연결이 없을 때만 멈춤으로 본다. */
private const val STUCK_GRACE_MS = 3_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val graph = context.appGraph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val events by graph.events.events.collectAsStateWithLifecycle()
    val accessibilityService by AccessibilityBridge.service.collectAsStateWithLifecycle()

    // 권한은 시스템 설정 화면에서 바뀌므로 돌아올 때마다 다시 읽는다. 접근성 연결은 그 자체로도 바뀐다.
    var setup by remember { mutableStateOf(SystemSetup.status(context)) }
    LifecycleResumeEffect(Unit) {
        setup = SystemSetup.status(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(accessibilityService) { setup = SystemSetup.status(context) }

    var showStuck by remember { mutableStateOf(false) }
    LaunchedEffect(setup.accessibilityStuck) {
        showStuck = false
        if (setup.accessibilityStuck) {
            delay(STUCK_GRACE_MS)
            showStuck = true
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        setup = SystemSetup.status(context)
    }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = {
        // 한 번 거절하면 시스템이 다시 묻지 않을 수 있어서, 두 번째부터는 알림 설정 화면을 연다.
        if (Build.VERSION.SDK_INT >= 33 && !askedNotificationPermission) {
            askedNotificationPermission = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SystemSetup.openAppNotificationSettings(context)
        }
    }

    val snoozed = rememberSnoozed(settings.snoozeUntilEpochMs)
    val todayWakes = remember(events) { events.countToday { it is EventOutcome.Woke } }
    val methodTested = settings.testResult(settings.method) != TestResult.UNTESTED
    val showSetup = !setup.essentialsDone(settings.method) || !setup.accessibilityEnabled ||
        !setup.batteryUnrestricted || !setup.notificationsAllowed || !methodTested

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            contentPadding = screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MasterCard(
                    settings = settings,
                    snoozed = snoozed,
                    ready = setup.essentialsDone(settings.method),
                    missing = setup.missingEssentials(settings.method),
                    todayWakes = todayWakes,
                    onToggle = { on -> graph.settings.update { it.copy(enabled = on, snoozeUntilEpochMs = 0) } },
                    onSnooze = {
                        val until = System.currentTimeMillis() + WakeSettings.SNOOZE_MS
                        graph.settings.update { it.copy(snoozeUntilEpochMs = until) }
                    },
                    onResume = { graph.settings.update { it.copy(snoozeUntilEpochMs = 0) } },
                )
            }
            if (showStuck) {
                item {
                    StuckAccessibilityCard(
                        consequence = when {
                            !setup.accessibilityRequired(settings.method) -> null
                            settings.fallbackToWakeLock -> stringResource(R.string.home_stuck_fallback)
                            else -> stringResource(R.string.home_stuck_no_wake)
                        },
                        onOpen = { SystemSetup.openAccessibility(context) },
                    )
                }
            }
            if (showSetup) {
                item {
                    SetupCard(
                        setup = setup,
                        accessibilityRequired = setup.accessibilityRequired(settings.method),
                        methodTested = methodTested,
                        onOpenNotificationAccess = { SystemSetup.openNotificationAccess(context) },
                        onOpenAccessibility = { SystemSetup.openAccessibility(context) },
                        onOpenAppDetails = { SystemSetup.openAppDetails(context) },
                        onRequestBattery = { SystemSetup.requestBatteryUnrestricted(context) },
                        onRequestNotifications = requestNotifications,
                        onOpenTest = { onNavigate(Destinations.METHOD) },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.home_section_settings)) }
            item {
                SettingsCard(
                    settings = settings,
                    todayWakes = todayWakes,
                    hasHistory = events.isNotEmpty(),
                    canDetectPocket = graph.canDetectPocket,
                    onNavigate = onNavigate,
                )
            }
            item { PrivacyNote() }
        }
    }
}

/** 쉬는 시간이 끝나는 순간 화면이 다시 그려지도록 한다. */
@Composable
private fun rememberSnoozed(snoozeUntilEpochMs: Long): Boolean {
    var snoozed by remember(snoozeUntilEpochMs) { mutableStateOf(System.currentTimeMillis() < snoozeUntilEpochMs) }
    // 폰이 잠든 동안에는 아래 타이머도 멈추므로, 화면으로 돌아올 때 시계로 다시 확인한다.
    LifecycleResumeEffect(snoozeUntilEpochMs) {
        snoozed = System.currentTimeMillis() < snoozeUntilEpochMs
        onPauseOrDispose {}
    }
    LaunchedEffect(snoozeUntilEpochMs) {
        val remaining = snoozeUntilEpochMs - System.currentTimeMillis()
        if (remaining > 0) {
            delay(remaining)
            snoozed = false
        }
    }
    return snoozed
}

@Composable
private fun MasterCard(
    settings: WakeSettings,
    snoozed: Boolean,
    ready: Boolean,
    missing: Int,
    todayWakes: Int,
    onToggle: (Boolean) -> Unit,
    onSnooze: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val active = settings.enabled && ready && !snoozed
    val status = when {
        !ready -> stringResource(R.string.home_status_setup_needed, missing)
        !settings.enabled -> stringResource(R.string.home_status_off)
        snoozed -> stringResource(R.string.home_status_snoozed, formatEpochTime(settings.snoozeUntilEpochMs))
        else -> stringResource(R.string.home_status_on, todayWakes)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (active) colors.onPrimaryContainer else colors.onSurface,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = settings.enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = if (settings.enabled && ready) 8.dp else 24.dp),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_master_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.enabled, onCheckedChange = null)
        }
        if (settings.enabled && ready) {
            TextButton(
                onClick = if (snoozed) onResume else onSnooze,
                modifier = Modifier.padding(start = 60.dp, bottom = 12.dp),
            ) {
                Text(stringResource(if (snoozed) R.string.home_resume else R.string.home_snooze))
            }
        }
    }
}

@Composable
private fun StuckAccessibilityCard(consequence: String?, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_stuck_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            val body = stringResource(R.string.home_stuck_body)
            Text(
                text = if (consequence != null) "$body $consequence" else body,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpen) { Text(stringResource(R.string.home_stuck_action)) }
        }
    }
}

@Composable
private fun SetupCard(
    setup: SetupStatus,
    accessibilityRequired: Boolean,
    methodTested: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenTest: () -> Unit,
) {
    OutlinedCard {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.home_setup_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SetupStep(
                done = setup.notificationAccess,
                title = R.string.setup_notification_title,
                body = R.string.setup_notification_body,
                action = R.string.setup_action_allow,
                onAction = onOpenNotificationAccess,
            )
            SetupStep(
                done = setup.accessibilityEnabled,
                title = R.string.setup_accessibility_title,
                body = if (accessibilityRequired) R.string.setup_accessibility_body else R.string.setup_accessibility_body_optional,
                action = R.string.setup_action_turn_on,
                onAction = onOpenAccessibility,
                optional = !accessibilityRequired,
            )
            if (!setup.notificationAccess || !setup.accessibilityEnabled) RestrictedSettingsHint(onOpenAppDetails)
            SetupStep(
                done = setup.batteryUnrestricted,
                title = R.string.setup_battery_title,
                body = R.string.setup_battery_body,
                action = R.string.setup_action_allow,
                onAction = onRequestBattery,
                optional = true,
            )
            SetupStep(
                done = setup.notificationsAllowed,
                title = R.string.setup_status_notifications_title,
                body = R.string.setup_status_notifications_body,
                action = R.string.setup_action_allow,
                onAction = onRequestNotifications,
                optional = true,
            )
            SetupStep(
                done = methodTested,
                title = R.string.setup_test_title,
                body = R.string.setup_test_body,
                action = R.string.setup_action_test,
                onAction = onOpenTest,
                optional = true,
            )
        }
    }
}

@Composable
private fun SetupStep(
    done: Boolean,
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        StepIndicator(done)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                if (optional) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(stringResource(R.string.setup_optional), BadgeTone.Neutral)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!done) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onAction) { Text(stringResource(action)) }
            }
        }
    }
}

@Composable
private fun StepIndicator(done: Boolean) {
    if (done) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.setup_done),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    } else {
        val pending = stringResource(R.string.setup_pending)
        Box(
            Modifier
                .size(24.dp)
                .padding(2.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .semantics { contentDescription = pending },
        )
    }
}

/** 스토어 밖에서 설치한 앱은 Android 13부터 알림 접근·접근성 허용이 막혀 있어서 푸는 방법을 알려 준다. */
@Composable
private fun RestrictedSettingsHint(onOpenAppDetails: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_restricted_title), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.setup_restricted_hint), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenAppDetails) { Text(stringResource(R.string.setup_open_app_info)) }
        }
    }
}

@Composable
private fun SettingsCard(
    settings: WakeSettings,
    todayWakes: Int,
    hasHistory: Boolean,
    canDetectPocket: Boolean,
    onNavigate: (String) -> Unit,
) {
    OutlinedCard {
        NavigationRow(
            icon = rememberVectorPainter(Icons.Filled.Face),
            title = stringResource(R.string.home_row_method),
            summary = stringResource(
                R.string.home_method_summary,
                stringResource(settings.method.titleRes()),
                stringResource(settings.testResult(settings.method).labelRes()),
            ),
            onClick = { onNavigate(Destinations.METHOD) },
        )
        NavigationRow(
            icon = rememberVectorPainter(Icons.Filled.Settings),
            title = stringResource(R.string.home_row_conditions),
            summary = conditionsSummary(settings, canDetectPocket),
            onClick = { onNavigate(Destinations.CONDITIONS) },
        )
        NavigationRow(
            icon = painterResource(R.drawable.ic_apps),
            title = stringResource(R.string.home_row_apps),
            summary = if (settings.excludedPackages.isEmpty()) {
                stringResource(R.string.home_apps_all)
            } else {
                stringResource(R.string.home_apps_excluded, settings.excludedPackages.size)
            },
            onClick = { onNavigate(Destinations.APPS) },
        )
        NavigationRow(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List),
            title = stringResource(R.string.home_row_history),
            summary = if (hasHistory) {
                stringResource(R.string.home_history_today, todayWakes)
            } else {
                stringResource(R.string.home_history_empty)
            },
            onClick = { onNavigate(Destinations.HISTORY) },
        )
        NavigationRow(
            icon = rememberVectorPainter(Icons.Filled.Info),
            title = stringResource(R.string.home_row_about),
            summary = stringResource(R.string.home_about_summary, BuildConfig.VERSION_NAME),
            onClick = { onNavigate(Destinations.ABOUT) },
        )
    }
}

@Composable
private fun conditionsSummary(settings: WakeSettings, canDetectPocket: Boolean): String {
    val parts = buildList {
        if (settings.respectDnd) add(stringResource(R.string.home_conditions_dnd))
        if (settings.skipWhenFaceDown) add(stringResource(R.string.home_conditions_face_down))
        if (settings.skipWhenInPocket && canDetectPocket) add(stringResource(R.string.home_conditions_pocket))
        if (settings.cooldownSeconds > 0) {
            add(stringResource(R.string.home_conditions_cooldown, durationLabel(settings.cooldownSeconds, R.string.cond_cooldown_none)))
        }
        if (settings.perAppCooldownSeconds > 0) {
            add(
                stringResource(
                    R.string.home_conditions_app_cooldown,
                    durationLabel(settings.perAppCooldownSeconds, R.string.cond_cooldown_none),
                ),
            )
        }
        if (settings.autoOffSeconds > 0) {
            add(stringResource(R.string.home_conditions_auto_off, durationLabel(settings.autoOffSeconds, R.string.cond_auto_off_none)))
        }
        if (settings.quietHours.isEffective) {
            add(
                stringResource(
                    R.string.home_conditions_quiet,
                    formatMinuteOfDay(settings.quietHours.startMinute),
                    formatMinuteOfDay(settings.quietHours.endMinute),
                ),
            )
        }
    }
    return if (parts.isEmpty()) stringResource(R.string.home_conditions_none) else parts.joinToString(" · ")
}

@Composable
private fun PrivacyNote() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
