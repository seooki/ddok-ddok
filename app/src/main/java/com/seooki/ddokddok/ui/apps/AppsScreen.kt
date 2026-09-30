package com.seooki.ddokddok.ui.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seooki.ddokddok.R
import com.seooki.ddokddok.appGraph
import com.seooki.ddokddok.ui.components.AppIcon
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.screenContentPadding

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val graph = LocalContext.current.appGraph
    val repository = graph.settings
    val viewModel: AppsViewModel = viewModel { AppsViewModel(graph.apps) }
    val apps by viewModel.visibleApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val settings by repository.settings.collectAsStateWithLifecycle()
    val excluded = settings.excludedPackages

    DetailScaffold(
        title = R.string.apps_title,
        onBack = onBack,
        actions = {
            if (excluded.isNotEmpty()) {
                TextButton(onClick = { repository.update { it.copy(excludedPackages = emptySet()) } }) {
                    Text(stringResource(R.string.apps_enable_all))
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = screenContentPadding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.apps_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.apps_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val list = apps
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                list.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> items(list, key = { it.packageName }) { app ->
                    val included = app.packageName !in excluded
                    ListItem(
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Switch(checked = included, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(value = included, role = Role.Switch) { on ->
                            repository.update {
                                val next = if (on) it.excludedPackages - app.packageName else it.excludedPackages + app.packageName
                                it.copy(excludedPackages = next)
                            }
                        },
                    )
                }
            }
        }
    }
}
