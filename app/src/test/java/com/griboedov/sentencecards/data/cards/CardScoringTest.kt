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
    fun `a word missing from the words table scores +2`() {
        val score = scoreSentenceForWord(listOf(wordToken(1)), wordsById = emptyMap())
        assertEquals(2, score)
    }

    @Test
    fun `a known word still showing forced furigana scores +1`() {
        val words = mapOf(1L to word(1, toLearn = false, forceFurigana = true))
        assertEquals(1, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `a well-known word with no forced furigana scores +2`() {
        val words = mapOf(1L to word(1, toLearn = false, forceFurigana = false))
        assertEquals(2, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `another to-learn word scores 0`() {
        val words = mapOf(1L to word(1, toLearn = true, forceFurigana = true))
        assertEquals(0, scoreSentenceForWord(listOf(wordToken(1)), words))
    }

    @Test
    fun `particles never contribute to the score`() {
        val score = scoreSentenceForWord(listOf(particleToken(), particleToken()), wordsById = emptyMap())
        assertEquals(0, score)
    }

    @Test
    fun `score sums every word token in the sentence`() {
        val words = mapOf(
            1L to word(1, toLearn = false, forceFurigana = false), // +2
            2L to word(2, toLearn = true), // +0
        )
        val structure = listOf(wordToken(1), particleToken(), wordToken(2), wordToken(3)) // 3 is unknown: +2
        assertEquals(4, scoreSentenceForWord(structure, words))
    }

    @Test
    fun `pickBestSentences returns the highest scoring candidates first, limited to n`() {
        val words = mapOf(
            1L to word(1, toLearn = false, forceFurigana = false), // +2 each occurrence
            2L to word(2, toLearn = true), // +0
        )
        val worst = sentence(1, listOf(wordToken(2))) // score 0
        val mid = sentence(2, listOf(wordToken(1))) // score 2
        val best = sentence(3, listOf(wordToken(1), wordToken(1))) // score 4

        val picked = pickBestSentences(listOf(worst, mid, best), words, limit = 2)

        assertEquals(listOf(best.id, mid.id), picked.map { it.id })
    }

    @Test
    fun `pickBestSentences breaks ties by shorter structure, then by id`() {
        val short = sentence(1, listOf(wordToken(1)))
        val long = sentence(2, listOf(wordToken(1), particleToken()))
        val shortHigherId = sentence(3, listOf(wordToken(1)))

        val picked = pickBestSentences(listOf(long, short, shortHigherId), wordsById = emptyMap(), limit = 3)

        assertEquals(listOf(short.id, shortHigherId.id, long.id), picked.map { it.id })
    }
}
