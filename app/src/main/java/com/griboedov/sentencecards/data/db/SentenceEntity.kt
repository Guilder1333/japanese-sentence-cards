package com.griboedov.sentencecards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored flash card sentence, matching the README's "Flash cards storage" / "Sentence storage"
 * fields.
 *
 * Queueing: [queueLevel] is the priority queue the card currently sits in. [lastMarkedLevel]
 * records which review button was last pressed, so [com.griboedov.sentencecards.data.queue]
 * logic can detect "marked the same level twice in a row" and demote a level further.
 *
 * Quiz: marking a card "Learned" does not finish it immediately - it sets [pendingQuiz] and
 * seeds [quizRemainingWordIds] from [mainWordIds]. The next time this card comes up for review it
 * is rendered as a quiz card instead of a normal front/back card; answering a word correctly
 * removes it from [quizRemainingWordIds], and once that list is empty the sentence becomes
 * [learned].
 */
@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val translation: String,
    val structure: List<SentenceToken>,
    /** Word ids this sentence was picked to teach - the words quizzed once marked "Learned". */
    val mainWordIds: List<Long>,
    val shownTimes: Int = 0,
    val learned: Boolean = false,
    val queueLevel: QueueLevel = QueueLevel.HIGHEST,
    val lastMarkedLevel: QueueLevel? = null,
    val pendingQuiz: Boolean = false,
    val quizRemainingWordIds: List<Long> = emptyList(),
)
