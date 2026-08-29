package com.griboedov.sentencecards.data.importer

import com.atilika.kuromoji.ipadic.Tokenizer
import com.griboedov.sentencecards.data.db.TokenKind
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

    @Test
    fun `extractSearchTerms splits a sentence into its content words' dictionary forms`() {
        val tokenizer = Tokenizer.Builder().build()

        // 猫(cat)/と(and, particle)/犬(dog)/が(subject marker, particle)/好き(fond of)/です(copula)/。
        val terms = extractSearchTerms(tokenizer.tokenize("猫と犬が好きです。"), fallbackTerm = "猫と犬が好きです。")

        assertEquals(listOf("猫", "犬", "好き"), terms)
    }

    @Test
    fun `extractSearchTerms resolves an inflected verb to its dictionary form, not the inflected surface`() {
        val tokenizer = Tokenizer.Builder().build()

        val terms = extractSearchTerms(tokenizer.tokenize("見た"), fallbackTerm = "見た")

        assertEquals(listOf("見る"), terms)
    }

    @Test
    fun `extractSearchTerms keeps a katakana loanword by its surface form`() {
        val tokenizer = Tokenizer.Builder().build()

        val terms = extractSearchTerms(tokenizer.tokenize("コーヒー"), fallbackTerm = "コーヒー")

        assertEquals(listOf("コーヒー"), terms)
    }

    @Test
    fun `extractSearchTerms falls back to the whole text when nothing tokenizes into a trackable word`() {
        val tokenizer = Tokenizer.Builder().build()

        // No kanji, no katakana - an English meaning query has nothing for the tokenizer to split.
        val terms = extractSearchTerms(tokenizer.tokenize("eat"), fallbackTerm = "eat")

        assertEquals(listOf("eat"), terms)
    }

    @Test
    fun `classifyToken separates kana content words from grammar on real tokenizer output`() {
        val tokenizer = Tokenizer.Builder().build()

        val kinds = tokenizer.tokenize("ぼくはちょっとしらべてみたけれど、なにもわからなかった。")
            .associate { it.surface to classifyToken(it) }

        // Content words that happen to be written in kana.
        assertEquals(TokenKind.HIRAGANA, kinds["ぼく"])    // 名詞/代名詞
        assertEquals(TokenKind.HIRAGANA, kinds["ちょっと"]) // 副詞
        assertEquals(TokenKind.HIRAGANA, kinds["しらべ"])  // 動詞/自立
        assertEquals(TokenKind.HIRAGANA, kinds["わから"])  // 動詞/自立
        // Actual grammar, in exactly the same script.
        assertEquals(TokenKind.PARTICLE, kinds["は"])     // 助詞
        assertEquals(TokenKind.PARTICLE, kinds["て"])     // 助詞
        assertEquals(TokenKind.PARTICLE, kinds["た"])     // 助動詞
        assertEquals(TokenKind.PARTICLE, kinds["み"])     // 動詞/非自立 - the みる of ～てみる
        assertEquals(TokenKind.PARTICLE, kinds["。"])     // 記号
    }

    @Test
    fun `extractSearchTerms keeps a kana-written content word by its dictionary form`() {
        val tokenizer = Tokenizer.Builder().build()

        // わからなかった -> わかる; が/なかっ/た are grammar and stay out.
        val terms = extractSearchTerms(tokenizer.tokenize("わからなかった"), fallbackTerm = "わからなかった")

        assertEquals(listOf("わかる"), terms)
    }

    @Test
    fun `extractSearchTerms deduplicates repeated words, keeping first-occurrence order`() {
        val tokenizer = Tokenizer.Builder().build()

        val terms = extractSearchTerms(tokenizer.tokenize("猫が猫を見た。"), fallbackTerm = "猫が猫を見た。")

        assertEquals(listOf("猫", "見る"), terms)
    }
}
