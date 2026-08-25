package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordEntity

/**
 * Implements the README's "Adding word to learn" sentence-selection algorithm: pure, DB-free
 * scoring so it's easy to unit test (see [com.griboedov.sentencecards.data.cards.CardScoringTest]),
 * matching [com.griboedov.sentencecards.data.queue.QueueEngine]'s style.
 *
 * Only kind=WORD tokens are scored - particles/katakana are never rows in the words table by
 * design (see [TokenKind]), so scoring them would just reward long sentences full of particles.
 * Bonus per kind=WORD token, per the spec:
 *  - not present in the words table at all: +2 (brand new, nothing already being learned there)
 *  - present, not to-learn, still force-furigana (freshly known, still shaky): +1
 *  - present, not to-learn, furigana no longer forced (well known): +2
 *  - present and to-learn (another word also being actively learned): +0 (discouraged - the goal
 *    is a sentence that isolates the *one* new word, not several at once)
 */
fun scoreSentenceForWord(structure: List<SentenceToken>, wordsById: Map<Long, WordEntity>): Int =
    structure.sumOf { token -> tokenBonus(token, wordsById) }

private fun tokenBonus(token: SentenceToken, wordsById: Map<Long, WordEntity>): Int {
    if (token.tokenKind != TokenKind.WORD) return 0
    val word = token.id?.let { wordsById[it] } ?: return 2
    return when {
        word.toLearn -> 0
        word.forceFurigana -> 1
        else -> 2
    }
}

/**
 * Picks the [limit] best-scoring [candidates] (highest [scoreSentenceForWord] first). Ties break
 * towards the shorter sentence (fewer tokens - simpler to review), then by id for determinism.
 */
fun pickBestSentences(
    candidates: List<SentenceEntity>,
    wordsById: Map<Long, WordEntity>,
    limit: Int = 3,
): List<SentenceEntity> {
    val scores = candidates.associateWith { scoreSentenceForWord(it.structure, wordsById) }
    return candidates
        .sortedWith(
            compareByDescending<SentenceEntity> { scores.getValue(it) }
                .thenBy { it.structure.size }
                .thenBy { it.id },
        )
        .take(limit)
}
