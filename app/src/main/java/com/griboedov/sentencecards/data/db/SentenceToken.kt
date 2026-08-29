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
 * [dictForm] is this word's dictionary/base form (e.g. "食べる", "わかる"), present whenever it
 * differs from [word] - for kana words ([TokenKind.isKanaWord]) as well as [TokenKind.WORD] ones,
 * since the review screen looks a kana word up in the dictionary by its base form and わから is not
 * an entry. [dictionaryEntryId] is the bundled dictionary's matching entry id (see
 * [com.griboedov.sentencecards.data.dictionary.DictionaryEntry.id]) and is only ever resolved at
 * import time for [TokenKind.WORD] tokens - a kana word has no words-table row to seed, so its
 * entry is resolved on demand instead, the first time it's looked up or promoted. [id] is keyed on
 * [dictForm] (see `tools/import_book.py`'s
 * `stable_word_id`), and both are what a brand-new [WordEntity] gets seeded with the first time
 * this id is encountered (see `data/importer/StructuredImport.kt`), so the same word across
 * different inflections/sentences converges on one dictionary-form row instead of whichever surface
 * happened to be imported first. Both are ignored on every later token referencing an [id] that's
 * already tracked. Both are null for [TokenKind.PARTICLE] tokens, and for older imports that
 * predate these fields.
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

    /**
     * The form to look this token up by: its [dictForm] when it has one, otherwise its [word]
     * surface. Also the text a kana word is promoted into the words table under, so that repeats
     * of the same word across sentences converge on one row (see
     * [com.griboedov.sentencecards.data.repository.WordRepository.addFromDictionary]).
     */
    val baseText: String get() = dictForm ?: word
}
