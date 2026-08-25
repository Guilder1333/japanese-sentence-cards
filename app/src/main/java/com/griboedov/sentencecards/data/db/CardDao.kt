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

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    /** Sentence ids that already have a card, of any kind - so a sentence never backs two cards. */
    @Query("SELECT DISTINCT sentenceId FROM cards")
    suspend fun cardedSentenceIds(): List<Long>
}
