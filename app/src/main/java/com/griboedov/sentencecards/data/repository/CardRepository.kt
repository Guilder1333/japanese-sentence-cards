package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import kotlinx.coroutines.flow.Flow

class CardRepository(private val dao: CardDao) {

    fun observeAll(): Flow<List<CardEntity>> = dao.observeAll()

    suspend fun update(card: CardEntity) = dao.update(card)
}
