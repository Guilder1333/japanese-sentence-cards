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

    /**
     * The local sentence matching [text] exactly, if any - used by
     * [com.griboedov.sentencecards.data.backup.DriveBackupService.restore] to backfill a
     * translation already known from a restored card onto the matching local sentence pool entry
     * by content rather than by id, since sentence ids aren't stable across a fresh import (see
     * that class's doc comment).
     */
    @Query("SELECT * FROM sentences WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): SentenceEntity?

    /** Fills in a sentence's translation only if it doesn't have one yet - never overwrites an existing one. See [findByText]. */
    @Query("UPDATE sentences SET translation = :translation WHERE id = :id AND translation = ''")
    suspend fun updateTranslationIfBlank(id: Long, translation: String)
}
