package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.WordDao

/**
 * Whenever a word is marked to-learn, searches the (potentially enormous) sentence pool for the
 * best-fitting sentences and turns them into cards - the README's "Adding word to learn" feature.
 * See [scoreSentenceForWord] for the scoring rule and [pickBestSentences] for the selection.
 *
 * Candidate sentences are narrowed to ones containing [wordId] via the [SentenceDao.sentenceIdsContaining]
 * index, then to ones that don't already back a card ([CardDao.cardedSentenceIds]) - a sentence
 * never backs two cards, so re-triggering this for the same word (e.g. toggling to-learn off and
 * on again) naturally surfaces the *next*-best sentences instead of duplicating the first batch.
 */
class CardGenerator(
    private val sentenceDao: SentenceDao,
    private val cardDao: CardDao,
    private val wordDao: WordDao,
) {
    suspend fun generateCardsForWord(wordId: Long, limit: Int = 3) {
        val containingIds = sentenceDao.sentenceIdsContaining(wordId)
        if (containingIds.isEmpty()) return

        val alreadyCarded = cardDao.cardedSentenceIds().toSet()
        val eligibleIds = containingIds.filterNot { it in alreadyCarded }
        if (eligibleIds.isEmpty()) return

        val candidates = sentenceDao.getByIds(eligibleIds)
        val neededWordIds = candidates.flatMapTo(mutableSetOf()) { it.structure.mapNotNull { token -> token.id } }
        val wordsById = wordDao.getByIds(neededWordIds.toList()).associateBy { it.id }

        val chosen = pickBestSentences(candidates, wordsById, limit)
        if (chosen.isEmpty()) return

        cardDao.upsertAll(
            chosen.map { sentence ->
                CardEntity(
                    sentenceId = sentence.id,
                    text = sentence.text,
                    translation = sentence.translation,
                    structure = sentence.structure,
                    mainWordIds = listOf(wordId),
                )
            },
        )
    }
}
