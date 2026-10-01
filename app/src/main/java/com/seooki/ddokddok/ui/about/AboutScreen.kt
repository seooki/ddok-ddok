package com.seooki.ddokddok.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seooki.ddokddok.BuildConfig
import com.seooki.ddokddok.R
import com.seooki.ddokddok.system.Diagnostics
import com.seooki.ddokddok.ui.components.DetailScaffold
import com.seooki.ddokddok.ui.components.screenContentPadding

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.about_copied)
    val clipLabel = stringResource(R.string.about_diagnostics_title)
    val copyDiagnostics = {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(clipLabel, Diagnostics.report(context)))
        // Android 13부터는 시스템이 복사됐다고 직접 알려 준다.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    DetailScaffold(title = R.string.about_title, onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = screenContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.about_diagnostics_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.about_diagnostics_body), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = copyDiagnostics, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.about_diagnostics_copy))
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.about_licenses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
