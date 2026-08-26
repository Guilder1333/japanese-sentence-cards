package com.griboedov.sentencecards.data.translation

/**
 * Translates Japanese sentence text to English - used by
 * [com.griboedov.sentencecards.data.cards.CardGenerator] to fill in a
 * [com.griboedov.sentencecards.data.db.SentenceEntity]'s missing
 * [translation][com.griboedov.sentencecards.data.db.SentenceEntity.translation] at the point a
 * sentence is actually picked for a card, rather than eagerly for the whole (potentially enormous)
 * sentence pool.
 */
interface Translator {
    /** Returns the translation, or null if unavailable - no API key configured, or a network/API failure. */
    suspend fun translate(text: String): String?
}

/** Used when no API key is configured - translation is silently skipped rather than failing card generation. */
object NoOpTranslator : Translator {
    override suspend fun translate(text: String): String? = null
}
