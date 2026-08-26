package com.griboedov.sentencecards.data.importer

import android.content.Context
import android.net.Uri
import com.griboedov.sentencecards.data.db.WordDao
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Tokenization/POS lookup is done on-device with Kuromoji's IPADIC tokenizer (see
 * [JapaneseTokenizer]; pure Kotlin/Java, no native code or network access) rather than the
 * script's fugashi+UniDic - the two dictionaries segment slightly differently, so an import here
 * vs. via the script won't produce byte-identical output, but the resulting cards are equivalent
 * in spirit.
 *
 * Unlike the structured-JSON import, this reads the whole input file into memory up front - fine
 * for a single plain-text book (a few MB at most), unlike the JSON path which is built to handle
 * pre-structured datasets that can run into the hundreds of megabytes.
 *
 * Word ids are only ever reused *within this one import run* (the same dictionary-form word seen
 * again later in the same book gets the same id, via an in-memory map) - this does not check the
 * DB for a pre-existing word with the same text, so re-importing overlapping vocabulary across
 * separate book imports creates separate [com.griboedov.sentencecards.data.db.WordEntity] rows,
 * same as the structured-JSON import already does. See ASSUMPTIONS.md for the reasoning; contrast
 * with [com.griboedov.sentencecards.data.cards.SingleSentenceImporter], which does check.
 */
class BookImporter(
    private val wordDao: WordDao,
    private val tokenizer: SentenceTokenizer,
    private val sentenceImporter: SentenceImporter,
) {
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
            var nextWordId = (wordDao.maxId() ?: 0L) + 1
            val results = mutableListOf<ImportSentence>()

            for (sentence in splitIntoCleanSentences(text, options.keepLongQuotes)) {
                if (sentence.length < options.minChars) continue
                if (japaneseRatio(sentence) < options.minJapaneseRatio) continue
                if (seenSentences != null && !seenSentences.add(sentence)) continue

                val structure = tokenizer.tokenize(sentence) { _, dictForm ->
                    wordIds.getOrPut(dictForm) { nextWordId++ }
                }
                results += ImportSentence(text = sentence, translation = "", structure = structure)

                if (options.limit != null && results.size >= options.limit) break
            }

            if (results.isEmpty()) return@withContext ImportResult.Failure("No sentences found in the text.")
            sentenceImporter.importParsed(results.asSequence())
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
