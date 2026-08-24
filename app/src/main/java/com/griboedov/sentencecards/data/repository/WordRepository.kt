package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.db.WordDao
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the known-words/kanji table, plus the actions the word 4-direction menu
 * and word browser expose.
 */
class WordRepository(private val dao: WordDao) {

    fun observeAll(): Flow<List<com.griboedov.sentencecards.data.db.WordEntity>> = dao.observeAll()

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

    private suspend inline fun updateWord(
        id: Long,
        transform: (com.griboedov.sentencecards.data.db.WordEntity) -> com.griboedov.sentencecards.data.db.WordEntity,
    ) {
        val current = dao.getById(id) ?: return
        dao.update(transform(current))
    }
}
