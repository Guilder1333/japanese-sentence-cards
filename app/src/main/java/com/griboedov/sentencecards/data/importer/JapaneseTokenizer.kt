package com.griboedov.sentencecards.data.importer

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository

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
 * [SentenceToken]s, resolving each WORD token's dictionary entry along the way. Used by both
 * [BookImporter] (a whole book's worth of sentences) and
 * [com.griboedov.sentencecards.data.cards.SingleSentenceImporter] (one sentence typed directly) -
 * kept as one shared instance rather than duplicated, since building a Kuromoji [Tokenizer] loads
 * its ~28MB dictionary into memory, so only one instance should exist per process (see how
 * [com.griboedov.sentencecards.SentenceCardsApp] wires it up).
 */
class JapaneseTokenizer(private val dictionaryRepository: DictionaryRepository) : SentenceTokenizer {
    private val tokenizer by lazy { Tokenizer.Builder().build() }

    /**
     * The Dictionary screen's search terms for [text] - see [extractSearchTerms]. A plain
     * (non-suspend) call: unlike [tokenize], nothing here needs a word id or a dictionary lookup, so
     * there's no DB access at all, just Kuromoji itself.
     */
    fun splitIntoSearchTerms(text: String): List<String> = extractSearchTerms(tokenizer.tokenize(text), fallbackTerm = text)

    override suspend fun tokenize(
        sentence: String,
        resolveWordId: suspend (surface: String, dictForm: String) -> Long,
    ): List<SentenceToken> {
        val entryIdCache = HashMap<String, Long?>()
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
                        kind = TokenKind.WORD.code,
                        furigana = furigana,
                        id = resolveWordId(surface, dictForm),
                        dictForm = dictForm,
                        dictionaryEntryId = resolveDictionaryEntryId(dictForm, furigana, entryIdCache),
                    )
                }
                // Katakana/particle tokens are never tracked as WordEntity rows (see
                // StructuredImport.kt - only WORD-kind tokens get an id), so there's no dictionary
                // entry to seed and no lookup needed here at all.
                TokenKind.KATAKANA -> out += SentenceToken(word = surface, kind = TokenKind.KATAKANA.code)
                TokenKind.PARTICLE -> out += SentenceToken(word = surface, kind = TokenKind.PARTICLE.code)
            }
        }
        return out
    }

    /** Resolves [dictForm]'s bundled dictionary entry id, to seed a brand-new [com.griboedov.sentencecards.data.db.WordEntity.dictionaryEntryId]. */
    private suspend fun resolveDictionaryEntryId(
        dictForm: String,
        furigana: String?,
        cache: MutableMap<String, Long?>,
    ): Long? {
        cache[dictForm]?.let { return it }
        if (dictForm in cache) return null // cached miss
        val id = dictionaryRepository.lookup(dictForm, furigana, limit = 1).firstOrNull()?.id
        cache[dictForm] = id
        return id
    }
}

/**
 * Extracts the Dictionary screen's search terms from already-tokenized Kuromoji [tokens]: one term
 * per trackable word - the same [classifyToken] rule [JapaneseTokenizer.tokenize] uses to decide
 * what counts as one - using each word's dictionary (base) form rather than however it happens to
 * be inflected in the text (見た -> 見る), so a pasted/typed sentence searches every word it
 * actually contains instead of being matched as one literal string. Katakana loanwords are kept
 * too, by their surface form (no dictionary base form applies to those). Particles, punctuation,
 * and hiragana/latin filler are skipped the same way they are on import.
 *
 * Falls back to [fallbackTerm] (the untokenized text, trimmed) as the sole term if that leaves
 * nothing - e.g. an English meaning query, or otherwise kanji/katakana-free input - so plain
 * single-term browsing (searching by meaning, or a bare kana word) keeps working unchanged.
 *
 * Pulled out as a standalone function taking already-tokenized [tokens], rather than a
 * [JapaneseTokenizer] method - see [JapaneseTokenizer.splitIntoSearchTerms] for that - purely so
 * it's unit-testable with a plain Kuromoji [Tokenizer], without needing the
 * [com.griboedov.sentencecards.data.dictionary.DictionaryRepository]/Context [JapaneseTokenizer]
 * itself requires.
 */
fun extractSearchTerms(tokens: List<Token>, fallbackTerm: String): List<String> {
    val terms = LinkedHashSet<String>()
    for (tok in tokens) {
        when (classifyToken(tok.surface)) {
            TokenKind.WORD -> terms += tok.baseForm?.takeIf { it != "*" } ?: tok.surface
            TokenKind.KATAKANA -> terms += tok.surface
            TokenKind.PARTICLE -> {}
        }
    }
    return terms.toList().ifEmpty { listOfNotNull(fallbackTerm.trim().takeIf(String::isNotEmpty)) }
}
