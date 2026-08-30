package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.TokenKind

/**
 * Plain-text sentence splitting, ported from `tools/import_book.py`'s `split_sentences` /
 * `split_long_quotes` / `clean_sentence` / `japanese_ratio` / `classify_token` - see that script's
 * doc comments for the full rationale behind each rule. Kept dependency-free (no Android/DB
 * imports) so it's easy to reason about/test in isolation from [BookImporter].
 */

private val OPEN_BRACKETS = setOf('「', '『', '｢')
private val CLOSE_BRACKETS = setOf('」', '』', '｣')
private val SENTENCE_END_CHARS = setOf('。', '.', '！', '!', '？', '?')

// Safety valve: if a bracket never gets matched (unrecognized quote variant, stray/mismatched
// character, etc.), sentence-end tracking would otherwise stay suppressed for the rest of the
// text, silently swallowing every remaining sentence into one giant blob. Once an unmatched quote
// has run on this long, give up on depth-tracking for it and go back to splitting on plain
// terminators.
private const val MAX_UNCLOSED_QUOTE_CHARS = 500

/**
 * Splits raw book text into sentences at 。.！!？?, tracking 「」/『』 quote depth so a terminator
 * *inside* a quote (e.g. 彼は「ああ、そうだ。」と言った。) doesn't cut the sentence short - the
 * quote is just part of the sentence it's embedded in.
 *
 * A blank line - i.e. two or more consecutive line breaks - is a paragraph break, and ends
 * whatever sentence is still open even without a terminator. Plain-text books routinely leave the
 * last line of a paragraph unpunctuated (a heading, a line of verse, a chapter title, a fragment of
 * dialogue), and without this that line would be glued onto the first sentence of the next
 * paragraph. Any run of blank lines counts once, so a double-spaced file doesn't emit empties.
 *
 * OCR'd/digitized books sometimes drop a line's closing quote mark entirely, which would otherwise
 * leave depth stuck open and swallow every terminator for the rest of the book
 * (MAX_UNCLOSED_QUOTE_CHARS guards the worst case of that, but by then several real sentences have
 * already been merged together). As a targeted fix: if a line ends with depth still open and the
 * *next* non-blank line opens another bracket, assume the missing close happened right there at
 * the line boundary - a new quote starting strongly implies the previous one ended. A paragraph
 * break is the stronger version of the same idea: a quote left open at the end of a paragraph is
 * treated as closed there, so a single dropped bracket can't swallow the rest of the chapter.
 */
fun splitSentences(text: String): List<String> {
    val lines = text.split("\n")
    val out = mutableListOf<String>()
    val buf = StringBuilder()
    var depth = 0
    var unclosedStart = 0
    for (i in lines.indices) {
        val line = lines[i]
        if (line.isBlank()) {
            // Paragraph break: flush whatever's accumulated, terminator or not, and give up on any
            // quote still open - it can't run across a paragraph boundary.
            if (buf.isNotEmpty()) {
                out += buf.toString()
                buf.setLength(0)
            }
            depth = 0
            continue
        }
        if (buf.isNotEmpty()) buf.append('\n')
        for (ch in line) {
            buf.append(ch)
            if (ch in OPEN_BRACKETS) {
                if (depth == 0) unclosedStart = buf.length - 1
                depth++
            } else if (ch in CLOSE_BRACKETS) {
                depth = maxOf(0, depth - 1)
            }
            if (depth > 0 && buf.length - unclosedStart > MAX_UNCLOSED_QUOTE_CHARS) depth = 0
            if (depth == 0 && ch in SENTENCE_END_CHARS) {
                out += buf.toString()
                buf.setLength(0)
            }
        }
        if (depth > 0) {
            val nextLine = lines.drop(i + 1).firstOrNull { it.isNotBlank() } ?: ""
            if (nextLine.firstOrNull() in OPEN_BRACKETS) depth--
        }
    }
    if (buf.isNotEmpty()) out += buf.toString()
    return out
}

