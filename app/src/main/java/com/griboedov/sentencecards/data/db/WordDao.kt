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
}
