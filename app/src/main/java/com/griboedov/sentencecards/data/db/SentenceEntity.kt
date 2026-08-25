package com.griboedov.sentencecards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One raw sentence in the (potentially enormous, e.g. a whole imported book) sentence pool -
 * matching the README's "Sentence storage" fields.
 *
 * This is deliberately *not* a flash card: importing a sentence only adds it to this searchable
 * pool. A sentence only becomes a reviewable [CardEntity] once it's picked as one of the "3 best
 * fitting sentences" for a word the user marks to-learn - see
 * [com.griboedov.sentencecards.data.cards.CardGenerator].
 */
@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val translation: String,
    val structure: List<SentenceToken>,
)
