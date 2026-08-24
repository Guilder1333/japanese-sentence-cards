package com.griboedov.sentencecards.data.queue

import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceEntity

/**
 * Implements the README's priority-queue review order:
 *  - Highest priority is shown first; medium only once highest is *completely* empty; easy only
 *    once medium is empty too; backlog last.
 *  - Once a pass through a queue has started (because higher queues were empty), it keeps going
 *    through the cards that were in that queue when the pass started until they've all been
 *    reviewed once - even if higher-priority cards appear again in the meantime (e.g. one of this
 *    queue's own cards gets marked "hard" and jumps back to highest: it will be picked up on the
 *    *next* highest-priority pass, not mid-pass).
 *
 * This is plain, pure, non-Compose logic on purpose so it is easy to unit test - see
 * app/src/test/.../QueueEngineTest.kt.
 */
class QueueEngine {
    private var activePassIds: ArrayDeque<Long> = ArrayDeque()

    /** Call after the queue's source data changes in a way unrelated to grading (e.g. import). */
    fun reset() {
        activePassIds = ArrayDeque()
    }

    /** Call once the currently-shown card has been graded/answered and should leave this pass. */
    fun onCardGraded(id: Long) {
        activePassIds.remove(id)
    }

    /** Picks the next card to show, starting a new pass over the highest non-empty queue if needed. */
    fun nextCard(all: List<SentenceEntity>): SentenceEntity? {
        val eligible = all.filter { !it.learned }
        if (eligible.isEmpty()) {
            reset()
            return null
        }
        val byId = eligible.associateBy { it.id }

        // Drop ids from the active pass that no longer exist or became learned meanwhile.
        while (activePassIds.isNotEmpty() && byId[activePassIds.first()] == null) {
            activePassIds.removeFirst()
        }

        if (activePassIds.isEmpty()) {
            val level = QueueLevel.entries.firstOrNull { level -> eligible.any { it.queueLevel == level } }
                ?: return null
            activePassIds = ArrayDeque(
                eligible.filter { it.queueLevel == level }.sortedBy { it.id }.map { it.id },
            )
        }

        return byId[activePassIds.first()]
    }
}
