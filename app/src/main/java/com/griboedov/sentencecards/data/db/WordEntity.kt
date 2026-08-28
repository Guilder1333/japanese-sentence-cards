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
    /** Always this word's dictionary/citation form (e.g. "食べる", never "食べた") - see [com.griboedov.sentencecards.data.db.SentenceToken.dictForm]. */
    val word: String,
    /**
     * The bundled dictionary's stable entry id for this word (see [com.griboedov.sentencecards.data.dictionary.DictionaryEntry.id]),
     * or null if nothing matched at the time this word was tracked. Reading and meaning are looked
     * up through this reference rather than duplicated here - see
     * [com.griboedov.sentencecards.data.dictionary.DictionaryRepository.getById]/`getByIds`.
     */
    val dictionaryEntryId: Long? = null,
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
