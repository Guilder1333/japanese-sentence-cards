package com.griboedov.sentencecards.data.backup

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for the one thing the Settings screen needs regardless of
 * whether the last backup ran from the periodic [DriveBackupWorker] or a manual force-sync: when
 * it last actually succeeded.
 */
object BackupPrefs {
    private const val PREFS_NAME = "drive_backup"
    private const val KEY_LAST_BACKUP_MILLIS = "last_backup_millis"

    fun setLastBackup(context: Context, timestampMillis: Long) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP_MILLIS, timestampMillis).apply()
    }

    fun lastBackupMillis(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_BACKUP_MILLIS, -1L).takeIf { it >= 0 }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
