package com.griboedov.sentencecards.data.importer

import com.atilika.kuromoji.ipadic.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Not testing our own code - this pins down the exact Kuromoji IPADIC API [BookImporter] relies on
 * (surface/baseForm/reading), and doubles as a smoke test that the bundled dictionary actually
 * loads: a plain JVM unit test resolves classpath resources (`getResourceAsStream`) the same way
 * Kuromoji does at runtime, so a failure here would also mean trouble on-device.
 */
class KuromojiTokenizerTest {

    @Test
    fun `tokenizes a sentence with expected surface, base form, and reading`() {
        val tokenizer = Tokenizer.Builder().build()

        val tokens = tokenizer.tokenize("これは言葉です。")

        assertTrue(tokens.isNotEmpty())
        val surfaces = tokens.map { it.surface }
        assertEquals(listOf("これ", "は", "言葉", "です", "。"), surfaces)

        val wordToken = tokens.first { it.surface == "言葉" }
        assertEquals("言葉", wordToken.baseForm)
        assertEquals("コトバ", wordToken.reading)
    }

    @Test
    fun `resolves an inflected verb back to its dictionary form`() {
        val tokenizer = Tokenizer.Builder().build()

        val tokens = tokenizer.tokenize("食べた")
        val verb = tokens.first { it.surface == "食べ" }

        assertEquals("食べる", verb.baseForm)
    }
}
