package com.seooki.ddokddok.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.data.EventOutcome
import com.seooki.ddokddok.data.WakeEvent
import com.seooki.ddokddok.data.WakeEventLog
import com.seooki.ddokddok.ui.components.AppIcon
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.screenContentPadding
import com.seooki.ddokddok.ui.countToday
import com.seooki.ddokddok.ui.formatEventTime
import com.seooki.ddokddok.ui.outcomeText

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val repository = graph.settings
    val events by graph.events.events.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val newestFirst = remember(events) { events.asReversed() }
    val wokeToday = remember(events) { events.countToday { it is EventOutcome.Woke } }
    val skippedToday = remember(events) { events.countToday { it is EventOutcome.Skipped } }
    val failedToday = remember(events) { events.countToday { it is EventOutcome.Failed } }

    DetailScaffold(
        title = R.string.history_title,
        onBack = onBack,
        actions = {
            if (events.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = screenContentPadding(padding)) {
            if (events.isEmpty()) {
                item { EmptyHistory() }
            } else {
                item {
                    Text(
                        text = if (failedToday > 0) {
                            stringResource(R.string.history_summary_with_failures, wokeToday, skippedToday, failedToday)
                        } else {
                            stringResource(R.string.history_summary, wokeToday, skippedToday)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(newestFirst) { event ->
                    HistoryRow(
                        event = event,
                        excluded = event.packageName in settings.excludedPackages,
                        onSetExcluded = { exclude ->
                            repository.update {
                                val next = if (exclude) it.excludedPackages + event.packageName else it.excludedPackages - event.packageName
                                it.copy(excludedPackages = next)
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.history_note, WakeEventLog.MAX_EVENTS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    graph.events.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryRow(event: WakeEvent, excluded: Boolean, onSetExcluded: (Boolean) -> Unit) {
    val catalog = LocalContext.current.appGraph.apps
    val label by produceState(initialValue = event.packageName, event.packageName) {
        value = catalog.label(event.packageName)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val outcomeColor = when (event.outcome) {
        is EventOutcome.Woke -> MaterialTheme.colorScheme.primary
        is EventOutcome.Skipped -> MaterialTheme.colorScheme.onSurfaceVariant
        is EventOutcome.Failed -> MaterialTheme.colorScheme.error
    }
    ListItem(
        leadingContent = { AppIcon(event.packageName) },
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(outcomeText(event.outcome), color = outcomeColor, style = MaterialTheme.typography.bodyMedium)
                Text(formatEventTime(event.timeMillis), style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.history_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (excluded) R.string.history_include_app else R.string.history_exclude_app))
                        },
                        onClick = {
                            onSetExcluded(!excluded)
                            menuOpen = false
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun EmptyHistory() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
