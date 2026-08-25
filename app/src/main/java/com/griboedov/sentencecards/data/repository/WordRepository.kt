package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.db.WordEntity
import kotlinx.coroutines.flow.Flow

/** The three actions available for adding a dictionary word into the internal words database. */
enum class WordStatusChoice(val label: String) {
    KNOWN("known"),
    TO_LEARN("to-learn"),
    HIDE_FURIGANA("hide furigana"),
}

/**
 * Read/write access to the known-words/kanji table, plus the actions the word 4-direction menu
 * and word browser expose.
 */
class WordRepository(private val dao: WordDao) {

    fun observeAll(): Flow<List<WordEntity>> = dao.observeAll()

    suspend fun getByIds(ids: List<Long>) = dao.getByIds(ids)

    /** 4-direction menu: right - mark word as known. */
    suspend fun markKnown(id: Long) = updateWord(id) { it.copy(toLearn = false, forceFurigana = false) }

    /** 4-direction menu: left - mark word for learning. */
    suspend fun markToLearn(id: Long) = updateWord(id) { it.copy(toLearn = true, forceFurigana = true) }

    /** 4-direction menu: down - hide furigana (strong "well known" marker, not the same as learned). */
    suspend fun hideFurigana(id: Long) = updateWord(id) { it.copy(hideFurigana = true) }

    suspend fun setToLearn(id: Long, toLearn: Boolean) = updateWord(id) { it.copy(toLearn = toLearn) }

    suspend fun setHideFurigana(id: Long, hidden: Boolean) = updateWord(id) { it.copy(hideFurigana = hidden) }

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
     */
    suspend fun addFromDictionary(word: String, furigana: String?, translation: String, status: WordStatusChoice): Long {
        val existing = dao.findByWord(word)
        val base = existing ?: WordEntity(id = (dao.maxId() ?: 0L) + 1, word = word, furigana = furigana, translation = translation)
        val updated = when (status) {
            WordStatusChoice.KNOWN -> base.copy(translation = translation, toLearn = false, forceFurigana = false)
            WordStatusChoice.TO_LEARN -> base.copy(translation = translation, toLearn = true, forceFurigana = true)
            WordStatusChoice.HIDE_FURIGANA -> base.copy(translation = translation, toLearn = false, forceFurigana = false, hideFurigana = true)
        }
        if (existing != null) dao.update(updated) else dao.upsert(updated)
        return updated.id
    }

    private suspend inline fun updateWord(id: Long, transform: (WordEntity) -> WordEntity) {
        val current = dao.getById(id) ?: return
        dao.update(transform(current))
    }
}
