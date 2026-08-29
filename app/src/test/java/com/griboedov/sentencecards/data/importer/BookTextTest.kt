package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTextTest {

    @Test
    fun `splits plain sentences on terminators`() {
        val sentences = splitSentences("これは文です。それも文です！最後の文は？")

        assertEquals(listOf("これは文です。", "それも文です！", "最後の文は？"), sentences)
    }

    @Test
    fun `does not split on a terminator inside a quote`() {
        val sentences = splitSentences("彼は「ああ、そうだ。」と言った。")

        assertEquals(listOf("彼は「ああ、そうだ。」と言った。"), sentences)
    }

    @Test
    fun `treats a missing closing quote at a line boundary as closed, so later sentences aren't swallowed`() {
        // The first line's 「 never actually closes (simulating an OCR-dropped closing quote), but
        // the next line opens another one - assume the missing close happened right at the
        // boundary. Without that, depth would stay permanently unbalanced and every terminator
        // from here on (well within MAX_UNCLOSED_QUOTE_CHARS, so the safety valve doesn't save it)
        // would be swallowed into one ever-growing sentence - in particular the final 花が咲いた。
        // would never split off on its own.
        val text = "彼は「ああ、そうだ\n「これは新しい文だ。」と言った。花が咲いた。"

        val sentences = splitSentences(text)

        assertEquals(2, sentences.size)
        assertEquals("花が咲いた。", sentences.last())
    }

    @Test
    fun `splits a multi-sentence quote into its own sentences and drops the narration`() {
        val raw = "彼は「おはよう。元気？」と言った。"

        val split = splitLongQuotes(raw)

        assertEquals(listOf("おはよう。", "元気？"), split)
    }

    @Test
    fun `leaves a single-sentence quote merged with its narration`() {
        val raw = "彼は「おはよう。」と言った。"

        assertEquals(listOf(raw), splitLongQuotes(raw))
    }

    @Test
    fun `clean sentence strips all whitespace, not just leading and trailing`() {
        assertEquals("これは文です。", cleanSentence("これは \n 文です。 "))
    }

    @Test
    fun `japanese ratio counts kanji kana and the prolonged sound mark`() {
        assertEquals(1.0, japaneseRatio("これは日本語です"), 0.0001)
        assertEquals(0.0, japaneseRatio("English"), 0.0001)
        assertEquals(1.0, japaneseRatio("123、。！"), 0.0001) // no letters at all -> vacuously 1.0
    }

    @Test
    fun `classifies a kanji-containing word as WORD`() {
        assertEquals(TokenKind.WORD, classifyToken("言葉"))
    }

    @Test
    fun `classifies a katakana-only word as KATAKANA`() {
        assertEquals(TokenKind.KATAKANA, classifyToken("イギリス"))
    }

    @Test
    fun `classifies hiragana-only and punctuation as PARTICLE when no part-of-speech is given`() {
        assertEquals(TokenKind.PARTICLE, classifyToken("は"))
        assertEquals(TokenKind.PARTICLE, classifyToken("。"))
    }

    @Test
    fun `part-of-speech tells a kana-written word apart from a particle`() {
        // Same script, opposite kinds - this is the whole reason classifyToken takes a POS.
        assertEquals(TokenKind.PARTICLE, classifyToken("が", "助詞", "格助詞"))
        assertEquals(TokenKind.HIRAGANA, classifyToken("わかる", "動詞", "自立"))
        assertEquals(TokenKind.HIRAGANA, classifyToken("きれい", "名詞", "形容動詞語幹"))
        assertEquals(TokenKind.HIRAGANA, classifyToken("とても", "副詞", "助詞類接続"))
    }

    @Test
    fun `classifies auxiliaries, punctuation and dependent helpers as PARTICLE`() {
        assertEquals(TokenKind.PARTICLE, classifyToken("です", "助動詞", "*"))
        assertEquals(TokenKind.PARTICLE, classifyToken("。", "記号", "句点"))
        // The いる of ～ている and the さん of 田中さん only exist to attach to another word.
        assertEquals(TokenKind.PARTICLE, classifyToken("いる", "動詞", "非自立"))
        assertEquals(TokenKind.PARTICLE, classifyToken("さん", "名詞", "接尾"))
    }

    @Test
    fun `kanji and katakana are still decided by script, whatever the part-of-speech says`() {
        assertEquals(TokenKind.WORD, classifyToken("言葉", "名詞", "一般"))
        // A kanji suffix stays a tracked word even though 接尾 would otherwise mean grammar.
        assertEquals(TokenKind.WORD, classifyToken("的", "名詞", "接尾"))
        assertEquals(TokenKind.KATAKANA, classifyToken("イギリス", "名詞", "固有名詞"))
    }

    @Test
    fun `latin and digits stay PARTICLE even with a content part-of-speech`() {
        assertEquals(TokenKind.PARTICLE, classifyToken("eat", "名詞", "一般"))
        assertEquals(TokenKind.PARTICLE, classifyToken("123", "名詞", "数"))
    }

    @Test
    fun `both kana kinds count as words, and as kana words`() {
        // What makes the review screen give them the dictionary and the 4-direction menu, and
        // what makes it withhold "force furigana" from them.
        assertTrue(TokenKind.HIRAGANA.isWord && TokenKind.HIRAGANA.isKanaWord)
        assertTrue(TokenKind.KATAKANA.isWord && TokenKind.KATAKANA.isKanaWord)
        assertTrue(TokenKind.WORD.isWord)
        assertFalse(TokenKind.WORD.isKanaWord)
        assertFalse(TokenKind.PARTICLE.isWord)
        assertFalse(TokenKind.PARTICLE.isKanaWord)
    }

    @Test
    fun `baseText is the dictionary form when there is one, the surface otherwise`() {
        val inflected = SentenceToken(word = "わから", kind = TokenKind.HIRAGANA.code, dictForm = "わかる")
        val plain = SentenceToken(word = "コーヒー", kind = TokenKind.KATAKANA.code)

        assertEquals("わかる", inflected.baseText)
        assertEquals("コーヒー", plain.baseText)
    }

    @Test
    fun `converts katakana to hiragana and leaves the prolonged sound mark alone`() {
        assertEquals("ことば", kataToHira("コトバ"))
        assertEquals("ばー", kataToHira("バー"))
    }

    @Test
    fun `checkSingleSentence accepts exactly one sentence, punctuation optional`() {
        assertEquals(SingleSentenceCheck.Ok("これは文です。"), checkSingleSentence("これは文です。"))
        // No terminator at all still counts as one sentence - splitSentences flushes the trailing
        // buffer even without a terminator.
        assertEquals(SingleSentenceCheck.Ok("これは文です"), checkSingleSentence("これは文です"))
    }

    @Test
    fun `checkSingleSentence rejects more than one sentence`() {
        val result = checkSingleSentence("これは一つ目。これは二つ目。")

        assertTrue(result is SingleSentenceCheck.Error)
    }

    @Test
    fun `checkSingleSentence rejects a quote packing more than one sentence`() {
        val result = checkSingleSentence("彼は「おはよう。元気？」と言った。")

        assertTrue(result is SingleSentenceCheck.Error)
    }

    @Test
    fun `checkSingleSentence rejects blank input`() {
        assertTrue(checkSingleSentence("   ") is SingleSentenceCheck.Error)
    }
}
