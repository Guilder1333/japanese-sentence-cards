package com.griboedov.sentencecards.data.knowledge

import com.griboedov.sentencecards.data.db.WordEntity

/**
 * The README explicitly leaves "come up with a proper metric for knowledge level" as a TODO.
 * This is a placeholder formula so the UI has something to show - swap it out once a real one is
 * defined; nothing else in the app depends on the exact scoring, only on this enum.
 */
enum class KnowledgeLevel(val label: String) {
    NEW("New"),
    LEARNING("Learning"),
    FAMILIAR("Familiar"),
    STRONG("Strong"),
    KNOWN("Known"),
}

/** PLACEHOLDER scoring - see [KnowledgeLevel] doc. */
fun WordEntity.knowledgeLevel(): KnowledgeLevel {
    if (hideFurigana) return KnowledgeLevel.KNOWN
    val score = quizSuccess * 3 + timesShown - quizFails * 2 - timesFuriganaShown
    return when {
        score >= 12 -> KnowledgeLevel.STRONG
        score >= 6 -> KnowledgeLevel.FAMILIAR
        score >= 2 -> KnowledgeLevel.LEARNING
        else -> KnowledgeLevel.NEW
    }
}
