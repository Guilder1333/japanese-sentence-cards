package com.griboedov.sentencecards.data.importer

import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fakes, same pattern as CardGeneratorTest's - real enough to exercise the DB writes without Room/Robolectric. */
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
}

private class FakeSentenceDao : SentenceDao {
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

class StructuredImportTest {

    @Test
    fun `importOne creates a WordEntity for an id not yet in the DB`() = runBlocking {
        val wordDao = FakeWordDao()
        val sentenceDao = FakeSentenceDao()
        val importer = SentenceImporter(wordDao, sentenceDao)
        val token = SentenceToken(word = "言葉", translation = "word", kind = TokenKind.WORD.code, furigana = "ことば", id = 42L)

        val result = importer.importOne(ImportSentence(translation = "", structure = listOf(token)))

        assertTrue(result.id != 0L)
        assertEquals(WordEntity(id = 42L, word = "言葉", furigana = "ことば", translation = "word"), wordDao.byId[42L])
        assertEquals(listOf(SentenceWordCrossRef(sentenceId = result.id, wordId = 42L)), sentenceDao.wordRefs)
    }

    @Test
    fun `importOne leaves an already-tracked word's entity untouched`() = runBlocking {
        val existing = WordEntity(id = 42L, word = "言葉", furigana = "ことば", translation = "word", toLearn = true)
        val wordDao = FakeWordDao(listOf(existing))
        val sentenceDao = FakeSentenceDao()
        val importer = SentenceImporter(wordDao, sentenceDao)
        // A token referencing the same id, but (hypothetically) different text/gloss - should not
        // overwrite the existing WordEntity's data or its toLearn/progress state.
        val token = SentenceToken(word = "言葉", translation = "different gloss", kind = TokenKind.WORD.code, id = 42L)

        importer.importOne(ImportSentence(translation = "", structure = listOf(token)))

        assertEquals(existing, wordDao.byId[42L])
    }

    @Test
    fun `importOne derives sentence text from the structure when text is omitted`() = runBlocking {
        val wordDao = FakeWordDao()
        val sentenceDao = FakeSentenceDao()
        val importer = SentenceImporter(wordDao, sentenceDao)
        val structure = listOf(
            SentenceToken(word = "これ", translation = "", kind = TokenKind.PARTICLE.code),
            SentenceToken(word = "は", translation = "", kind = TokenKind.PARTICLE.code),
            SentenceToken(word = "文", translation = "sentence", kind = TokenKind.WORD.code, id = 1L),
        )

        val result = importer.importOne(ImportSentence(translation = "", structure = structure))

        assertEquals("これは文", result.text)
    }

    @Test
    fun `importOne does not create a WordEntity for non-WORD tokens`() = runBlocking {
        val wordDao = FakeWordDao()
        val sentenceDao = FakeSentenceDao()
        val importer = SentenceImporter(wordDao, sentenceDao)
        val token = SentenceToken(word = "は", translation = "topic marker", kind = TokenKind.PARTICLE.code)

        importer.importOne(ImportSentence(text = "は", translation = "", structure = listOf(token)))

        assertTrue(wordDao.byId.isEmpty())
        assertFalse(sentenceDao.wordRefs.isNotEmpty())
    }
}
