package com.griboedov.sentencecards.data.db

/**
 * The four review-priority queues described in the README, ordered from most to least urgent.
 * [ordinal] order matters: it is the order [com.griboedov.sentencecards.data.queue.QueueEngine]
 * checks queues in when picking the next card.
 */
enum class QueueLevel {
    HIGHEST,
    MEDIUM,
    EASY,
    BACKLOG;

    /** One step less urgent than this level, clamped at BACKLOG. */
    fun demoted(): QueueLevel = entries.getOrElse(ordinal + 1) { BACKLOG }
}
