package com.griboedov.sentencecards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A stored flash card, matching the README's "Flash cards storage" fields.
 *
 * A card is a snapshot of one [SentenceEntity] (referenced by [sentenceId], for traceability back
 * to the raw sentence pool) picked out to teach specific words - see
 * [com.griboedov.sentencecards.data.cards.CardGenerator]. [text]/[translation]/[structure] are
 * copied from that sentence at creation time rather than joined live, since a card's wording
 * shouldn't shift under a user mid-review even if the sentence pool changes later.
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
 * again later re-quizzes just what's left), and once [mainWordIds] is empty the card is
 * [learned] and [quizSucceeded].
 *
 * `@Serializable` (in addition to the `@Entity`) so a card can be written straight into a Drive
 * backup - see [com.griboedov.sentencecards.data.backup.BackupSnapshot].
 */
@Serializable
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The [SentenceEntity] this card was generated from. */
    val sentenceId: Long,
    val text: String,
    val translation: String,
    val structure: List<SentenceToken>,
    /** Word ids this card was picked to teach. Shrinks as the quiz is answered correctly. */
    val mainWordIds: List<Long>,
    val shownTimes: Int = 0,
    val learned: Boolean = false,
    val queueLevel: QueueLevel = QueueLevel.HIGHEST,
    val lastMarkedLevel: QueueLevel? = null,
    val pendingQuiz: Boolean = false,
    val quizSucceeded: Boolean = false,
)
