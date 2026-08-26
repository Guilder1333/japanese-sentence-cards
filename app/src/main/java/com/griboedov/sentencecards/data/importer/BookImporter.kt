package com.griboedov.sentencecards.data.importer

import android.content.Context
import android.net.Uri
import com.atilika.kuromoji.ipadic.Tokenizer
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Default filters, matching `tools/import_book.py`'s CLI defaults. */
data class BookImportOptions(
    val minChars: Int = 2,
    val minJapaneseRatio: Double = 0.5,
    val keepDuplicates: Boolean = false,
    val keepLongQuotes: Boolean = false,
    val limit: Int? = null,
)

/**
 * In-app equivalent of `tools/import_book.py`, for when running that Python script isn't an
 * option: turns plain-text Japanese book(s) directly into [ImportSentence]s and writes them via
 * [SentenceImporter.importParsed] - same batched DB-write path as the structured-JSON import, just
 * fed from parsed plain text instead of already-structured JSON.
 *
 * Tokenization/POS lookup is done on-device with Kuromoji's IPADIC tokenizer (pure Kotlin/Java, no
 * native code or network access) rather than the script's fugashi+UniDic - the two dictionaries
 * segment slightly differently, so an import here vs. via the script won't produce byte-identical
 * output, but the resulting cards are equivalent in spirit. Per-word glosses reuse the same bundled
 * JMdict [DictionaryRepository] the Dictionary tab searches.
 *
 * Unlike the structured-JSON import, this reads the whole input file into memory up front - fine
 * for a single plain-text book (a few MB at most), unlike the JSON path which is built to handle
 * pre-structured datasets that can run into the hundreds of megabytes.
 */
class BookImporter(
    private val wordDao: WordDao,
    private val dictionaryRepository: DictionaryRepository,
    private val sentenceImporter: SentenceImporter,
) {
    private val tokenizer by lazy { Tokenizer.Builder().build() }

    suspend fun importFile(context: Context, uri: Uri, options: BookImportOptions = BookImportOptions()): ImportResult =
        withContext(Dispatchers.IO) {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext ImportResult.Failure("Could not open the selected file.")
            } catch (e: Throwable) {
                return@withContext ImportResult.Failure(e.message ?: "Could not read the selected file.")
            }
            importText(decodeBookText(bytes), options)
        }

    suspend fun importText(text: String, options: BookImportOptions = BookImportOptions()): ImportResult =
        withContext(Dispatchers.IO) {
            val seenSentences = if (options.keepDuplicates) null else HashSet<String>()
            val wordIds = HashMap<String, Long>() // dictionary-form -> id, stable within this run
            val glossCache = HashMap<Pair<String?, String?>, String>()
            var nextWordId = (wordDao.maxId() ?: 0L) + 1
            val results = mutableListOf<ImportSentence>()

            outer@ for (raw in splitSentences(text)) {
                val candidates = if (options.keepLongQuotes) listOf(raw) else splitLongQuotes(raw)
                for (candidate in candidates) {
                    val sentence = cleanSentence(candidate)
                    if (sentence.length < options.minChars) continue
                    if (japaneseRatio(sentence) < options.minJapaneseRatio) continue
                    if (seenSentences != null && !seenSentences.add(sentence)) continue

                    val structure = buildStructure(sentence, wordIds, glossCache) { nextWordId++ }
                    results += ImportSentence(text = sentence, translation = "", structure = structure)

                    if (options.limit != null && results.size >= options.limit) break@outer
                }
            }

            if (results.isEmpty()) return@withContext ImportResult.Failure("No sentences found in the text.")
            sentenceImporter.importParsed(results.asSequence())
        }

    private suspend fun buildStructure(
        sentence: String,
        wordIds: MutableMap<String, Long>,
        glossCache: MutableMap<Pair<String?, String?>, String>,
        nextWordId: () -> Long,
    ): List<SentenceToken> {
        val tokens = tokenizer.tokenize(sentence)
        val out = ArrayList<SentenceToken>(tokens.size)
        for (tok in tokens) {
            val surface = tok.surface
            when (classifyToken(surface)) {
                TokenKind.WORD -> {
                    val dictForm = tok.baseForm?.takeIf { it != "*" } ?: surface
                    val furigana = tok.reading?.takeIf { it != "*" }?.let(::kataToHira)
                    val id = wordIds.getOrPut(dictForm, nextWordId)
                    out += SentenceToken(
                        word = surface,
                        translation = gloss(dictForm, furigana, glossCache),
                        kind = TokenKind.WORD.code,
                        furigana = furigana,
                        id = id,
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

private val FALLBACK_CHARSET_NAMES = listOf("Shift_JIS", "windows-31j")

/**
 * Decodes a book file's raw bytes, trying UTF-8 (BOM-stripped if present) first and falling back
 * through the Shift_JIS family - mirrors `tools/import_book.py`'s `read_text` encoding fallback,
 * since plain-text Japanese books in the wild show up in either.
 */
private fun decodeBookText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    val candidates = buildList {
        add(Charsets.UTF_8)
        for (name in FALLBACK_CHARSET_NAMES) {
            try {
                add(Charset.forName(name))
            } catch (_: Exception) {
                // Not available on this device - skip it.
            }
        }
    }
    for (charset in candidates) {
        try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            // Try the next encoding.
        }
    }
    // Last resort: decode as UTF-8 with replacement characters rather than failing outright.
    return String(bytes, Charsets.UTF_8)
}
