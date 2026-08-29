package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.db.WordProgress
import com.griboedov.sentencecards.data.importer.SentenceImporter
import com.griboedov.sentencecards.data.importer.SentenceTokenizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// Same in-memory-fake pattern as CardGeneratorTest/StructuredImportTest - named distinctly (Ssi*)
// since Kotlin's top-level "private" is file-scoped for visibility only, not for the class name
// itself, so a plain "FakeWordDao" here would collide with CardGeneratorTest's same-package one.

private class SsiFakeWordDao : WordDao {
    val byId = mutableMapOf<Long, WordEntity>()
    override fun observeAll(): Flow<List<WordEntity>> = flowOf(byId.values.toList())
    override suspend fun getById(id: Long): WordEntity? = byId[id]
    override suspend fun getByIds(ids: List<Long>): List<WordEntity> = ids.mapNotNull { byId[it] }
    override suspend fun findByWord(word: String): WordEntity? = byId.values.find { it.word == word }
    override suspend fun upsert(word: WordEntity) { byId[word.id] = word }
    override suspend fun upsertAll(words: List<WordEntity>) { for (w in words) byId[w.id] = w }
    override suspend fun update(word: WordEntity) { byId[word.id] = word }
    override suspend fun maxId(): Long? = byId.keys.maxOrNull()
    override suspend fun allIds(): List<Long> = byId.keys.toList()
    override suspend fun count(): Int = byId.size

    override suspend fun getProgress(): List<WordProgress> = byId.values
        .filter { it.toLearn || it.forceFurigana || it.quizSuccess != 0 || it.quizFails != 0 }
        .map { WordProgress(it.id, it.toLearn, it.forceFurigana, it.quizSuccess, it.quizFails) }
    override suspend fun updateProgress(wordId: Long, toLearn: Boolean, forceFurigana: Boolean, quizSuccess: Int, quizFails: Int) {
        byId[wordId]?.let { byId[wordId] = it.copy(toLearn = toLearn, forceFurigana = forceFurigana, quizSuccess = quizSuccess, quizFails = quizFails) }
    }
}

private class SsiFakeSentenceDao : SentenceDao {
    val byId = mutableMapOf<Long, SentenceEntity>()
    val wordRefs = mutableListOf<SentenceWordCrossRef>()
    private var nextId = 1L
    override suspend fun upsertAll(sentences: List<SentenceEntity>): List<Long> = sentences.map { sentence ->
        val id = if (sentence.id != 0L) sentence.id else nextId++
        byId[id] = sentence.copy(id = id)
        id
    }
    override suspend fun getByIds(ids: List<Long>): List<SentenceEntity> = ids.mapNotNull { byId[it] }
    override suspend fun count(): Int = byId.size
    override suspend fun insertWordRefs(refs: List<SentenceWordCrossRef>) { wordRefs += refs }
    override suspend fun sentenceIdsContaining(wordId: Long): List<Long> =
        wordRefs.filter { it.wordId == wordId }.map { it.sentenceId }
}

private class SsiFakeCardDao : CardDao {
    val cards = mutableListOf<CardEntity>()
    private var nextId = 1L
    override fun observeAll(): Flow<List<CardEntity>> = flowOf(cards.toList())
    override suspend fun upsertAll(cards: List<CardEntity>) {
        for (card in cards) this.cards += card.copy(id = if (card.id != 0L) card.id else nextId++)
    }
    override suspend fun update(card: CardEntity) {
        val index = cards.indexOfFirst { it.id == card.id }
        if (index >= 0) cards[index] = card
    }
    override suspend fun count(): Int = cards.size
    override suspend fun cardsForSentences(sentenceIds: Collection<Long>): List<CardEntity> =
        cards.filter { it.sentenceId in sentenceIds }
    override suspend fun getAll(): List<CardEntity> = cards.toList()
    override suspend fun deleteAll() { cards.clear() }
}

/** [importAsCard] never calls the tokenizer - this is only here to satisfy the constructor. */
private object SsiFakeTokenizer : SentenceTokenizer {
    override suspend fun tokenize(sentence: String, resolveWordId: suspend (String, String) -> Long) =
        error("SingleSentenceImporter.parse() isn't exercised by these tests - only importAsCard()")
}

