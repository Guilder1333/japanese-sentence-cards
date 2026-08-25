package com.griboedov.sentencecards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SentenceDao {
    @Upsert
    suspend fun upsertAll(sentences: List<SentenceEntity>): List<Long>

    @Query("SELECT * FROM sentences WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SentenceEntity>

    @Query("SELECT COUNT(*) FROM sentences")
    suspend fun count(): Int

    /** Ignores duplicates: a word repeated within one sentence only needs one link row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordRefs(refs: List<SentenceWordCrossRef>)

    /** Every sentence id whose structure contains [wordId] as a kind=WORD token - see [SentenceWordCrossRef]. */
    @Query("SELECT sentenceId FROM sentence_words WHERE wordId = :wordId")
    suspend fun sentenceIdsContaining(wordId: Long): List<Long>
}
