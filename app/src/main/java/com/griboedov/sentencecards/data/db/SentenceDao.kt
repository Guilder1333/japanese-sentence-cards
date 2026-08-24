package com.griboedov.sentencecards.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences ORDER BY id")
    fun observeAll(): Flow<List<SentenceEntity>>

    @Upsert
    suspend fun upsertAll(sentences: List<SentenceEntity>)

    @Update
    suspend fun update(sentence: SentenceEntity)

    @Query("SELECT COUNT(*) FROM sentences")
    suspend fun count(): Int
}
