package com.griboedov.sentencecards.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.griboedov.sentencecards.SentenceCardsApp

/**
 * Periodic time-based backup - see [SentenceCardsApp.onCreate], which schedules this via
 * WorkManager. This is the "just time based" half of the sync design; the other half is the
 * Settings screen's manual "force sync" button, which calls [DriveBackupService] directly instead
 * of going through WorkManager, for immediate feedback.
 *
 * Runs silently: if Drive access hasn't already been interactively granted (or was revoked),
 * [DriveAuthManager.requestAccess] throws since there's no Activity here to show a consent screen
 * from, and this cycle is simply skipped - it'll succeed once the user connects from Settings,
 * and the next scheduled run picks it back up regardless of this one's outcome.
 */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SentenceCardsApp
        return try {
            when (val outcome = app.driveAuthManager.requestAccess(activity = null)) {
                is DriveAuthManager.Outcome.Resolved -> {
                    val accessToken = outcome.result.accessToken
                    if (accessToken == null) {
                        Log.w(TAG, "Scheduled Drive backup skipped: no access token returned")
                        return Result.failure()
                    }
                    when (val result = app.driveBackupService.backup(accessToken)) {
                        is BackupResult.Success -> {
                            BackupPrefs.setLastBackup(applicationContext, result.timestampMillis)
                            Result.success()
                        }
                        is BackupResult.Failure -> {
                            Log.w(TAG, "Scheduled Drive backup failed: ${result.message}")
                            Result.failure()
                        }
                    }
                }
                is DriveAuthManager.Outcome.NeedsResolution -> {
                    Log.i(TAG, "Scheduled Drive backup skipped: Drive access not yet granted (connect it from Settings)")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Scheduled Drive backup failed", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "DriveBackupWorker"
    }
}
