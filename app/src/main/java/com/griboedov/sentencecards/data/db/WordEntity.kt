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
    /**
     * Whether furigana shows on the front of the card. Off by default for every word, including
     * brand-new ones - front furigana is opt-in, only ever turned on by an explicit "force
     * furigana" action (word browser / dictionary, or the 4-direction menu's down flick). Marking
     * known ([com.griboedov.sentencecards.data.repository.WordRepository.markKnown]) and a correct
     * quiz answer both clear it, in case it had been forced on.
     */
    val forceFurigana: Boolean = false,
    val quizSuccess: Int = 0,
    val quizFails: Int = 0,
)
