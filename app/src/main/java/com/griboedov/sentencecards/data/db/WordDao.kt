package com.griboedov.sentencecards.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY word")
    fun observeAll(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: Long): WordEntity?

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<WordEntity>

    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun findByWord(word: String): WordEntity?

    @Upsert
    suspend fun upsert(word: WordEntity)

    @Upsert
    suspend fun upsertAll(words: List<WordEntity>)

    @Update
    suspend fun update(word: WordEntity)

    @Query("SELECT MAX(id) FROM words")
    suspend fun maxId(): Long?

    /**
     * Every tracked word id, for [com.griboedov.sentencecards.data.importer.SentenceImporter] to
     * check "is this word already known" in memory during a large import instead of one DB round
     * trip per word token.
     */
    @Query("SELECT id FROM words")
    suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    /**
     * Every word the user has actually acted on - at least one of the four progress columns
     * differs from its default - for [com.griboedov.sentencecards.data.backup.DriveBackupService]
     * to export. See [WordProgress] for why only these four columns (not the rest of the row) are
     * backed up.
     */
    @Query(
        "SELECT id AS wordId, toLearn, forceFurigana, quizSuccess, quizFails FROM words " +
            "WHERE toLearn = 1 OR forceFurigana = 1 OR quizSuccess != 0 OR quizFails != 0",
    )
    suspend fun getProgress(): List<WordProgress>

    /**
     * Applies one restored [WordProgress] entry by id, touching only these four columns - never
     * inserts a new word row, so a [wordId] not present locally (not imported on this device yet)
     * is silently a no-op. See [com.griboedov.sentencecards.data.backup.DriveBackupService.restore].
     */
    @Query(
        "UPDATE words SET toLearn = :toLearn, forceFurigana = :forceFurigana, " +
            "quizSuccess = :quizSuccess, quizFails = :quizFails WHERE id = :wordId",
    )
    suspend fun updateProgress(wordId: Long, toLearn: Boolean, forceFurigana: Boolean, quizSuccess: Int, quizFails: Int)
}
