package com.griboedov.sentencecards.data.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps Play Services' Authorization API to obtain an OAuth access token scoped to
 * `drive.appdata` - a hidden per-user Drive folder only this app can read/write. That narrow
 * scope is the entire "protection" story for [DriveBackupService]: no custom backend or API-key
 * management needed, just this token.
 *
 * The underlying `AuthorizationClient.authorize()` call is Task-based; [requestAccess] wraps it
 * as a suspend function rather than pulling in `kotlinx-coroutines-play-services` for one call
 * site. If the OS needs to show a consent screen (first use, or a previously granted scope was
 * revoked), the result is [Outcome.NeedsResolution] carrying the [PendingIntent] to launch - the
 * caller (a Composable, via `ActivityResultContracts.StartIntentSenderForResult`) launches it and
 * feeds the resulting [Intent] back through [resultFromIntent].
 */
class DriveAuthManager(private val context: Context) {

    sealed class Outcome {
        data class Resolved(val result: AuthorizationResult) : Outcome()
        data class NeedsResolution(val pendingIntent: PendingIntent) : Outcome()
    }

    /**
     * [activity] is only needed to build the consent [PendingIntent] if resolution turns out to
     * be required - pass null from a context with no UI (e.g. [DriveBackupWorker]'s scheduled
     * runs) to fail fast with [IllegalStateException] in that case, rather than returning an
     * [Outcome.NeedsResolution] with nothing able to launch it.
     */
    suspend fun requestAccess(activity: Activity?): Outcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_APPDATA)))
            .build()
        val client = if (activity != null) Identity.getAuthorizationClient(activity) else Identity.getAuthorizationClient(context)
        val result = suspendCancellableCoroutine { cont ->
            client.authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        if (!result.hasResolution()) return Outcome.Resolved(result)

        val pendingIntent = result.pendingIntent
        if (activity == null || pendingIntent == null) {
            error("Google Drive access needs interactive consent, which isn't available here")
        }
        return Outcome.NeedsResolution(pendingIntent)
    }

    /** Extracts the [AuthorizationResult] (with its access token) from the Intent an interactive [requestAccess] resolution launch returned. */
    fun resultFromIntent(intent: Intent): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)

    companion object {
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}
