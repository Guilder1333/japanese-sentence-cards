package com.griboedov.sentencecards.data.db

import kotlinx.serialization.Serializable

/**
 * One token of a parsed sentence structure, matching the JSON schema shown in the README:
 *
 * ```json
 * { "word": "言葉", "furigana": "ことば", "kind": 1, "id": 1234 }
 * ```
 *
 * [id] is only present/meaningful for [TokenKind.WORD] tokens - it references the row in the
 * words/kanji database this token was resolved to.
 *
 * [word] is this token's surface form - however it's actually inflected in the sentence (e.g.
 * "食べた") - since that's what has to be rendered on the card. [furigana] is this same occurrence's
 * actual (possibly inflected) reading, needed for the same reason - it's what's drawn as ruby text
 * over [word], and can't be recovered from the tracked word alone (見た's reading isn't 見る's).
 *
 * [dictForm] and [dictionaryEntryId] are the same word's dictionary/base form (e.g. "食べる") and
 * the bundled dictionary's matching entry id (see
 * [com.griboedov.sentencecards.data.dictionary.DictionaryEntry.id]) - present only for
 * [TokenKind.WORD] tokens. [id] is keyed on [dictForm] (see `tools/import_book.py`'s
 * `stable_word_id`), and both are what a brand-new [WordEntity] gets seeded with the first time
 * this id is encountered (see `data/importer/StructuredImport.kt`), so the same word across
 * different inflections/sentences converges on one dictionary-form row instead of whichever surface
 * happened to be imported first. Both are ignored on every later token referencing an [id] that's
 * already tracked - null for non-WORD tokens, and for older imports that predate these fields.
 */
@Serializable
data class SentenceToken(
    val word: String,
    val kind: Int,
    val furigana: String? = null,
    val id: Long? = null,
    val dictForm: String? = null,
    val dictionaryEntryId: Long? = null,
) {
    val tokenKind: TokenKind get() = TokenKind.fromCode(kind)
}
