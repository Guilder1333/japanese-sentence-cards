package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.SentenceWordCrossRef
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.db.WordProgress
import com.griboedov.sentencecards.data.translation.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fakes so [CardGenerator]'s DB orchestration can be unit tested without Room/Robolectric. */
private class FakeSentenceDao(sentences: List<SentenceEntity>, wordRefs: List<SentenceWordCrossRef>) : SentenceDao {
    val byId = sentences.associateBy { it.id }.toMutableMap()
    private val refs = wordRefs

    override suspend fun upsertAll(sentences: List<SentenceEntity>): List<Long> {
        for (sentence in sentences) byId[sentence.id] = sentence
        return sentences.map { it.id }
    }
    override suspend fun getByIds(ids: List<Long>): List<SentenceEntity> = ids.mapNotNull { byId[it] }
    override suspend fun count(): Int = byId.size
    override suspend fun insertWordRefs(refs: List<SentenceWordCrossRef>) = error("not needed")
    override suspend fun sentenceIdsContaining(wordId: Long): List<Long> =
        refs.filter { it.wordId == wordId }.map { it.sentenceId }
    override suspend fun findByText(text: String): SentenceEntity? = byId.values.find { it.text == text }
    override suspend fun updateTranslationIfBlank(id: Long, translation: String) {
        val sentence = byId[id] ?: return
        if (sentence.translation.isBlank()) byId[id] = sentence.copy(translation = translation)
    }
}

/** Records every sentence text it's asked to translate, and returns a fixed canned translation. */
private class FakeTranslator(private val result: String? = "translated") : Translator {
    val requested = mutableListOf<String>()
    override suspend fun translate(text: String): String? {
        requested += text
        return result
    }
}

private class FakeCardDao(initial: List<CardEntity> = emptyList()) : CardDao {
    val cards = mutableListOf<CardEntity>().apply { addAll(initial) }
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeAll(): Flow<List<CardEntity>> = flowOf(cards.toList())

    override suspend fun upsertAll(cards: List<CardEntity>) {
        for (card in cards) {
            val index = this.cards.indexOfFirst { it.id == card.id }
            if (index >= 0) {
                this.cards[index] = card
            } else {
                this.cards += card.copy(id = nextId++)
            }
        }
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

private class FakeWordDao(words: List<WordEntity>) : WordDao {
    private val byId = words.associateBy { it.id }

    override fun observeAll(): Flow<List<WordEntity>> = flowOf(byId.values.toList())
    override suspend fun getById(id: Long): WordEntity? = byId[id]
    override suspend fun getByIds(ids: List<Long>): List<WordEntity> = ids.mapNotNull { byId[it] }
    override suspend fun findByWord(word: String): WordEntity? = byId.values.find { it.word == word }
    override suspend fun upsert(word: WordEntity) = error("not needed")
    override suspend fun upsertAll(words: List<WordEntity>) = error("not needed")
    override suspend fun update(word: WordEntity) = error("not needed")
    override suspend fun maxId(): Long? = byId.keys.maxOrNull()
    override suspend fun allIds(): List<Long> = byId.keys.toList()
    override suspend fun count(): Int = byId.size

    override suspend fun getProgress(): List<WordProgress> = byId.values
        .filter { it.toLearn || it.forceFurigana || it.quizSuccess != 0 || it.quizFails != 0 }
        .map { WordProgress(it.id, it.toLearn, it.forceFurigana, it.quizSuccess, it.quizFails) }
    override suspend fun updateProgress(wordId: Long, toLearn: Boolean, forceFurigana: Boolean, quizSuccess: Int, quizFails: Int) =
        error("not needed")
}

private fun wordToken(id: Long) = SentenceToken(word = "w$id", kind = 1, id = id)

private fun sentence(id: Long, vararg wordIds: Long, translation: String = "translation$id") = SentenceEntity(
    id = id,
    text = "text$id",
    translation = translation,
    structure = wordIds.map { wordToken(it) },
)

private fun card(
    id: Long,
    sentenceId: Long,
    mainWordIds: List<Long>,
    learned: Boolean = false,
    translation: String = "translation$sentenceId",
) = CardEntity(
    id = id,
    sentenceId = sentenceId,
    text = "text$sentenceId",
    translation = translation,
    structure = listOf(wordToken(mainWordIds.first())),
    mainWordIds = mainWordIds,
    learned = learned,
    queueLevel = if (learned) QueueLevel.BACKLOG else QueueLevel.HIGHEST,
)

class CardGeneratorTest {

    @Test
    fun `with no existing cards, generates up to 3 new cards for the word`() = runBlocking {
        val sentences = listOf(sentence(1, 100L), sentence(2, 100L), sentence(3, 100L), sentence(4, 100L))
        val refs = sentences.map { SentenceWordCrossRef(sentenceId = it.id, wordId = 100L) }
        val cardDao = FakeCardDao()
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()))

        generator.generateCardsForWord(100L)

        assertEquals(3, cardDao.cards.size)
        assertTrue(cardDao.cards.all { it.mainWordIds == listOf(100L) })
    }

