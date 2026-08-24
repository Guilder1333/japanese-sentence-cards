package com.griboedov.sentencecards.data.queue

import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceEntity

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
 * Learned doesn't finish the card - it queues the word quiz at medium priority; the card only
 * becomes [SentenceEntity.learned] once every main word has been answered correctly in the quiz
 * (see [gradeQuizWord]).
 */
fun SentenceEntity.applyReview(action: ReviewAction): SentenceEntity = when (action) {
    ReviewAction.LEARNED -> copy(
        pendingQuiz = true,
        quizRemainingWordIds = mainWordIds,
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
 * Applies one quiz answer for [wordId]. Correct answers drop the word from the remaining list;
 * once none remain the sentence is fully [SentenceEntity.learned]. Incorrect answers cycle the
 * word to the back of the list so the quiz keeps moving but comes back to it before finishing.
 */
fun SentenceEntity.gradeQuizWord(wordId: Long, correct: Boolean): SentenceEntity {
    val withoutWord = quizRemainingWordIds - wordId
    val remaining = if (correct) withoutWord else withoutWord + wordId
    return if (remaining.isEmpty()) {
        copy(learned = true, pendingQuiz = false, quizRemainingWordIds = emptyList())
    } else {
        copy(quizRemainingWordIds = remaining)
    }
}
