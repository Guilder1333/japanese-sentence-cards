package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.TokenKind
import org.junit.Assert.assertEquals
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
    fun `classifies hiragana-only and punctuation as PARTICLE`() {
        assertEquals(TokenKind.PARTICLE, classifyToken("は"))
        assertEquals(TokenKind.PARTICLE, classifyToken("。"))
    }

    @Test
    fun `converts katakana to hiragana and leaves the prolonged sound mark alone`() {
        assertEquals("ことば", kataToHira("コトバ"))
        assertEquals("ばー", kataToHira("バー"))
    }
}
