package com.griboedov.sentencecards.data.queue

import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun sentence(id: Long, level: QueueLevel, learned: Boolean = false) = SentenceEntity(
    id = id,
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
    fun `learned queues the quiz at medium priority instead of finishing immediately`() {
        var card = sentence(1, QueueLevel.HIGHEST).copy(mainWordIds = listOf(10L, 20L))
        card = card.applyReview(ReviewAction.LEARNED)

        assertEquals(QueueLevel.MEDIUM, card.queueLevel)
        assertEquals(true, card.pendingQuiz)
        assertEquals(listOf(10L, 20L), card.quizRemainingWordIds)
        assertEquals(false, card.learned)
    }

    @Test
    fun `sentence becomes learned once every quiz word is answered correctly`() {
        var card = sentence(1, QueueLevel.MEDIUM).copy(
            pendingQuiz = true,
            quizRemainingWordIds = listOf(10L, 20L),
        )

        card = card.gradeQuizWord(10L, correct = false)
        assertEquals(listOf(20L, 10L), card.quizRemainingWordIds) // wrong answer cycles to the back
        assertEquals(false, card.learned)

        card = card.gradeQuizWord(20L, correct = true)
        card = card.gradeQuizWord(10L, correct = true)

        assertEquals(true, card.learned)
        assertEquals(false, card.pendingQuiz)
    }

    @Test
    fun `empty queue returns null`() {
        val engine = QueueEngine()
        assertNull(engine.nextCard(emptyList()))
        assertNull(engine.nextCard(listOf(sentence(1, QueueLevel.HIGHEST, learned = true))))
    }
}
