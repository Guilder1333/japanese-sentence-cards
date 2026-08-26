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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fakes so [CardGenerator]'s DB orchestration can be unit tested without Room/Robolectric. */
private class FakeSentenceDao(sentences: List<SentenceEntity>, wordRefs: List<SentenceWordCrossRef>) : SentenceDao {
    private val byId = sentences.associateBy { it.id }
    private val refs = wordRefs

    override suspend fun upsertAll(sentences: List<SentenceEntity>): List<Long> = error("not needed")
    override suspend fun getByIds(ids: List<Long>): List<SentenceEntity> = ids.mapNotNull { byId[it] }
    override suspend fun count(): Int = byId.size
    override suspend fun insertWordRefs(refs: List<SentenceWordCrossRef>) = error("not needed")
    override suspend fun sentenceIdsContaining(wordId: Long): List<Long> =
        refs.filter { it.wordId == wordId }.map { it.sentenceId }
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
}

private fun wordToken(id: Long) = SentenceToken(word = "w$id", translation = "", kind = 1, id = id)

private fun sentence(id: Long, vararg wordIds: Long) = SentenceEntity(
    id = id,
    text = "text$id",
    translation = "translation$id",
    structure = wordIds.map { wordToken(it) },
)

private fun card(id: Long, sentenceId: Long, mainWordIds: List<Long>, learned: Boolean = false) = CardEntity(
    id = id,
    sentenceId = sentenceId,
    text = "text$sentenceId",
    translation = "translation$sentenceId",
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
}
