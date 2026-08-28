package com.griboedov.sentencecards.data.quiz

import kotlin.random.Random

/**
 * Builds 2-4 multiple-choice reading options: [correct] plus up to 3 distinct distractor readings
 * drawn at random from [pool] (typically every other tracked word's reading). Returns null if
 * [correct] is blank - there's nothing to test for a word with no known reading.
 *
 * Takes plain reading strings rather than [com.griboedov.sentencecards.data.db.WordEntity] - the
 * reading itself now lives in the bundled dictionary, not on the word entity (see
 * [com.griboedov.sentencecards.data.db.WordEntity.dictionaryEntryId]), so callers resolve readings
 * once (batched) and pass them in rather than this function reaching for the DB itself.
 */
fun readingOptions(
    correct: String,
    pool: Collection<String>,
    random: Random = Random.Default,
    maxOptions: Int = 4,
): List<String>? {
    if (correct.isBlank()) return null
    val distractorPool = pool.filter { it.isNotBlank() && it != correct }.distinct()
    val distractors = distractorPool.shuffled(random).take(maxOptions - 1)
    return (distractors + correct).shuffled(random)
}
