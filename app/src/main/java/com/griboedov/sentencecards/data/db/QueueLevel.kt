package com.griboedov.sentencecards.data.db

import kotlinx.serialization.Serializable

/**
 * The four review-priority queues described in the README, ordered from most to least urgent.
 * [ordinal] order matters: it is the order [com.griboedov.sentencecards.data.queue.QueueEngine]
 * checks queues in when picking the next card.
 *
 * `@Serializable` since it's part of [CardEntity], which gets written into a Drive backup - see
 * [com.griboedov.sentencecards.data.backup.BackupSnapshot].
 */
@Serializable
enum class QueueLevel {
    HIGHEST,
    MEDIUM,
    EASY,
    BACKLOG;

    /** One step less urgent than this level, clamped at BACKLOG. */
    fun demoted(): QueueLevel = entries.getOrElse(ordinal + 1) { BACKLOG }
}
