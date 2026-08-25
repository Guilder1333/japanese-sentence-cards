package com.griboedov.sentencecards.data.db

import androidx.room.Entity
import androidx.room.Index

/**
 * One (sentence, word) link, for every kind=WORD token a sentence's [SentenceEntity.structure]
 * contains. Exists purely as a fast index - "which sentences contain word X" - so
 * [com.griboedov.sentencecards.data.cards.CardGenerator] can search a potentially enormous
 * sentence pool without scanning/deserializing every row's structure.
 *
 * Populated at import time (see [com.griboedov.sentencecards.data.importer.SentenceImporter]),
 * one row per word occurrence - a word repeated within one sentence is only linked once, since a
 * composite primary key naturally de-duplicates that.
 */
@Entity(
    tableName = "sentence_words",
    primaryKeys = ["sentenceId", "wordId"],
    indices = [Index("wordId")],
)
data class SentenceWordCrossRef(
    val sentenceId: Long,
    val wordId: Long,
)
