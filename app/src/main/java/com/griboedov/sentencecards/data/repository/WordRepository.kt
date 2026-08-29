package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.cards.CardGenerator
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import kotlinx.coroutines.flow.Flow

/** The three actions available for adding a dictionary word into the internal words database. */
enum class WordStatusChoice(val label: String) {
    KNOWN("known"),
    TO_LEARN("to-learn"),
    FORCE_FURIGANA("force furigana"),
}

/**
 * Read/write access to the known-words/kanji table, plus the actions the word 4-direction menu
 * and word browser expose.
 *
 * Every path that marks a word to-learn also triggers [CardGenerator] - the README's "Adding word
 * to learn" feature - to search the sentence pool and turn the 3 best-fitting sentences into cards.
 */
class WordRepository(private val dao: WordDao, private val cardGenerator: CardGenerator) {

    fun observeAll(): Flow<List<WordEntity>> = dao.observeAll()

    suspend fun getByIds(ids: List<Long>) = dao.getByIds(ids)

    /** 4-direction menu: right - mark word as known. */
    suspend fun markKnown(id: Long) = updateWord(id) { it.copy(toLearn = false, forceFurigana = false) }

    /**
     * 4-direction menu: left - mark word for learning. Doesn't touch [WordEntity.forceFurigana] -
     * front furigana is a separate, explicit opt-in (see [forceFurigana]/[setForceFurigana]), not
     * bundled into "to learn".
     */
    suspend fun markToLearn(id: Long) {
        updateWord(id) { it.copy(toLearn = true) }
        cardGenerator.generateCardsForWord(id)
    }

    /** 4-direction menu: down - force furigana back on, e.g. for an otherwise-known word you still want the help for. */
    suspend fun forceFurigana(id: Long) = updateWord(id) { it.copy(forceFurigana = true) }

    suspend fun setToLearn(id: Long, toLearn: Boolean) {
        updateWord(id) { it.copy(toLearn = toLearn) }
        if (toLearn) cardGenerator.generateCardsForWord(id)
    }

    suspend fun setForceFurigana(id: Long, forced: Boolean) = updateWord(id) { it.copy(forceFurigana = forced) }

    /** Called whenever a card carrying this word is shown; increments the tracking metrics. */
    suspend fun recordShown(ids: Collection<Long>, furiganaShownIds: Collection<Long>) {
        val furiganaSet = furiganaShownIds.toSet()
        for (id in ids) {
            updateWord(id) {
                it.copy(
                    timesShown = it.timesShown + 1,
                    timesFuriganaShown = it.timesFuriganaShown + if (id in furiganaSet) 1 else 0,
                )
            }
        }
    }

    suspend fun recordTranslationShown(id: Long) =
        updateWord(id) { it.copy(timesTranslationShown = it.timesTranslationShown + 1) }

    /**
     * A correct quiz answer "graduates" the word the same way manually marking it known does
     * ([markKnown]) - otherwise it keeps its forceFurigana crutch forever, since nothing else
     * ever clears that flag once the word stops being a sentence's main word.
     */
    suspend fun recordQuizResult(id: Long, correct: Boolean) = updateWord(id) {
        if (correct) {
            it.copy(quizSuccess = it.quizSuccess + 1, toLearn = false, forceFurigana = false)
        } else {
            it.copy(quizFails = it.quizFails + 1)
        }
    }

    /**
     * Adds a word looked up from the bundled dictionary into the internal words database (or
     * updates it, if a word with the same text is already tracked - e.g. re-adding it with a
     * different status). This is how a dictionary entry becomes a real, trackable [WordEntity]:
     * dictionary browsing has no metrics of its own, only the internal table does.
     *
     * Also the promotion path for a kana word met during review ([com.griboedov.sentencecards.ui.review.ReviewViewModel.onWordDirection]),
     * which is why [dictionaryEntryId] is nullable: those aren't resolved at import time (see
     * [com.griboedov.sentencecards.data.db.SentenceToken]), and a kana word with no JMdict match
     * should still be trackable rather than silently refused. A null never clears an entry id an
     * already-tracked word had resolved.
     */
    suspend fun addFromDictionary(word: String, dictionaryEntryId: Long?, status: WordStatusChoice): Long {
        val existing = dao.findByWord(word)
        val base = existing ?: WordEntity(id = (dao.maxId() ?: 0L) + 1, word = word, dictionaryEntryId = dictionaryEntryId)
        val entryId = dictionaryEntryId ?: base.dictionaryEntryId
        val updated = when (status) {
            WordStatusChoice.KNOWN -> base.copy(dictionaryEntryId = entryId, toLearn = false, forceFurigana = false)
            WordStatusChoice.TO_LEARN -> base.copy(dictionaryEntryId = entryId, toLearn = true)
            WordStatusChoice.FORCE_FURIGANA -> base.copy(dictionaryEntryId = entryId, toLearn = false, forceFurigana = true)
        }
        if (existing != null) dao.update(updated) else dao.upsert(updated)
        if (status == WordStatusChoice.TO_LEARN) cardGenerator.generateCardsForWord(updated.id)
        return updated.id
    }

    private suspend inline fun updateWord(id: Long, transform: (WordEntity) -> WordEntity) {
        val current = dao.getById(id) ?: return
        dao.update(transform(current))
    }
}
