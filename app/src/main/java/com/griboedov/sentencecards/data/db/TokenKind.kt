package com.griboedov.sentencecards.data.db

/**
 * Kind of a token inside a parsed sentence [SentenceToken.kind].
 *
 * The README only specifies numeric codes by example (1, 2, 3); this is our reading of them:
 *  - WORD: a kanji-containing word/kanji that gets tracked in the words/kanji database.
 *  - PARTICLE: grammar - particles, copulas, punctuation, hiragana-only words. Per the README,
 *    hiragana/katakana words are assumed already known and are never added to the word database.
 *  - KATAKANA: katakana loanwords. Also not tracked, kept as its own kind in case the UI ever
 *    wants to style them differently (e.g. gairaigo).
 */
enum class TokenKind(val code: Int) {
    WORD(1),
    PARTICLE(2),
    KATAKANA(3);

    companion object {
        fun fromCode(code: Int): TokenKind = entries.firstOrNull { it.code == code } ?: PARTICLE
    }
}
