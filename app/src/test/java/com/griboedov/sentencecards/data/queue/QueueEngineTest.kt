package com.griboedov.sentencecards.data.queue

import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun sentence(id: Long, level: QueueLevel, learned: Boolean = false) = CardEntity(
    id = id,
    sentenceId = id,
    text = "text$id",
    translation = "translation$id",
    structure = emptyList(),
    mainWordIds = emptyList(),
    queueLevel = level,
    learned = learned,
)

class QueueEngineTest {

    @Test
    fun `highest priority is shown before medium`() {
        val engine = QueueEngine()
        val sentences = listOf(sentence(1, QueueLevel.MEDIUM), sentence(2, QueueLevel.HIGHEST))

        assertEquals(2L, engine.nextCard(sentences)?.id)
    }

    @Test
    fun `does not move to medium until highest is fully emptied`() {
        val engine = QueueEngine()
        var all = listOf(
            sentence(1, QueueLevel.HIGHEST),
            sentence(2, QueueLevel.HIGHEST),
            sentence(3, QueueLevel.MEDIUM),
        )

        // Grade both highest-priority cards down to easy, as if the user found them easy.
        val first = engine.nextCard(all)
        assertEquals(1L, first?.id)
        all = all.map { if (it.id == 1L) it.applyReview(ReviewAction.EASY) else it }
        engine.onCardGraded(1L)

        // One highest-priority card (id 2) is still left - medium must not be reachable yet.
        val second = engine.nextCard(all)
        assertEquals(2L, second?.id)
        all = all.map { if (it.id == 2L) it.applyReview(ReviewAction.EASY) else it }
        engine.onCardGraded(2L)

        // Highest is now fully empty - the medium card surfaces.
        val third = engine.nextCard(all)
        assertEquals(3L, third?.id)
    }

    @Test
    fun `mid-pass promotion is not shown until the next pass`() {
        val engine = QueueEngine()
        var all = listOf(sentence(1, QueueLevel.MEDIUM), sentence(2, QueueLevel.MEDIUM))

        val first = engine.nextCard(all)
        assertEquals(1L, first?.id)
        // Card 1 gets marked "hard" mid medium-pass: jumps to HIGHEST, but the medium pass
        // (already covering card 2) should continue uninterrupted per the README example.
        all = all.map { if (it.id == 1L) it.copy(queueLevel = QueueLevel.HIGHEST) else it }
        engine.onCardGraded(1L)

        val second = engine.nextCard(all)
        assertEquals(2L, second?.id)
        engine.onCardGraded(2L)

        // Only now, on the next pass, does the promoted card resurface.
        val third = engine.nextCard(all)
        assertEquals(1L, third?.id)
    }

    @Test
    fun `same button pressed twice in a row demotes one level further`() {
        var card = sentence(1, QueueLevel.HIGHEST)
        card = card.applyReview(ReviewAction.MEDIUM)
        assertEquals(QueueLevel.MEDIUM, card.queueLevel)

        card = card.applyReview(ReviewAction.MEDIUM)
        assertEquals(QueueLevel.EASY, card.queueLevel)
    }

    @Test
    fun `learned moves to medium priority and queues the quiz instead of finishing immediately`() {
        var card = sentence(1, QueueLevel.HIGHEST).copy(mainWordIds = listOf(10L, 20L))
        card = card.applyReview(ReviewAction.LEARNED)

        assertEquals(QueueLevel.MEDIUM, card.queueLevel)
        assertEquals(true, card.pendingQuiz)
        assertEquals(listOf(10L, 20L), card.mainWordIds)
        assertEquals(false, card.learned)
    }

    @Test
    fun `quiz words answered wrong send the card back to normal review at the hard queue`() {
        var card = sentence(1, QueueLevel.MEDIUM).copy(
            pendingQuiz = true,
            mainWordIds = listOf(10L, 20L),
        )

        // Only 20L answered correctly this round - 10L stays in the main word list.
        card = card.applyQuizResult(correctWordIds = setOf(20L))

        assertEquals(listOf(10L), card.mainWordIds)
        assertEquals(QueueLevel.HIGHEST, card.queueLevel)
        // The quiz only runs once per Learned press - it must NOT stay pending and re-trigger
        // itself; the card goes back to being a normal flip-and-grade flashcard.
        assertEquals(false, card.pendingQuiz)
        assertEquals(false, card.learned)
        assertEquals(false, card.quizSucceeded)
    }

    @Test
    fun `a partially-failed quiz round explicitly clears pendingQuiz, learned and quizSucceeded`() {
        // Simulates a stale pendingQuiz/learned/quizSucceeded state to prove applyQuizResult
        // actively clears it on partial failure, rather than merely happening to start false.
        var card = sentence(1, QueueLevel.MEDIUM).copy(
            pendingQuiz = true,
            mainWordIds = listOf(10L, 20L),
            learned = true,
            quizSucceeded = true,
        )

        card = card.applyQuizResult(correctWordIds = setOf(20L))

        assertEquals(false, card.pendingQuiz)
        assertEquals(false, card.learned)
        assertEquals(false, card.quizSucceeded)
    }

    @Test
    fun `marking Learned again after a partial failure re-quizzes only the words still remaining`() {
        var card = sentence(1, QueueLevel.HIGHEST).copy(mainWordIds = listOf(10L, 20L))

        card = card.applyReview(ReviewAction.LEARNED) // first quiz round queued
        card = card.applyQuizResult(correctWordIds = setOf(20L)) // 10L wrong, back to normal review
        assertEquals(listOf(10L), card.mainWordIds)
        assertEquals(false, card.pendingQuiz)

        card = card.applyReview(ReviewAction.LEARNED) // reviewed normally again, marked Learned again

        assertEquals(true, card.pendingQuiz)
        assertEquals(listOf(10L), card.mainWordIds)
    }

    @Test
    fun `sentence becomes learned and quiz-succeeded once every main word is answered correctly`() {
        var card = sentence(1, QueueLevel.HIGHEST).copy(
            pendingQuiz = true,
            mainWordIds = listOf(10L),
        )

        card = card.applyQuizResult(correctWordIds = setOf(10L))

        assertEquals(emptyList<Long>(), card.mainWordIds)
        assertEquals(true, card.learned)
        assertEquals(true, card.quizSucceeded)
        assertEquals(false, card.pendingQuiz)
    }

    @Test
    fun `empty queue returns null`() {
        val engine = QueueEngine()
        assertNull(engine.nextCard(emptyList()))
        assertNull(engine.nextCard(listOf(sentence(1, QueueLevel.HIGHEST, learned = true))))
    }
}
