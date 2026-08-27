package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

private fun word(id: Long, toLearn: Boolean = false, forceFurigana: Boolean = false) = WordEntity(
    id = id,
    word = "word$id",
    furigana = null,
    translation = "t$id",
    toLearn = toLearn,
    forceFurigana = forceFurigana,
)

private fun wordToken(id: Long) = SentenceToken(word = "w$id", translation = "", kind = 1, id = id)
private fun particleToken() = SentenceToken(word = "は", translation = "topic marker", kind = 2)

private fun sentence(id: Long, structure: List<SentenceToken>) = SentenceEntity(
    id = id,
    text = "text$id",
    translation = "translation$id",
    structure = structure,
)

class CardScoringTest {

    @Test
    fun `a word missing from the words table scores 1 (bonus 2 of a possible 2)`() {
        val score = scoreSentenceForWord(listOf(wordToken(1)), wordsById = emptyMap())
        assertEquals(1f, score)
    }

    @Test
    fun `a known word still showing forced furigana scores 0point5 (bonus 1 of a possible 2)`() {
        val words = mapOf(1L to word(1, toLearn = false, forceFurigana = true))
        assertEquals(0.5f, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `a well-known word with no forced furigana scores 1 (bonus 2 of a possible 2)`() {
        val words = mapOf(1L to word(1, toLearn = false, forceFurigana = false))
        assertEquals(1f, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `another to-learn word scores 0`() {
        val words = mapOf(1L to word(1, toLearn = true, forceFurigana = true))
        assertEquals(0f, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `particles never contribute to the score, and a sentence with none scores 0`() {
        val score = scoreSentenceForWord(listOf(particleToken(), particleToken()), wordsById = emptyMap())
        assertEquals(0f, score)
    }

    @Test
    fun `score is the bonus sum normalized by the max possible bonus (2 per word token)`() {
        val words = mapOf(
            1L to word(1, toLearn = false, forceFurigana = false), // +2
            2L to word(2, toLearn = true), // +0
        )
        // 3 word tokens (1, 2, 3 - particles don't count towards the word total): max bonus 6.
        val structure = listOf(wordToken(1), particleToken(), wordToken(2), wordToken(3)) // 3 is unknown: +2
        assertEquals(4f / 6f, scoreSentenceForWord(structure, words))
    }

    @Test
    fun `a shorter sentence can outscore a longer one carrying the same bonus`() {
        val words = mapOf(
            1L to word(1, toLearn = false, forceFurigana = false), // +2
            2L to word(2, toLearn = true), // +0 - another to-learn word dilutes the longer sentence
        )
        // Both sentences have the same raw bonus sum (+2), but the short one packs it into a
        // single word instead of diluting it across an extra 0-scoring word.
        val short = scoreSentenceForWord(listOf(wordToken(1)), words) // 2/2
        val long = scoreSentenceForWord(listOf(wordToken(1), wordToken(2)), words) // (2+0)/4
        assertEquals(1f, short)
        assertEquals(0.5f, long)
    }

    @Test
    fun `pickBestSentences returns the highest scoring candidates first, limited to n`() {
        val words = mapOf(
            1L to word(1, toLearn = false, forceFurigana = false), // +2 each occurrence
            2L to word(2, toLearn = true), // +0
        )
        val worst = sentence(1, listOf(wordToken(2))) // score 0
        val mid = sentence(2, listOf(wordToken(1))) // score 1
        val best = sentence(3, listOf(wordToken(1), wordToken(1))) // score 1, but tied with mid - see below

        val picked = pickBestSentences(listOf(worst, mid, best), words, limit = 2)

        assertEquals(listOf(mid.id, best.id), picked.map { it.id })
    }

    @Test
    fun `pickBestSentences breaks ties by fewer word tokens, then by id`() {
        val short = sentence(1, listOf(wordToken(1)))
        val long = sentence(2, listOf(wordToken(1), wordToken(1)))
        val shortHigherId = sentence(3, listOf(wordToken(1)))

        val picked = pickBestSentences(listOf(long, short, shortHigherId), wordsById = emptyMap(), limit = 3)

        assertEquals(listOf(short.id, shortHigherId.id, long.id), picked.map { it.id })
    }

    @Test
    fun `pickBestSentences tiebreak counts word tokens, not structure size, so particle padding doesn't matter`() {
        // Same single word token in both, but one is padded with particles. structure.size differs
        // (3 vs 1) yet word count doesn't (1 each), so this should tie all the way through to id,
        // not favor the sentence with fewer raw tokens.
        val paddedWithParticles = sentence(1, listOf(wordToken(1), particleToken(), particleToken()))
        val bareWord = sentence(2, listOf(wordToken(1)))

        val picked = pickBestSentences(listOf(paddedWithParticles, bareWord), wordsById = emptyMap(), limit = 2)

        assertEquals(listOf(paddedWithParticles.id, bareWord.id), picked.map { it.id })
    }
}