/** Splits text into chunks at 。.！!？?, tracking bracket depth the same way [splitSentences] does. */
private fun splitDepthZero(text: String): List<String> {
    val parts = mutableListOf<String>()
    val buf = StringBuilder()
    var depth = 0
    for (ch in text) {
        buf.append(ch)
        if (ch in OPEN_BRACKETS) depth++
        else if (ch in CLOSE_BRACKETS) depth = maxOf(0, depth - 1)
        if (depth == 0 && ch in SENTENCE_END_CHARS) {
            parts += buf.toString()
            buf.setLength(0)
        }
    }
    if (buf.isNotEmpty()) parts += buf.toString()
    return parts
}

/**
 * Returns the contents (brackets excluded) of each top-level 「」/『』 quote span in [text]. A
 * quote that never closes within [text] is still included, running from its opening bracket to
 * the end - see [splitSentences]'s doc comment for why that can happen.
 */
private fun extractTopLevelQuotes(text: String): List<String> {
    val spans = mutableListOf<String>()
    var depth = 0
    var start = -1
    for (i in text.indices) {
        val ch = text[i]
        if (ch in OPEN_BRACKETS) {
            if (depth == 0) start = i + 1
            depth++
        } else if (ch in CLOSE_BRACKETS) {
            depth = maxOf(0, depth - 1)
            if (depth == 0 && start >= 0) {
                spans += text.substring(start, i)
                start = -1
            }
        }
    }
    if (depth > 0 && start >= 0) spans += text.substring(start)
    return spans
}

/**
 * A quote that runs on for several sentences (a whole stretch of dialogue packed into one 「」)
 * makes for a needlessly long, unfocused flashcard once merged with its surrounding narration. So:
 * count how many sentences are packed inside [raw]'s top-level quote(s), and if that's more than
 * one, break each quote's contents into its own per-sentence pieces and drop the outer sentence
 * (narration and all) in favor of those. Returns `[raw]` unchanged when there's nothing to split.
 */
fun splitLongQuotes(raw: String): List<String> {
    val quoteSentences = extractTopLevelQuotes(raw).flatMap { splitDepthZero(it) }
    return if (quoteSentences.size > 1) quoteSentences else listOf(raw)
}

/**
 * The full per-sentence split [BookImporter] runs over a whole book, exposed as one step so
 * [com.griboedov.sentencecards.data.cards.SingleSentenceImporter] can check "is this really just
 * one sentence" against the exact same rule instead of a separate, looser one. Empty results (e.g.
 * blank input) are dropped rather than returned as an empty-string "sentence".
 */
fun splitIntoCleanSentences(text: String, keepLongQuotes: Boolean = false): List<String> =
    splitSentences(text)
        .flatMap { if (keepLongQuotes) listOf(it) else splitLongQuotes(it) }
        .map(::cleanSentence)
        .filter { it.isNotEmpty() }

sealed interface SingleSentenceCheck {
    data class Ok(val sentence: String) : SingleSentenceCheck
    data class Error(val message: String) : SingleSentenceCheck
}

/**
 * Checks that [text] really is just one sentence, against the exact same splitting rule
 * [splitIntoCleanSentences] applies to a whole book - for
 * [com.griboedov.sentencecards.data.cards.SingleSentenceImporter]'s "import a single sentence"
 * feature, which (unlike book import) needs to reject anything that isn't exactly one, rather than
 * quietly importing every sentence it finds.
 */
fun checkSingleSentence(text: String): SingleSentenceCheck {
    val sentences = splitIntoCleanSentences(text)
    return when (sentences.size) {
        0 -> SingleSentenceCheck.Error("Enter a sentence to import.")
        1 -> SingleSentenceCheck.Ok(sentences.single())
        else -> SingleSentenceCheck.Error(
            "That looks like ${sentences.size} sentences, not one - only a single sentence can " +
                "be imported this way. Use the book import above for more than one.",
        )
    }
}

/**
 * Plain-text books commonly wrap lines and/or use full-width spaces for ruby/indentation - none of
 * that is meaningful in a single flash-card sentence, so strip *all* whitespace, not only
 * leading/trailing.
 */
fun cleanSentence(raw: String): String = raw.filterNot { it.isWhitespace() }

private enum class CharCategory { HIRA, KATA, KANJI, EITHER, OTHER }