    @Test
    fun `reuses a card already covering the word's sentence instead of duplicating it`() = runBlocking {
        val sentences = listOf(sentence(1, 100L))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        // Card already exists for sentence 1, teaching a different word (200L).
        val cardDao = FakeCardDao(listOf(card(id = 1L, sentenceId = 1L, mainWordIds = listOf(200L))))
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()))

        generator.generateCardsForWord(100L)

        // No new card spawned - the existing one is reused, now teaching both words.
        assertEquals(1, cardDao.cards.size)
        assertEquals(listOf(200L, 100L), cardDao.cards.single().mainWordIds)
    }

    @Test
    fun `reactivates an already-learned card when a new word is added to it`() = runBlocking {
        val sentences = listOf(sentence(1, 100L))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val cardDao = FakeCardDao(listOf(card(id = 1L, sentenceId = 1L, mainWordIds = listOf(200L), learned = true)))
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()))

        generator.generateCardsForWord(100L)

        val reused = cardDao.cards.single()
        assertEquals(listOf(200L, 100L), reused.mainWordIds)
        assertEquals(false, reused.learned)
        assertEquals(QueueLevel.HIGHEST, reused.queueLevel)
    }

    @Test
    fun `fills only the shortfall when some cards are already reused`() = runBlocking {
        val sentences = listOf(sentence(1, 100L), sentence(2, 100L), sentence(3, 100L))
        val refs = sentences.map { SentenceWordCrossRef(sentenceId = it.id, wordId = 100L) }
        // Sentence 1 already has a card (for another word) - should be reused, not duplicated.
        val cardDao = FakeCardDao(listOf(card(id = 1L, sentenceId = 1L, mainWordIds = listOf(200L))))
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()))

        generator.generateCardsForWord(100L)

        // 1 reused + 2 new = 3 total covering the word; sentence 2 and 3 each got a fresh card.
        assertEquals(3, cardDao.cards.size)
        assertEquals(listOf(200L, 100L), cardDao.cards.first { it.sentenceId == 1L }.mainWordIds)
        assertTrue(cardDao.cards.filter { it.sentenceId != 1L }.all { it.mainWordIds == listOf(100L) })
    }

    @Test
    fun `does not spawn new cards once 3 or more existing cards already cover the word`() = runBlocking {
        val sentences = listOf(sentence(1, 100L), sentence(2, 100L), sentence(3, 100L), sentence(4, 100L))
        val refs = sentences.map { SentenceWordCrossRef(sentenceId = it.id, wordId = 100L) }
        val existing = listOf(
            card(id = 1L, sentenceId = 1L, mainWordIds = listOf(200L)),
            card(id = 2L, sentenceId = 2L, mainWordIds = listOf(201L)),
            card(id = 3L, sentenceId = 3L, mainWordIds = listOf(202L)),
        )
        val cardDao = FakeCardDao(existing)
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()))

        generator.generateCardsForWord(100L)

        // All 3 existing cards get the word added, but sentence 4 never gets a card - already at the cap.
        assertEquals(3, cardDao.cards.size)
        assertTrue(cardDao.cards.all { 100L in it.mainWordIds })
    }

    @Test
    fun `translates an untranslated sentence when it's picked for a new card, and persists it back to the pool`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = ""))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val sentenceDao = FakeSentenceDao(sentences, refs)
        val cardDao = FakeCardDao()
        val translator = FakeTranslator(result = "translated1")
        val generator = CardGenerator(sentenceDao, cardDao, FakeWordDao(emptyList()), translator)

        generator.generateCardsForWord(100L)

        assertEquals(listOf("text1"), translator.requested)
        assertEquals("translated1", cardDao.cards.single().translation)
        assertEquals("translated1", sentenceDao.byId.getValue(1L).translation)
    }

    @Test
    fun `does not call the translator for a sentence that already has a translation`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = "already translated"))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val cardDao = FakeCardDao()
        val translator = FakeTranslator()
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()), translator)

        generator.generateCardsForWord(100L)

        assertTrue(translator.requested.isEmpty())
        assertEquals("already translated", cardDao.cards.single().translation)
    }

    @Test
    fun `leaves the card's translation blank if the translator can't produce one`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = ""))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val cardDao = FakeCardDao()
        val translator = FakeTranslator(result = null)
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), cardDao, FakeWordDao(emptyList()), translator)

        generator.generateCardsForWord(100L)

        assertEquals("", cardDao.cards.single().translation)
    }

    @Test
    fun `backfills translation on a reused card that was created before it had one`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = ""))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val sentenceDao = FakeSentenceDao(sentences, refs)
        // Existing card for sentence 1 (teaching a different word) predates translation - blank.
        val cardDao = FakeCardDao(listOf(card(id = 1L, sentenceId = 1L, mainWordIds = listOf(200L), translation = "")))
        val translator = FakeTranslator(result = "translated1")
        val generator = CardGenerator(sentenceDao, cardDao, FakeWordDao(emptyList()), translator)

        generator.generateCardsForWord(100L)

        val reused = cardDao.cards.single()
        assertEquals(listOf(200L, 100L), reused.mainWordIds)
        assertEquals("translated1", reused.translation)
        assertEquals("translated1", sentenceDao.byId.getValue(1L).translation)
    }

    @Test
    fun `translateCardIfNeeded retries a still-untranslated card, such as when shown again in review`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = ""))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val sentenceDao = FakeSentenceDao(sentences, refs)
        val translator = FakeTranslator(result = "translated1")
        val generator = CardGenerator(sentenceDao, FakeCardDao(), FakeWordDao(emptyList()), translator)
        val untranslatedCard = card(id = 1L, sentenceId = 1L, mainWordIds = listOf(100L), translation = "")

        val result = generator.translateCardIfNeeded(untranslatedCard)

        assertEquals("translated1", result.translation)
        assertEquals("translated1", sentenceDao.byId.getValue(1L).translation)
    }

    @Test
    fun `translateCardIfNeeded leaves an already-translated card untouched and never calls the translator`() = runBlocking {
        val sentences = listOf(sentence(1, 100L, translation = "already translated"))
        val refs = listOf(SentenceWordCrossRef(sentenceId = 1L, wordId = 100L))
        val translator = FakeTranslator()
        val generator = CardGenerator(FakeSentenceDao(sentences, refs), FakeCardDao(), FakeWordDao(emptyList()), translator)
        val translatedCard = card(id = 1L, sentenceId = 1L, mainWordIds = listOf(100L), translation = "already translated")

        val result = generator.translateCardIfNeeded(translatedCard)

        assertTrue(translator.requested.isEmpty())
        assertEquals("already translated", result.translation)
    }
}
