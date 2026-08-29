package com.griboedov.sentencecards.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.backup.BackupPrefs
import com.griboedov.sentencecards.data.backup.BackupResult
import com.griboedov.sentencecards.data.backup.DriveAuthManager
import com.griboedov.sentencecards.data.backup.DriveBackupService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What [SettingsViewModel.onConsentResult] should resume once interactive consent finishes. */
private enum class PendingAction { BACKUP, RESTORE }

data class SettingsUiState(
    val inProgress: Boolean = false,
    val lastBackupMillis: Long? = null,
    val message: String? = null,
)

/**
 * Drives the Settings screen's Google Drive backup UI - see
 * [com.griboedov.sentencecards.data.backup.BackupSnapshot] for what's synced. [forceSync] and
 * [restore] both go through [DriveAuthManager.requestAccess] first; when that comes back needing
 * interactive consent, [pendingConsent] carries the [android.content.IntentSender] for the
 * Composable to launch, and [onConsentResult] resumes whichever action was pending once that
 * returns.
 */
class SettingsViewModel(
    private val appContext: Context,
    private val driveAuthManager: DriveAuthManager,
    private val driveBackupService: DriveBackupService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(lastBackupMillis = BackupPrefs.lastBackupMillis(appContext)))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _pendingConsent = MutableStateFlow<android.content.IntentSender?>(null)
    val pendingConsent: StateFlow<android.content.IntentSender?> = _pendingConsent.asStateFlow()

    private var pendingAction: PendingAction? = null

    fun forceSync(activity: Activity) = start(activity, PendingAction.BACKUP)

    fun restore(activity: Activity) = start(activity, PendingAction.RESTORE)

    private fun start(activity: Activity, action: PendingAction) {
        if (_uiState.value.inProgress) return
        _uiState.value = _uiState.value.copy(inProgress = true, message = null)
        viewModelScope.launch {
            runCatching { driveAuthManager.requestAccess(activity) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is DriveAuthManager.Outcome.Resolved -> perform(action, outcome.result.accessToken)
                        is DriveAuthManager.Outcome.NeedsResolution -> {
                            pendingAction = action
                            _pendingConsent.value = outcome.pendingIntent.intentSender
                        }
                    }
                }
                .onFailure { e -> fail("Google sign-in failed: ${e.message}") }
        }
    }

    /** [SettingsScreen] calls this once its consent launcher returns, with the result Intent (or null if cancelled). */
    fun onConsentResult(resultIntent: Intent?) {
        _pendingConsent.value = null
        val action = pendingAction
        pendingAction = null
        if (resultIntent == null || action == null) {
            fail("Google Drive access was cancelled.")
            return
        }
        viewModelScope.launch {
            runCatching { driveAuthManager.resultFromIntent(resultIntent) }
                .onSuccess { perform(action, it.accessToken) }
                .onFailure { e -> fail("Google sign-in failed: ${e.message}") }
        }
    }

    private suspend fun perform(action: PendingAction, accessToken: String?) {
        if (accessToken == null) {
            fail("Google sign-in failed: no access token returned.")
            return
        }
        val result = when (action) {
            PendingAction.BACKUP -> driveBackupService.backup(accessToken)
            PendingAction.RESTORE -> driveBackupService.restore(accessToken)
        }
        when (result) {
            is BackupResult.Success -> {
                if (action == PendingAction.BACKUP) BackupPrefs.setLastBackup(appContext, result.timestampMillis)
                _uiState.value = SettingsUiState(
                    inProgress = false,
                    lastBackupMillis = BackupPrefs.lastBackupMillis(appContext),
                    message = if (action == PendingAction.BACKUP) "Backup complete." else "Restore complete.",
                )
            }
            is BackupResult.Failure -> fail(result.message)
        }
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(inProgress = false, message = message)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
