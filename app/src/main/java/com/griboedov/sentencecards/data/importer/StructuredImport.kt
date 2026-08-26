package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import java.io.InputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence

/**
 * One sentence as accepted by the bulk import screen. This is deliberately the same shape as the
 * README's structure example, plus [translation] for the whole sentence:
 *
 * ```json
 * [
 *   {
 *     "translation": "This word is English.",
 *     "structure": [
 *       { "word": "この", "translation": "this", "kind": 2 },
 *       { "word": "言葉", "furigana": "ことば", "translation": "word", "kind": 1, "id": 1234 }
 *     ]
 *   }
 * ]
 * ```
 *
 * [text] is optional - when omitted it is derived by concatenating the structure's words.
 *
 * This only covers the "already structured" import path from the README. Plain-text import (via
 * an adapted parsing script) is a later addition.
 */
@Serializable
data class ImportSentence(
    val text: String? = null,
    val translation: String,
    val structure: List<SentenceToken>,
)

sealed interface ImportResult {
    data class Success(val sentences: Int, val newWords: Int) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Turns [ImportSentence]s into rows in the raw sentence pool (the README's "Sentence storage" /
 * [SentenceEntity]) - never cards directly; see
 * [com.griboedov.sentencecards.data.cards.CardGenerator] for how sentences later become cards,
 * once a word they contain is marked to-learn.
 *
 * Entry points:
 *  - [importJson] - the whole JSON already in memory as a String (used for seeding built-in data).
 *  - [importStream] - the file-picker path, for files that can run into the hundreds of megabytes
 *    (a whole book). Streams the JSON array element-by-element straight off the [InputStream]
 *    instead of first reading it into one giant String, and [importSentences] writes it to the DB
 *    in fixed-size batches instead of collecting the whole parsed array into one giant List -
 *    peak memory stays roughly constant no matter how large the file is.
 *  - [importParsed] - [ImportSentence]s already built in memory by
 *    [com.griboedov.sentencecards.data.importer.BookImporter] (the plain-text book import path)
 *    rather than deserialized from JSON, but written via the exact same batched DB-write core.
 *  - [importOne] - a single already-tokenized sentence whose WORD tokens already carry a resolved
 *    id (new or reused), for [com.griboedov.sentencecards.data.cards.SingleSentenceImporter] to
 *    build a card from immediately afterward - so unlike the other three, this hands back the
 *    written [SentenceEntity] (real DB id included) instead of just a summary count.
 */
class SentenceImporter(
    private val wordDao: WordDao,
    private val sentenceDao: SentenceDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importJson(rawJson: String): ImportResult {
        val parsed = try {
            json.decodeFromString<List<ImportSentence>>(rawJson)
        } catch (e: Exception) {
            return ImportResult.Failure(e.message ?: "Could not parse JSON")
        }
        return importSentences(parsed.asSequence())
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun importStream(input: InputStream): ImportResult = try {
        importSentences(json.decodeToSequence<ImportSentence>(input, DecodeSequenceMode.ARRAY_WRAPPED))
    } catch (e: Exception) {
        ImportResult.Failure(e.message ?: "Could not parse JSON")
    }

    suspend fun importParsed(sentences: Sequence<ImportSentence>): ImportResult = importSentences(sentences)

    /**
     * Writes one already-tokenized [sentence] straight to the sentence pool and returns the
     * resulting [SentenceEntity], real DB id included. Every WORD token in [sentence.structure] is
     * expected to already carry the id it should reference (see [JapaneseTokenizer.tokenize]'s
     * `resolveWordId` callback) - a [com.griboedov.sentencecards.data.db.WordEntity] row is created
     * for any id not already present in the DB, and any that already exist are left untouched
     * (so an id resolved to an already-tracked word doesn't clobber its existing progress).
     */
    suspend fun importOne(sentence: ImportSentence): SentenceEntity {
        val wordIds = sentence.structure.mapNotNull { token ->
            token.id.takeIf { TokenKind.fromCode(token.kind) == TokenKind.WORD }
        }
        val newWords = wordIds.distinct()
            .filter { wordDao.getById(it) == null }
            .mapNotNull { id -> sentence.structure.firstOrNull { it.id == id } }
            .map { token -> WordEntity(id = token.id!!, word = token.word, furigana = token.furigana, translation = token.translation) }
        if (newWords.isNotEmpty()) wordDao.upsertAll(newWords)

        val text = sentence.text ?: sentence.structure.joinToString("") { it.word }
        val entity = SentenceEntity(text = text, translation = sentence.translation, structure = sentence.structure)
        val insertedId = sentenceDao.upsertAll(listOf(entity)).single()
        if (wordIds.isNotEmpty()) {
            sentenceDao.insertWordRefs(wordIds.distinct().map { SentenceWordCrossRef(sentenceId = insertedId, wordId = it) })
        }
        return entity.copy(id = insertedId)
    }

    /**
     * Shared batch-processing core. [sentences] is consumed lazily in [batchSize]-sized chunks -
     * each chunk is resolved and written to the DB before the next one is pulled from the source,
     * so a huge (possibly streamed) source is never held fully in memory at once.
     */
    private suspend fun importSentences(sentences: Sequence<ImportSentence>, batchSize: Int = 1000): ImportResult {
        var nextWordId = (wordDao.maxId() ?: 0L) + 1
        // Preloaded once and kept updated locally, so checking "is this word already known"
        // during a huge import is an in-memory set lookup instead of one DB query per word token.
        val knownWordIds = wordDao.allIds().toMutableSet()
        var totalSentences = 0
        var totalNewWords = 0
        var sawAny = false

        try {
            for (chunk in sentences.chunked(batchSize)) {
                sawAny = true
                val newWords = mutableListOf<WordEntity>()
                val sentenceEntities = mutableListOf<SentenceEntity>()
                // Parallel to sentenceEntities - the word ids each entry's structure references,
                // for the sentence_words index (see SentenceWordCrossRef), built once real
                // (post-insert) sentence ids are known below.
                val sentenceWordIds = mutableListOf<Set<Long>>()

                for (sentence in chunk) {
                    val resolvedTokens = mutableListOf<SentenceToken>()
                    val wordIds = mutableSetOf<Long>()

                    for (token in sentence.structure) {
                        if (TokenKind.fromCode(token.kind) != TokenKind.WORD) {
                            resolvedTokens += token
                            continue
                        }
                        // Per the README: a brand-new kanji/word defaults to not-learned - this is
                        // just raw sentence-pool import, not "this sentence is now a card teaching
                        // this word" (that only happens via CardGenerator, once the word is
                        // explicitly marked to-learn).
                        val id = token.id ?: nextWordId++
                        if (knownWordIds.add(id)) {
                            newWords += WordEntity(
                                id = id,
                                word = token.word,
                                furigana = token.furigana,
                                translation = token.translation,
                            )
                        }
                        wordIds += id
                        resolvedTokens += token.copy(id = id)
                    }

                    val text = sentence.text ?: sentence.structure.joinToString("") { it.word }
                    sentenceEntities += SentenceEntity(text = text, translation = sentence.translation, structure = resolvedTokens)
                    sentenceWordIds += wordIds
                }

                if (newWords.isNotEmpty()) wordDao.upsertAll(newWords)
                val insertedIds = sentenceDao.upsertAll(sentenceEntities)
                val refs = insertedIds.indices.flatMap { i ->
                    sentenceWordIds[i].map { wordId -> SentenceWordCrossRef(sentenceId = insertedIds[i], wordId = wordId) }
                }
                if (refs.isNotEmpty()) sentenceDao.insertWordRefs(refs)

                totalSentences += sentenceEntities.size
                totalNewWords += newWords.size
            }
        } catch (e: Exception) {
            if (!sawAny) return ImportResult.Failure(e.message ?: "Could not parse JSON")
            // Partway through a huge file: whatever already made it into the DB (every fully
            // processed batch before this one) stays - only the failure is surfaced, so the user
            // knows the file was cut short instead of silently losing everything already imported.
            return ImportResult.Failure(
                "Imported $totalSentences sentence(s) before failing: ${e.message ?: "could not parse the rest of the file"}",
            )
        }

        if (!sawAny) return ImportResult.Failure("No sentences found in JSON")
        return ImportResult.Success(sentences = totalSentences, newWords = totalNewWords)
    }
}
