package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import kotlinx.coroutines.flow.Flow

class SentenceRepository(private val dao: SentenceDao) {

    fun observeAll(): Flow<List<SentenceEntity>> = dao.observeAll()

    suspend fun update(sentence: SentenceEntity) = dao.update(sentence)

    suspend fun insertAll(sentences: List<SentenceEntity>) = dao.upsertAll(sentences)

    suspend fun count() = dao.count()
}
