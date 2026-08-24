package com.griboedov.sentencecards.data.quiz

import com.griboedov.sentencecards.data.db.WordEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun word(id: Long, word: String, furigana: String?) = WordEntity(
    id = id,
    word = word,
    furigana = furigana,
    translation = "translation$id",
)

class QuizOptionsTest {

    @Test
    fun `includes the correct reading among the options`() {
        val target = word(1, "言葉", "ことば")
        val pool = listOf(
            target,
            word(2, "語", "ご"),
            word(3, "私", "わたし"),
            word(4, "猫", "ねこ"),
        )

        val options = readingOptions(target, pool, random = Random(42))

        assertTrue(options != null && "ことば" in options)
    }

    @Test
    fun `caps options at 4 and never duplicates the correct reading as a distractor`() {
        val target = word(1, "言葉", "ことば")
        val pool = listOf(target) + (2L..20L).map { word(it, "word$it", "reading$it") }

        val options = readingOptions(target, pool, random = Random(7), maxOptions = 4)

        assertEquals(4, options?.size)
        assertEquals(1, options?.count { it == "ことば" })
    }

    @Test
    fun `returns null when the word has no furigana to quiz`() {
        val target = word(1, "です", furigana = null)

        assertNull(readingOptions(target, listOf(target)))
    }

    @Test
    fun `falls back to fewer options when the pool has no distractors`() {
        val target = word(1, "言葉", "ことば")

        val options = readingOptions(target, listOf(target), random = Random(1))

        assertEquals(listOf("ことば"), options)
    }
}
