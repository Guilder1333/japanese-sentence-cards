package com.griboedov.sentencecards.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp
import java.text.DateFormat
import java.util.Date

/** Compose gives us a [Context] that's usually an Activity wrapped in decorators - unwrap it to find one, for launching the Drive consent screen. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Google Drive backup for review progress - cards plus word toLearn/forceFurigana/quiz flags
 * (see [com.griboedov.sentencecards.data.backup.BackupSnapshot]): connect once, then either the
 * daily background job (see [SentenceCardsApp.onCreate]) or "Force sync now" keeps it current.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as SentenceCardsApp
    val activity = context.findActivity()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app, app.driveAuthManager, app.driveBackupService) }
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsent.collectAsStateWithLifecycle()
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsentResult(result.data)
    }
    LaunchedEffect(pendingConsent) {
        pendingConsent?.let { intentSender ->
            consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Back up your review progress - cards and word learning flags - to your " +
                "Google Drive, in a private hidden folder only this app can read. Runs " +
                "automatically about once a day, or on demand below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { activity?.let(viewModel::forceSync) },
            enabled = activity != null && !uiState.inProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Force sync now")
            }
        }

        val lastBackup = uiState.lastBackupMillis
        Text(
            text = if (lastBackup != null) {
                "Last synced: ${DateFormat.getDateTimeInstance().format(Date(lastBackup))}"
            } else {
                "Never synced yet."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Restoring overwrites all local cards and word learning flags with whatever " +
                "was last backed up - use it to recover progress on a new install, not casually.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { showRestoreConfirm = true },
            enabled = activity != null && !uiState.inProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from backup")
        }

        uiState.message?.let { message ->
            Snackbar(
                modifier = Modifier.padding(top = 8.dp),
                action = { OutlinedButton(onClick = viewModel::clearMessage) { Text("Dismiss") } },
            ) {
                Text(message)
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This overwrites all local cards and word learning flags with the last " +
                        "backup from Google Drive. This can't be undone.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    activity?.let(viewModel::restore)
                }) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
