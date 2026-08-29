package com.griboedov.sentencecards.data.backup

import android.util.Log
import androidx.room.withTransaction
import com.griboedov.sentencecards.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Result of one backup or restore attempt. Unlike
 * [com.griboedov.sentencecards.data.translation.Translator] (which silently collapses every
 * failure to null, since a missing translation is a non-event), a failed backup/restore needs to
 * be visible to the user, so failures carry a message instead.
 */
sealed class BackupResult {
    data class Success(val timestampMillis: Long) : BackupResult()
    data class Failure(val message: String) : BackupResult()
}

/**
 * Reads/writes a [BackupSnapshot] as a single fixed-name JSON file ("backup.json") in the
 * caller's Drive "app data" folder - a per-user hidden storage area only this app can see,
 * addressed via the Drive v3 REST API using an OAuth access token scoped to `drive.appdata` (see
 * [DriveAuthManager]). Plain [HttpURLConnection] calls, matching
 * [com.griboedov.sentencecards.data.translation.DeepLTranslator]'s style - no HTTP client
 * dependency needed for this small a surface.
 *
 * [backup] always deletes+recreates the file rather than PATCHing its content in place: the
 * JDK's [HttpURLConnection] doesn't reliably support the PATCH method Drive's update-content
 * endpoint expects, and this data is small enough that the extra round trip doesn't matter.
 */
class DriveBackupService(private val database: AppDatabase) {
    private val cardDao get() = database.cardDao()
    private val wordDao get() = database.wordDao()

    suspend fun backup(accessToken: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val snapshot = BackupSnapshot(
                exportedAtEpochMillis = System.currentTimeMillis(),
                cards = cardDao.getAll(),
                wordProgress = wordDao.getProgress(),
            )
            val existingFileId = findBackupFileId(accessToken)
            if (existingFileId != null) deleteFile(accessToken, existingFileId)
            createFile(accessToken, json.encodeToString(BackupSnapshot.serializer(), snapshot))
            BackupResult.Success(snapshot.exportedAtEpochMillis)
        } catch (e: Exception) {
            Log.w(TAG, "Drive backup failed", e)
            BackupResult.Failure(e.message ?: "Backup failed")
        }
    }

    suspend fun restore(accessToken: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val fileId = findBackupFileId(accessToken)
                ?: return@withContext BackupResult.Failure("No backup found in Google Drive yet.")
            val snapshot = json.decodeFromString(BackupSnapshot.serializer(), downloadFile(accessToken, fileId))
            database.withTransaction {
                cardDao.deleteAll()
                cardDao.upsertAll(snapshot.cards)
                for (progress in snapshot.wordProgress) {
                    wordDao.updateProgress(progress.wordId, progress.toLearn, progress.forceFurigana, progress.quizSuccess, progress.quizFails)
                }
            }
            BackupResult.Success(snapshot.exportedAtEpochMillis)
        } catch (e: Exception) {
            Log.w(TAG, "Drive restore failed", e)
            BackupResult.Failure(e.message ?: "Restore failed")
        }
    }

    private fun findBackupFileId(accessToken: String): String? {
        val query = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val connection = openConnection("$FILES_URL?spaces=appDataFolder&q=$query&fields=files(id)", "GET", accessToken)
        try {
            ensureSuccessful(connection) { "list backup file" }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString(FileListResponse.serializer(), body).files.firstOrNull()?.id
        } finally {
            connection.disconnect()
        }
    }

    private fun deleteFile(accessToken: String, fileId: String) {
        val connection = openConnection("$FILES_URL/$fileId", "DELETE", accessToken)
        try {
            ensureSuccessful(connection) { "delete previous backup file" }
        } finally {
            connection.disconnect()
        }
    }

    private fun createFile(accessToken: String, contentJson: String) {
        val boundary = "backup-${UUID.randomUUID()}"
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(contentJson).append("\r\n")
            append("--$boundary--")
        }
        val connection = openConnection("$UPLOAD_URL?uploadType=multipart", "POST", accessToken)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            ensureSuccessful(connection) { "upload backup file" }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(accessToken: String, fileId: String): String {
        val connection = openConnection("$FILES_URL/$fileId?alt=media", "GET", accessToken)
        try {
            ensureSuccessful(connection) { "download backup file" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String, accessToken: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

    /** Throws with the response body (Drive's error JSON is human-readable) if [connection]'s response wasn't 2xx. */
    private fun ensureSuccessful(connection: HttpURLConnection, action: () -> String) {
        val code = connection.responseCode
        if (code < 200 || code >= 300) {
            val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            throw IOException("Failed to ${action()}: HTTP $code $errorBody")
        }
    }

    @Serializable
    private data class FileListResponse(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class DriveFile(val id: String)

    private companion object {
        const val TAG = "DriveBackupService"
        const val TIMEOUT_MS = 15_000
        const val FILE_NAME = "backup.json"
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

        // Same reasoning as DeepLTranslator/Converters: tolerate unknown fields so a future
        // schema change doesn't break restoring an older backup.
        val json = Json { ignoreUnknownKeys = true }
    }
}
