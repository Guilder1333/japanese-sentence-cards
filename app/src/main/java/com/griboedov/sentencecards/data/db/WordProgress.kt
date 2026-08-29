package com.griboedov.sentencecards.data.db

import kotlinx.serialization.Serializable

/**
 * The subset of a [WordEntity]'s columns that count as "backed-up progress" - see
 * [com.griboedov.sentencecards.data.backup.BackupSnapshot]. Everything else on [WordEntity]
 * ([WordEntity.word]/[WordEntity.dictionaryEntryId], the view-count stats) has its own source of
 * truth (dictionary import) or is purely local usage tracking, and is never synced.
 *
 * Doubles as both the shape [WordDao.getProgress] projects Room's query result into (field names
 * must match the query's column aliases) and the JSON DTO written into the backup file.
 */
@Serializable
data class WordProgress(
    val wordId: Long,
    val toLearn: Boolean,
    val forceFurigana: Boolean,
    val quizSuccess: Int,
    val quizFails: Int,
)
