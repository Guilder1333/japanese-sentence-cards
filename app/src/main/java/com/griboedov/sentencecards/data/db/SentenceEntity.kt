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
 * Quiz: marking a card "Learned" moves it to the medium queue and sets [pendingQuiz] - it does
 * NOT finish the card, and it stays visible in review. The next time this card comes up it is
 * rendered as a quiz card (reading multiple-choice for every word in [mainWordIds]) instead of a
 * normal front/back card, and [pendingQuiz] always clears after that one round - it is never
 * re-quizzed back-to-back. Words answered correctly are removed from [mainWordIds]; if any remain
 * afterwards, the card returns to normal front/back review at the hard queue (marking it Learned
 * again later re-quizzes just what's left), and once [mainWordIds] is empty the sentence is
 * [learned] and [quizSucceeded].
 */
@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val translation: String,
    val structure: List<SentenceToken>,
    /** Word ids this sentence was picked to teach. Shrinks as the quiz is answered correctly. */
    val mainWordIds: List<Long>,
    val shownTimes: Int = 0,
    val learned: Boolean = false,
    val queueLevel: QueueLevel = QueueLevel.HIGHEST,
    val lastMarkedLevel: QueueLevel? = null,
    val pendingQuiz: Boolean = false,
    val quizSucceeded: Boolean = false,
)
