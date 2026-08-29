package com.griboedov.sentencecards.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY id")
    fun observeAll(): Flow<List<CardEntity>>

    /** One-shot equivalent of [observeAll], for [com.griboedov.sentencecards.data.backup.DriveBackupService] to export the full set. */
    @Query("SELECT * FROM cards ORDER BY id")
    suspend fun getAll(): List<CardEntity>

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    /** Restore-from-backup replaces every card, since a backup always contains the full set - see [com.griboedov.sentencecards.data.backup.DriveBackupService.restore]. */
    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    /**
     * Every existing card backed by one of [sentenceIds] - so
     * [com.griboedov.sentencecards.data.cards.CardGenerator] can both find cards to reuse (add a
     * newly to-learn word to an already-carded sentence's main words, instead of spawning a
     * duplicate card for it) and know which of [sentenceIds] are already carded and thus excluded
     * from fresh candidates.
     */
    @Query("SELECT * FROM cards WHERE sentenceId IN (:sentenceIds)")
    suspend fun cardsForSentences(sentenceIds: Collection<Long>): List<CardEntity>
}
