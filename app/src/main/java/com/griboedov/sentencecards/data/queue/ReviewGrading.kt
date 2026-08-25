package com.griboedov.sentencecards.data.queue

import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel

/** The four buttons on the back of a review card. */
enum class ReviewAction(val targetLevel: QueueLevel?) {
    HARD(QueueLevel.HIGHEST),
    MEDIUM(QueueLevel.MEDIUM),
    EASY(QueueLevel.EASY),

    /** Not a queue level by itself: queues the post-"learned" word quiz, at medium priority. */
    LEARNED(null),
}

/**
 * Applies a review button press to a sentence.
 *
 * Hard/medium/easy move the card to that queue - unless the *same* button was pressed last time
 * too, in which case it is demoted one level further (README: "if sentence is marked as same
 * level two times in a row, it is decreased in priority to level below").
 *
 * Learned doesn't finish the card - it moves to the medium queue and queues one round of the
 * reading quiz (see [applyQuizResult]); the sentence isn't hidden from review until that quiz is
 * fully passed, possibly across several separate Learned presses.
 */
fun CardEntity.applyReview(action: ReviewAction): CardEntity = when (action) {
    ReviewAction.LEARNED -> copy(
        pendingQuiz = true,
        queueLevel = QueueLevel.MEDIUM,
        lastMarkedLevel = null,
    )
    else -> {
        val target = requireNotNull(action.targetLevel)
        val effective = if (lastMarkedLevel == target) target.demoted() else target
        copy(queueLevel = effective, lastMarkedLevel = target)
    }
}

/**
 * Applies one quiz attempt: [correctWordIds] is the subset of [CardEntity.mainWordIds] the
 * user answered correctly this round. The quiz only runs once per "Learned" press - either way,
 * [CardEntity.pendingQuiz] clears afterwards, so the card is never re-quizzed back-to-back.
 *
 * Correct words are dropped from the main word list. If any remain, the quiz partially failed -
 * the card is explicitly *not* [CardEntity.learned] (learning continues), and the card
 * returns to normal front/back review at the hard queue; marking it Learned again later re-quizzes
 * only the words still remaining. Once none remain, the card is fully [CardEntity.learned]
 * and [CardEntity.quizSucceeded].
 */
fun CardEntity.applyQuizResult(correctWordIds: Set<Long>): CardEntity {
    val remaining = mainWordIds.filterNot { it in correctWordIds }
    return if (remaining.isEmpty()) {
        copy(mainWordIds = remaining, learned = true, quizSucceeded = true, pendingQuiz = false)
    } else {
        copy(
            mainWordIds = remaining,
            queueLevel = QueueLevel.HIGHEST,
            pendingQuiz = false,
            learned = false,
            quizSucceeded = false,
        )
    }
}
