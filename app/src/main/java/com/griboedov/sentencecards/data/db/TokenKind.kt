package com.griboedov.sentencecards.data.db

/**
 * Kind of a token inside a parsed sentence [SentenceToken.kind].
 *
 * The README only specifies numeric codes by example (1, 2, 3); this is our reading of them, plus
 * HIRAGANA (4) which we added:
 *  - WORD: a kanji-containing word/kanji that gets tracked in the words/kanji database.
 *  - PARTICLE: pure grammar - particles, auxiliaries/copulas, punctuation, and the dependent
 *    helper verbs/suffixes that only ever glue other words together (the いる of ～ている, the
 *    さん of 田中さん).
 *  - KATAKANA: katakana loanwords. Not tracked, kept as its own kind so the UI can style them
 *    differently (e.g. gairaigo).
 *  - HIRAGANA: a kana-written *content* word - a verb, adjective, adverb, noun or pronoun that
 *    happens to be spelled without kanji (わかる, きれい, とても, ぼく). Like KATAKANA it is not
 *    tracked in the word database (per the README, kana words are assumed already known), but it
 *    is deliberately kept apart from PARTICLE: "は" and "わからない" are not the same thing, and
 *    conflating them means the UI can't style them apart and the dictionary can't search them.
 *
 * Distinguishing HIRAGANA from PARTICLE needs part-of-speech information, not just the surface
 * script - see [com.griboedov.sentencecards.data.importer.classifyToken].
 */
enum class TokenKind(val code: Int) {
    WORD(1),
    PARTICLE(2),
    KATAKANA(3),
    HIRAGANA(4);

    /**
     * Whether this token is a *word* rather than grammar - true for [WORD] and for the two kana
     * kinds, false for [PARTICLE]. The kana kinds aren't tracked in the words table on import, but
     * they are still real vocabulary: the review screen gives them the same dictionary lookup and
     * 4-direction menu a [WORD] gets, and marking one known/to-learn there promotes it into the
     * words table (see [com.griboedov.sentencecards.ui.review.ReviewViewModel.onWordDirection]).
     */
    val isWord: Boolean get() = this != PARTICLE

    /**
     * Whether this token is a word written entirely in kana ([KATAKANA] or [HIRAGANA]). These
     * behave like words everywhere except furigana: there is no reading to annotate a kana word
     * with, so "force furigana" is meaningless for them and is disabled in the 4-direction menu
     * (see [com.griboedov.sentencecards.ui.review.FlickMenu]) - even once the word has been
     * promoted into the words table, since that's a property of the script, not of tracking.
     */
    val isKanaWord: Boolean get() = this == KATAKANA || this == HIRAGANA

    companion object {
        fun fromCode(code: Int): TokenKind = entries.firstOrNull { it.code == code } ?: PARTICLE
    }
}
