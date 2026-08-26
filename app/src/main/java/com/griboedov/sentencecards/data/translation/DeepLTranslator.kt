package com.griboedov.sentencecards.data.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [Translator] backed by the DeepL API (https://www.deepl.com/docs-api/translate-text/).
 *
 * The API key is supplied at build time via the `DEEPL_API_KEY` environment variable - see
 * `app/build.gradle.kts`, which exposes it as `BuildConfig.DEEPL_API_KEY` - rather than committed
 * to source. A free-tier key (DeepL always suffixes those with ":fx") is routed to the free API
 * host, any other key to the paid one, matching DeepL's own convention.
 */
class DeepLTranslator(private val apiKey: String) : Translator {

    override suspend fun translate(text: String): String? {
        if (text.isBlank()) return null
        if (apiKey.isBlank()) {
            // The single most likely failure mode in practice - DEEPL_API_KEY wasn't set (or
            // wasn't picked up - see the Gradle daemon note on BuildConfig.DEEPL_API_KEY) when the
            // app was built - so it gets its own explicit log line rather than silently returning
            // null same as every other failure below.
            Log.w(TAG, "DeepL translation skipped: no API key (BuildConfig.DEEPL_API_KEY is blank)")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                requestTranslation(text)
            } catch (e: Exception) {
                Log.w(TAG, "DeepL translation request failed", e)
                null
            }
        }
    }

    private fun requestTranslation(text: String): String? {
        val host = if (apiKey.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        val body = "text=${URLEncoder.encode(text, "UTF-8")}&source_lang=JA&target_lang=EN"

        val connection = URL("https://$host/v2/translate").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key $apiKey")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                // DeepL's error responses carry a `{"message": "..."}` body that's usually exactly
                // what explains the failure (bad key, wrong host for the key's tier, quota
                // exceeded, ...) - worth logging in full rather than just the bare status code.
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Log.w(TAG, "DeepL translation failed: HTTP ${connection.responseCode} $errorBody")
                return null
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val translated = json.decodeFromString<DeepLResponse>(response).translations.firstOrNull()?.text
            if (translated == null) Log.w(TAG, "DeepL translation failed: no translation in response: $response")
            return translated
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class DeepLResponse(val translations: List<DeepLTranslation> = emptyList())

    @Serializable
    private data class DeepLTranslation(val text: String)

    private companion object {
        const val TAG = "DeepLTranslator"
        const val TIMEOUT_MS = 10_000

        // The default Json instance rejects unknown keys - DeepL's response includes fields
        // (e.g. detected_source_language) this app has no use for beyond translations[].text, so
        // that would otherwise fail every single translation.
        val json = Json { ignoreUnknownKeys = true }
    }
}