// Unicode blocks, by code point (clearer/less error-prone here than char literals): Hiragana
// U+3040-U+309F, Katakana U+30A0-U+30FF, CJK Unified Ideographs U+4E00-U+9FFF, CJK Unified
// Ideographs Extension A U+3400-U+4DBF, CJK Compatibility Ideographs U+F900-U+FAFF.
private const val PROLONGED_SOUND_MARK = 0x30FC // ー - ambiguous, shows up in both hira/kata runs

private fun charCategory(ch: Char): CharCategory {
    val code = ch.code
    return when {
        code == PROLONGED_SOUND_MARK -> CharCategory.EITHER
        code in 0x3040..0x309F -> CharCategory.HIRA
        code in 0x30A0..0x30FF -> CharCategory.KATA
        code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0xF900..0xFAFF -> CharCategory.KANJI
        else -> CharCategory.OTHER
    }
}

/**
 * Fraction of the sentence's letters (kanji/kana/Latin/etc - punctuation and digits don't count
 * either way) that are Japanese script (kanji, hiragana, katakana, or the ー prolonged-sound mark).
 * A sentence with no letters at all returns 1.0 (nothing to judge, so the ratio filter doesn't
 * apply to it).
 */
fun japaneseRatio(text: String): Double {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty()) return 1.0
    val japanese = letters.count { charCategory(it) != CharCategory.OTHER }
    return japanese.toDouble() / letters.length
}

// Kuromoji IPADIC part-of-speech level 1 values that are pure grammar rather than words:
// 助詞 = particle, 助動詞 = auxiliary verb/copula (です, た, ない), 記号 = symbol/punctuation,
// フィラー = filler (えーと). Anything tagged with one of these is PARTICLE whatever its script.
private val GRAMMAR_POS1 = setOf("助詞", "助動詞", "記号", "フィラー")

// Part-of-speech level 2 values marking a token that only exists to attach to another word:
// 非自立 = dependent (the いる of ～ている, the こと of ～ということ), 接尾 = suffix (さん, たち,
// 的). These carry no meaning on their own, so they belong with the grammar even though their
// level-1 tag says 動詞/名詞/形容詞.
private val DEPENDENT_POS2 = setOf("非自立", "接尾")

/**
 * Maps a token to a [TokenKind] - see [TokenKind]'s doc comment for the codes.
 *
 * [pos1]/[pos2] are the tokenizer's part-of-speech levels 1 and 2 (Kuromoji IPADIC:
 * `Token.partOfSpeechLevel1`/`2`; `"*"` and null both mean "not known"). They are what separates a
 * kana-written *word* from a particle - script alone cannot: わかる and が are both hiragana-only.
 * Without them every kana token falls back to [TokenKind.PARTICLE], which is the old,
 * script-only behaviour.
 *
 * Kanji and katakana are still decided by script alone, before part-of-speech is even consulted:
 * a kanji-containing token is a tracked [TokenKind.WORD] even when it is a dependent suffix
 * (的, 性), and a katakana loanword is [TokenKind.KATAKANA] even when it is a filler (ソレ).
 */
fun classifyToken(surface: String, pos1: String? = null, pos2: String? = null): TokenKind {
    val categories = surface.map { charCategory(it) }.filterNot { it == CharCategory.EITHER }.toSet()
    if (CharCategory.KANJI in categories) return TokenKind.WORD
    if (categories == setOf(CharCategory.KATA)) return TokenKind.KATAKANA
    if (pos1 in GRAMMAR_POS1 || pos2 in DEPENDENT_POS2) return TokenKind.PARTICLE
    // A content word (verb/adjective/adverb/noun/pronoun) that happens to be written in kana.
    if (CharCategory.HIRA in categories && pos1 != null && pos1 != "*") return TokenKind.HIRAGANA
    // Digits, latin, punctuation, and - with no part-of-speech to go on - any kana token.
    return TokenKind.PARTICLE
}

/** Converts katakana to hiragana; anything else passes through unchanged. */
fun kataToHira(text: String): String = text.map { ch ->
    val code = ch.code
    if (code in 0x30A1..0x30F6) (code - 0x60).toChar() else ch
}.joinToString("")
