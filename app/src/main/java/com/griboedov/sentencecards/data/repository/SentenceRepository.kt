package com.griboedov.sentencecards.data.repository

import com.griboedov.sentencecards.data.db.SentenceDao

/** Read/write access to the raw sentence pool (see [com.griboedov.sentencecards.data.db.SentenceEntity]). */
class SentenceRepository(private val dao: SentenceDao) {

    suspend fun count() = dao.count()
}
