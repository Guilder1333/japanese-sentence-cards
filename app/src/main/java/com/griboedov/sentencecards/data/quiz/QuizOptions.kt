package com.griboedov.sentencecards.data.quiz

import com.griboedov.sentencecards.data.db.WordEntity
import kotlin.random.Random

/**
 * Builds 2-4 multiple-choice reading options for [word]: its real furigana plus up to 3 distinct
 * distractor readings drawn at random from [pool] (typically every known word). Returns null if
 * [word] has no furigana to quiz - there's nothing to test for a kana-only entry.
 */
fun readingOptions(
    word: WordEntity,
    pool: Collection<WordEntity>,
    random: Random = Random.Default,
    maxOptions: Int = 4,
): List<String>? {
    val correct = word.furigana?.takeIf { it.isNotBlank() } ?: return null
    val distractorPool = pool.mapNotNull { it.furigana?.takeIf { f -> f.isNotBlank() && f != correct } }.distinct()
    val distractors = distractorPool.shuffled(random).take(maxOptions - 1)
    return (distractors + correct).shuffled(random)
}
