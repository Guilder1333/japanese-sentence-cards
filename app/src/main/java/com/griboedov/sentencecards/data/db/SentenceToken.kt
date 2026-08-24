package com.griboedov.sentencecards.data.db

import kotlinx.serialization.Serializable

/**
 * One token of a parsed sentence structure, matching the JSON schema shown in the README:
 *
 * ```json
 * { "word": "言葉", "furigana": "ことば", "translation": "word", "kind": 1, "id": 1234 }
 * ```
 *
 * [id] is only present/meaningful for [TokenKind.WORD] tokens - it references the row in the
 * words/kanji database this token was resolved to.
 */
@Serializable
data class SentenceToken(
    val word: String,
    val translation: String,
    val kind: Int,
    val furigana: String? = null,
    val id: Long? = null,
) {
    val tokenKind: TokenKind get() = TokenKind.fromCode(kind)
}
