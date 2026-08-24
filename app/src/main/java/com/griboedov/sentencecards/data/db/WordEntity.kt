package com.griboedov.sentencecards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single known (or to-be-known) kanji/word, with the tracking metrics described in the
 * README's "Maintain list of known kanji/words" section.
 *
 * [id] is not auto-generated: it is assigned when the word is first encountered during sentence
 * import (see [com.griboedov.sentencecards.data.importer.SentenceImporter]) and reused by every
 * later sentence that contains the same word, matching "ID is assigned to the word present in
 * database" in the README.
 */
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: Long,
    val word: String,
    val furigana: String?,
    val translation: String,
    val timesShown: Int = 0,
    val timesFuriganaShown: Int = 0,
    val timesTranslationShown: Int = 0,
    val toLearn: Boolean = false,
    val hideFurigana: Boolean = false,
    /** Forces furigana to show even on the front of the card. Defaults on for brand-new words. */
    val forceFurigana: Boolean = true,
    val quizSuccess: Int = 0,
    val quizFails: Int = 0,
)