/**
 * A fake standing in for [JapaneseTokenizer]/Kuromoji for [SingleSentenceImporter.parse] tests -
 * treats [sentence] as one inflected WORD token, with a caller-supplied dictionary form (the way a
 * real tokenizer would resolve "食べた" back to "食べる").
 */
private class SsiFakeInflectingTokenizer(private val surface: String, private val dictForm: String) : SentenceTokenizer {
    override suspend fun tokenize(sentence: String, resolveWordId: suspend (String, String) -> Long): List<SentenceToken> {
        val id = resolveWordId(surface, dictForm)
        return listOf(SentenceToken(word = surface, dictForm = dictForm, kind = TokenKind.WORD.code, id = id))
    }
}

class SingleSentenceImporterTest {

    @Test
    fun `importAsCard writes the sentence to the pool and creates exactly one card with the picked main words`() = runBlocking {
        val wordDao = SsiFakeWordDao()
        val sentenceDao = SsiFakeSentenceDao()
        val cardDao = SsiFakeCardDao()
        val sentenceImporter = SentenceImporter(wordDao, sentenceDao)
        val importer = SingleSentenceImporter(wordDao, cardDao, SsiFakeTokenizer, sentenceImporter)

        val structure = listOf(
            SentenceToken(word = "これ", kind = TokenKind.PARTICLE.code),
            SentenceToken(word = "は", kind = TokenKind.PARTICLE.code),
            SentenceToken(word = "文", kind = TokenKind.WORD.code, id = 1L),
            SentenceToken(word = "です", kind = TokenKind.PARTICLE.code),
        )

        importer.importAsCard(
            sentenceText = "これは文です",
            structure = structure,
            translation = "This is a sentence.",
            mainWordIds = setOf(1L),
        )

        assertEquals(1, sentenceDao.byId.size)
        assertEquals(1, cardDao.cards.size)
        val card = cardDao.cards.single()
        assertEquals(listOf(1L), card.mainWordIds)
        assertEquals("This is a sentence.", card.translation)
        assertEquals(sentenceDao.byId.values.single().id, card.sentenceId)
        // The word itself still got tracked (default not-learned), even though this test doesn't
        // pick it - importing always adds every WORD token, per the README's "Adding sentence" rule.
        assertEquals("文", wordDao.byId.getValue(1L).word)
        assertEquals(false, wordDao.byId.getValue(1L).toLearn)
    }

    @Test
    fun `importAsCard creates a card with no main words when none are picked`() = runBlocking {
        val wordDao = SsiFakeWordDao()
        val sentenceDao = SsiFakeSentenceDao()
        val cardDao = SsiFakeCardDao()
        val sentenceImporter = SentenceImporter(wordDao, sentenceDao)
        val importer = SingleSentenceImporter(wordDao, cardDao, SsiFakeTokenizer, sentenceImporter)
        val structure = listOf(SentenceToken(word = "文", kind = TokenKind.WORD.code, id = 1L))

        importer.importAsCard(sentenceText = "文", structure = structure, translation = "sentence", mainWordIds = emptySet())

        assertEquals(emptyList<Long>(), cardDao.cards.single().mainWordIds)
    }

    @Test
    fun `parse resolves an inflected word to an already-tracked word by its dictionary form`() = runBlocking {
        val wordDao = SsiFakeWordDao()
        wordDao.byId[1L] = WordEntity(id = 1L, word = "食べる")
        val sentenceDao = SsiFakeSentenceDao()
        val cardDao = SsiFakeCardDao()
        val sentenceImporter = SentenceImporter(wordDao, sentenceDao)
        val tokenizer = SsiFakeInflectingTokenizer(surface = "食べた", dictForm = "食べる")
        val importer = SingleSentenceImporter(wordDao, cardDao, tokenizer, sentenceImporter)

        val result = importer.parse("食べた。") as SingleSentenceParseResult.Ok

        // Reuses the existing "食べる" WordEntity's id instead of minting a new one for "食べた".
        assertEquals(1L, result.structure.single().id)
    }
}
