package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.SentenceDao
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.translation.NoOpTranslator
import com.griboedov.sentencecards.data.translation.Translator

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
 *
 * Most imported sentences have no translation yet (see `tools/import_book.py`). Rather than
 * machine-translating the entire pool up front, a sentence is only ever sent to [translator] right
 * here, at the point it's actually picked to back a card - whether that's a brand-new card
 * ([translateIfNeeded]) or an existing one being reused for another word
 * ([translateCardIfNeeded]) - and only if it doesn't already have one. The result is persisted back
 * onto the [SentenceEntity] too (and copied onto the [CardEntity]), so the pool itself fills in over
 * time and a sentence never gets re-translated once it has a translation.
 */
class CardGenerator(
    private val sentenceDao: SentenceDao,
    private val cardDao: CardDao,
    private val wordDao: WordDao,
    private val translator: Translator = NoOpTranslator,
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
                val translated = translateIfNeeded(sentence)
                CardEntity(
                    sentenceId = translated.id,
                    text = translated.text,
                    translation = translated.translation,
                    structure = translated.structure,
                    mainWordIds = listOf(wordId),
                )
            },
        )
    }

    /**
     * Fills in [sentence]'s translation via [translator] if it doesn't have one yet, persisting
     * the result back to the sentence pool. Left untouched (and no request made) if it's already
     * translated, or if [translator] can't produce one - e.g. no API key configured, or a
     * network/API failure - so card generation never fails just because translation did.
     */
    private suspend fun translateIfNeeded(sentence: SentenceEntity): SentenceEntity {
        if (sentence.translation.isNotBlank()) return sentence
        val translation = translator.translate(sentence.text)?.takeIf { it.isNotBlank() } ?: return sentence
        val updated = sentence.copy(translation = translation)
        sentenceDao.upsertAll(listOf(updated))
        return updated
    }

    /**
     * Adds [wordId] to every already-carded sentence's main words, reactivating any card that had
     * already finished. Also backfills the card's translation if it's still missing - e.g. it was
     * created before a DeepL key was configured, or the request failed at the time - since a
     * reused card is just as "used for a card" as a brand-new one, and it's otherwise never
     * revisited.
     */
    private suspend fun reuseExistingCards(existingCards: List<CardEntity>, wordId: Long) {
        val toUpdate = existingCards.mapNotNull { card ->
            val needsWord = wordId !in card.mainWordIds
            if (!needsWord && card.translation.isNotBlank()) return@mapNotNull null

            var updated = card
            if (needsWord) {
                val mainWordIds = card.mainWordIds + wordId
                updated = if (card.learned) {
                    // Already graduated and out of the review rotation - reactivate it so the
                    // newly added word actually gets reviewed/quizzed, instead of silently never
                    // surfacing.
                    updated.copy(
                        mainWordIds = mainWordIds,
                        learned = false,
                        quizSucceeded = false,
                        pendingQuiz = false,
                        queueLevel = QueueLevel.HIGHEST,
                        lastMarkedLevel = null,
                    )
                } else {
                    updated.copy(mainWordIds = mainWordIds)
                }
            }
            translateCardIfNeeded(updated)
        }
        if (toUpdate.isNotEmpty()) cardDao.upsertAll(toUpdate)
    }

    /**
     * [translateIfNeeded]'s per-[CardEntity] counterpart, for backfilling an already-existing card
     * being reused. Also called directly by [com.griboedov.sentencecards.ui.review.ReviewViewModel]
     * to retry a still-untranslated card each time it's shown in review - the most likely reason a
     * card ended up without one is the translation request failing when the card was first created
     * (e.g. no network at the time), and that's the next point it's ever revisited.
     */
    suspend fun translateCardIfNeeded(card: CardEntity): CardEntity {
        if (card.translation.isNotBlank()) return card
        val sentence = sentenceDao.getByIds(listOf(card.sentenceId)).firstOrNull() ?: return card
        val translated = translateIfNeeded(sentence)
        return if (translated.translation.isBlank()) card else card.copy(translation = translated.translation)
    }

    private companion object {
        const val DEFAULT_LIMIT = 3
    }
}
