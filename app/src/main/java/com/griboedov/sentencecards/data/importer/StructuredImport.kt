package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
        if (parsed.isEmpty()) return ImportResult.Failure("No sentences found in JSON")

        var nextWordId = (wordDao.maxId() ?: 0L) + 1
        val newWords = mutableListOf<WordEntity>()
        val sentenceEntities = mutableListOf<SentenceEntity>()
        // Parallel to sentenceEntities - the word ids each entry's structure references, for the
        // sentence_words index (see SentenceWordCrossRef), built once real (post-insert) sentence
        // ids are known below.
        val sentenceWordIds = mutableListOf<Set<Long>>()

        for (sentence in parsed) {
            val resolvedTokens = mutableListOf<SentenceToken>()
            val wordIds = mutableSetOf<Long>()

            for (token in sentence.structure) {
                if (TokenKind.fromCode(token.kind) != TokenKind.WORD) {
                    resolvedTokens += token
                    continue
                }
                // Per the README: a brand-new kanji/word defaults to not-learned - this is just
                // raw sentence-pool import, not "this sentence is now a card teaching this word"
                // (that only happens via CardGenerator, once the word is explicitly marked
                // to-learn).
                val id = token.id ?: nextWordId++
                val alreadyKnown = wordDao.getById(id) != null || newWords.any { it.id == id }
                if (!alreadyKnown) {
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
        return ImportResult.Success(sentences = sentenceEntities.size, newWords = newWords.size)
    }
}
