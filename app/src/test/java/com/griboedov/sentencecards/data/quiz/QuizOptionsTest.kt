package com.griboedov.sentencecards.data.quiz

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizOptionsTest {

    @Test
    fun `includes the correct reading among the options`() {
        val pool = listOf("ご", "わたし", "ねこ")

        val options = readingOptions("ことば", pool, random = Random(42))

        assertTrue(options != null && "ことば" in options)
    }

    @Test
    fun `caps options at 4 and never duplicates the correct reading as a distractor`() {
        val pool = (2L..20L).map { "reading$it" }

        val options = readingOptions("ことば", pool, random = Random(7), maxOptions = 4)

        assertEquals(4, options?.size)
        assertEquals(1, options?.count { it == "ことば" })
    }

    @Test
    fun `returns null when there's no correct reading to quiz`() {
        assertNull(readingOptions("", listOf("ことば")))
    }

    @Test
    fun `falls back to fewer options when the pool has no distractors`() {
        val options = readingOptions("ことば", emptyList(), random = Random(1))

        assertEquals(listOf("ことば"), options)
    }
}
