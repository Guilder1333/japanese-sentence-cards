package com.griboedov.sentencecards.data.importer

import com.atilika.kuromoji.ipadic.Tokenizer
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository

// Small hand-picked table so common particles/copulas get a short gloss for free, instead of
// either an empty string or a misleading dictionary hit (kana-only entries like "は" collide with
// unrelated dictionary words). Mirrors tools/import_book.py's PARTICLE_GLOSSES.
private val PARTICLE_GLOSSES = mapOf(
    "は" to "topic marker", "が" to "subject marker", "を" to "object marker",
    "に" to "to/at/in", "で" to "at/by/with", "の" to "of/'s", "と" to "and/with",
    "も" to "also/too", "や" to "and/or", "へ" to "towards", "から" to "from/because",
    "まで" to "until/up to", "より" to "than/from", "だ" to "is/am/are", "です" to "is/am/are",
    "ね" to "(seeking agreement)", "よ" to "(emphasis)", "な" to "(emphasis/prohibition)",
    "か" to "(question marker)", "けど" to "but/though", "けれど" to "but/though",
    "ば" to "if/when", "たら" to "if/when", "ながら" to "while", "し" to "and (listing)",
)

/**
 * The tokenizing step [BookImporter] and [com.griboedov.sentencecards.data.cards.SingleSentenceImporter]
 * both need - pulled out as an interface (rather than depending on [JapaneseTokenizer] directly) so
 * tests can fake it without needing a real (Android-only) [DictionaryRepository].
 */
interface SentenceTokenizer {
    /**
     * [resolveWordId] is called once per WORD-kind token, with its surface and dictionary base
     * form, and must return the [com.griboedov.sentencecards.data.db.WordEntity] id that token
     * should reference. Callers decide the id policy (memoize-within-this-run only vs. also
     * reusing an already-tracked word's id, say) - see [BookImporter] and
     * [com.griboedov.sentencecards.data.cards.SingleSentenceImporter] for two different ones.
     */
    suspend fun tokenize(
        sentence: String,
        resolveWordId: suspend (surface: String, dictForm: String) -> Long,
    ): List<SentenceToken>
}

/**
 * Shared Kuromoji-backed [SentenceTokenizer]: turns a single Japanese sentence into
 * [SentenceToken]s with per-word dictionary glosses filled in. Used by both [BookImporter] (a
 * whole book's worth of sentences) and
 * [com.griboedov.sentencecards.data.cards.SingleSentenceImporter] (one sentence typed directly) -
 * kept as one shared instance rather than duplicated, since building a Kuromoji [Tokenizer] loads
 * its ~28MB dictionary into memory, so only one instance should exist per process (see how
 * [com.griboedov.sentencecards.SentenceCardsApp] wires it up).
 */
class JapaneseTokenizer(private val dictionaryRepository: DictionaryRepository) : SentenceTokenizer {
    private val tokenizer by lazy { Tokenizer.Builder().build() }

    override suspend fun tokenize(
        sentence: String,
        resolveWordId: suspend (surface: String, dictForm: String) -> Long,
    ): List<SentenceToken> {
        val glossCache = HashMap<Pair<String?, String?>, String>()
        val tokens = tokenizer.tokenize(sentence)
        val out = ArrayList<SentenceToken>(tokens.size)
        for (tok in tokens) {
            val surface = tok.surface
            when (classifyToken(surface)) {
                TokenKind.WORD -> {
                    val dictForm = tok.baseForm?.takeIf { it != "*" } ?: surface
                    val furigana = tok.reading?.takeIf { it != "*" }?.let(::kataToHira)
                    out += SentenceToken(
                        word = surface,
                        translation = gloss(dictForm, furigana, glossCache),
                        kind = TokenKind.WORD.code,
                        furigana = furigana,
                        id = resolveWordId(surface, dictForm),
                    )
                }
                TokenKind.KATAKANA -> out += SentenceToken(
                    word = surface,
                    translation = gloss(null, surface, glossCache),
                    kind = TokenKind.KATAKANA.code,
                )
                TokenKind.PARTICLE -> out += SentenceToken(
                    word = surface,
                    translation = PARTICLE_GLOSSES[surface] ?: "",
                    kind = TokenKind.PARTICLE.code,
                )
            }
        }
        return out
    }

    private suspend fun gloss(
        kanji: String?,
        kana: String?,
        cache: MutableMap<Pair<String?, String?>, String>,
    ): String {
        val key = kanji to kana
        cache[key]?.let { return it }
        if (kanji.isNullOrBlank() && kana.isNullOrBlank()) return ""
        val entries = dictionaryRepository.lookup(kanji, kana, limit = 1)
        // meaning looks like "1. (n) word; phrase; expression\n2. ..." - take just the first gloss
        // of the first sense, to keep this a short label rather than a dictionary dump.
        val firstLine = entries.firstOrNull()?.meaning?.substringBefore('\n') ?: ""
        val afterNumber = firstLine.substringAfter(". ", firstLine)
        val afterPos = if (afterNumber.startsWith("(")) afterNumber.substringAfter(") ", afterNumber) else afterNumber
        val result = afterPos.substringBefore(";").trim()
        cache[key] = result
        return result
    }
}
