package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.cards.CardGenerator
import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.db.WordProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fakes, same pattern as CardGeneratorTest's/StructuredImportTest's. */
private class FakeWordDao(words: List<WordEntity> = emptyList()) : WordDao {
    val byId = words.associateBy { it.id }.toMutableMap()

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
    override suspend fun updateProgress(wordId: Long, toLearn: Boolean, forceFurigana: Boolean, quizSuccess: Int, quizFails: Int) =
        error("not needed")
}

private class FakeSentenceDao : SentenceDao {
    override suspend fun upsertAll(sentences: List<SentenceEntity>): List<Long> = error("not needed")
    override suspend fun getByIds(ids: List<Long>): List<SentenceEntity> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun insertWordRefs(refs: List<SentenceWordCrossRef>) = error("not needed")
    override suspend fun sentenceIdsContaining(wordId: Long): List<Long> = emptyList()
    override suspend fun findByText(text: String): SentenceEntity? = null
    override suspend fun updateTranslationIfBlank(id: Long, translation: String) = Unit
}

private class FakeCardDao : CardDao {
    override fun observeAll(): Flow<List<CardEntity>> = flowOf(emptyList())
    override suspend fun upsertAll(cards: List<CardEntity>) = Unit
    override suspend fun update(card: CardEntity) = Unit
    override suspend fun count(): Int = 0
    override suspend fun cardsForSentences(sentenceIds: Collection<Long>): List<CardEntity> = emptyList()
    override suspend fun getAll(): List<CardEntity> = emptyList()
    override suspend fun deleteAll() = Unit
}

private fun repository(dao: WordDao) =
    WordRepository(dao, CardGenerator(FakeSentenceDao(), FakeCardDao(), dao))

class WordRepositoryTest {

    @Test
    fun `addFromDictionary promotes an untracked kana word with no dictionary match`() = runBlocking {
        val dao = FakeWordDao()

        // The review screen's flick-left on a kana word: nothing in JMdict matched, so there's no
        // entry id to attach - the word should still become trackable rather than be dropped.
        val id = repository(dao).addFromDictionary("わかる", dictionaryEntryId = null, status = WordStatusChoice.TO_LEARN)

        val stored = dao.getById(id)!!
        assertEquals("わかる", stored.word)
        assertTrue(stored.toLearn)
        assertNull(stored.dictionaryEntryId)
    }

    @Test
    fun `addFromDictionary reuses the existing row for a kana word met twice`() = runBlocking {
        val dao = FakeWordDao()
        val repo = repository(dao)

        val first = repo.addFromDictionary("わかる", dictionaryEntryId = null, status = WordStatusChoice.TO_LEARN)
        val second = repo.addFromDictionary("わかる", dictionaryEntryId = 1_234L, status = WordStatusChoice.KNOWN)

        // Same word text - matched by findByWord - so the second flick updates the row rather than
        // spawning a duplicate one with its own metrics.
        assertEquals(first, second)
        assertEquals(1, dao.count())
        assertEquals(false, dao.getById(first)!!.toLearn)
        assertEquals(1_234L, dao.getById(first)!!.dictionaryEntryId)
    }

    @Test
    fun `a null entry id never clears one an already-tracked word had resolved`() = runBlocking {
        val dao = FakeWordDao(listOf(WordEntity(id = 7, word = "わかる", dictionaryEntryId = 1_358_280L)))

        repository(dao).addFromDictionary("わかる", dictionaryEntryId = null, status = WordStatusChoice.TO_LEARN)

        assertEquals(1_358_280L, dao.getById(7)!!.dictionaryEntryId)
    }
}
