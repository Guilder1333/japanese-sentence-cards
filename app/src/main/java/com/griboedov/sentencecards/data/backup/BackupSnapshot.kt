package com.griboedov.sentencecards.data.backup

import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.WordProgress
import kotlinx.serialization.Serializable

/**
 * Everything backed up to the Drive app-data folder: cards in full (the actual review/queue
 * state), plus only the [WordProgress] subset of word rows the user has actually acted on.
 * Sentences, and the rest of the words table (dictionary content, view-count stats), have their
 * own source of truth - the sentence pool/dictionary import - and are never included here.
 *
 * [version] and decoding with `ignoreUnknownKeys = true` (see [DriveBackupService]), mirroring
 * [com.griboedov.sentencecards.data.db.Converters], exist so a future schema change doesn't break
 * restoring an older backup.
 */
@Serializable
data class BackupSnapshot(
    val version: Int = 1,
    val exportedAtEpochMillis: Long,
    val cards: List<CardEntity>,
    val wordProgress: List<WordProgress>,
)
