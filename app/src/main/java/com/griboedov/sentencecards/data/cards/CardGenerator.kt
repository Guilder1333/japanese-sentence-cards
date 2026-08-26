package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.WordDao

/**
 * Whenever a word is marked to-learn, searches the (potentially enormous) sentence pool for the
 * best-fitting sentences and turns them into cards - the README's "Adding word to learn" feature.
 * See [scoreSentenceForWord] for the scoring rule and [pickBestSentences] for the selection.
 *
 * The user should end up with (at most) [DEFAULT_LIMIT] cards teaching the word, total - not
 * [DEFAULT_LIMIT] *new* ones regardless of what's already there. So first, any sentence containing
 * the word that already backs a card (for some other word, or even this one already) is reused:
 * the word is just added to that card's main words instead of spawning a duplicate card for the
 * same sentence. Only the shortfall, if any, is filled with brand-new cards from the remaining
 * (not yet carded) candidate sentences.
 */
class CardGenerator(
    private val sentenceDao: SentenceDao,
    private val cardDao: CardDao,
    private val wordDao: WordDao,
) {
    suspend fun generateCardsForWord(wordId: Long, limit: Int = DEFAULT_LIMIT) {
        val containingIds = sentenceDao.sentenceIdsContaining(wordId)
        if (containingIds.isEmpty()) return

        val existingCards = cardDao.cardsForSentences(containingIds)
        reuseExistingCards(existingCards, wordId)

        // Only make up the shortfall with brand-new cards, so reused cards count towards the cap.
        val remaining = limit - existingCards.size
        if (remaining <= 0) return

        val carriedSentenceIds = existingCards.mapTo(mutableSetOf()) { it.sentenceId }
        val eligibleIds = containingIds.filterNot { it in carriedSentenceIds }
        if (eligibleIds.isEmpty()) return

        val candidates = sentenceDao.getByIds(eligibleIds)
        val neededWordIds = candidates.flatMapTo(mutableSetOf()) { it.structure.mapNotNull { token -> token.id } }
        val wordsById = wordDao.getByIds(neededWordIds.toList()).associateBy { it.id }

        val chosen = pickBestSentences(candidates, wordsById, remaining)
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

    /** Adds [wordId] to every already-carded sentence's main words, reactivating any card that had already finished. */
    private suspend fun reuseExistingCards(existingCards: List<CardEntity>, wordId: Long) {
        val toUpdate = existingCards.filter { wordId !in it.mainWordIds }.map { card ->
            val mainWordIds = card.mainWordIds + wordId
            if (card.learned) {
                // Already graduated and out of the review rotation - reactivate it so the newly
                // added word actually gets reviewed/quizzed, instead of silently never surfacing.
                card.copy(
                    mainWordIds = mainWordIds,
                    learned = false,
                    quizSucceeded = false,
                    pendingQuiz = false,
                    queueLevel = QueueLevel.HIGHEST,
                    lastMarkedLevel = null,
                )
            } else {
                card.copy(mainWordIds = mainWordIds)
            }
        }
        if (toUpdate.isNotEmpty()) cardDao.upsertAll(toUpdate)
    }

    private companion object {
        const val DEFAULT_LIMIT = 3
    }
}
