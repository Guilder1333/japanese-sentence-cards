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
 *
 * The raw bonus sum is normalized to a 0..1 fraction of the maximum possible bonus (+2 per
 * kind=WORD token), so sentences with different word counts are actually comparable - otherwise a
 * long sentence full of well-known words would always outscore a short, tightly-focused one purely
 * by having more tokens to rack up bonus on. A sentence with no kind=WORD tokens at all scores 0.
 */
fun scoreSentenceForWord(structure: List<SentenceToken>, wordsById: Map<Long, WordEntity>): Float {
    val wordCount = wordTokenCount(structure)
    if (wordCount == 0) return 0f
    val bonusSum = structure.sumOf { token -> tokenBonus(token, wordsById) }
    return bonusSum.toFloat() / (wordCount * 2)
}

private fun tokenBonus(token: SentenceToken, wordsById: Map<Long, WordEntity>): Int {
    if (token.tokenKind != TokenKind.WORD) return 0
    val word = token.id?.let { wordsById[it] } ?: return 2
    return when {
        word.toLearn -> 0
        word.forceFurigana -> 1
        else -> 2
    }
}

private fun wordTokenCount(structure: List<SentenceToken>): Int =
    structure.count { it.tokenKind == TokenKind.WORD }

/**
 * Picks the [limit] best-scoring [candidates] (highest [scoreSentenceForWord] first). Ties break
 * towards fewer words (kind=WORD tokens, not raw structure size - a sentence isn't "longer" for
 * carrying more particles - simpler to review), then by id for determinism.
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
                .thenBy { wordTokenCount(it.structure) }
                .thenBy { it.id },
        )
        .take(limit)
}
