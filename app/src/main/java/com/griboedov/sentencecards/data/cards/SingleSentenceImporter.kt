package com.griboedov.sentencecards.data.cards

import com.griboedov.sentencecards.data.db.CardDao
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.WordDao
import com.griboedov.sentencecards.data.importer.ImportSentence
import com.griboedov.sentencecards.data.importer.SentenceImporter
import com.griboedov.sentencecards.data.importer.SentenceTokenizer
import com.griboedov.sentencecards.data.importer.SingleSentenceCheck
import com.griboedov.sentencecards.data.importer.checkSingleSentence
import com.griboedov.sentencecards.data.translation.NoOpTranslator
import com.griboedov.sentencecards.data.translation.Translator

sealed interface SingleSentenceParseResult {
    data class Ok(val sentenceText: String, val structure: List<SentenceToken>, val translation: String) : SingleSentenceParseResult
    data class Error(val message: String) : SingleSentenceParseResult
}

/**
 * Backs the "import a single sentence" feature: type one sentence directly, pick which of its
 * words this card should actually teach (no automatic scoring/search - the user chooses), and it
 * becomes exactly one card. Unlike [com.griboedov.sentencecards.data.importer.BookImporter],
 * there's no bulk-throughput concern here, so this affords a couple of nicer behaviors:
 *  - [parse] checks the text really is one sentence (same rule
 *    [com.griboedov.sentencecards.data.importer.BookImporter] uses for a whole book) and reports
 *    it back as an error rather than silently mangling multiple sentences into one card.
 *  - word ids are resolved against the words table by exact surface match first (same convention
 *    [com.griboedov.sentencecards.data.repository.WordRepository.addFromDictionary] already uses),
 *    reusing an already-tracked word's id instead of creating a duplicate - worth the extra DB
 *    lookups for one sentence in a way it isn't for a whole book.
 */
class SingleSentenceImporter(
    private val wordDao: WordDao,
    private val cardDao: CardDao,
    private val tokenizer: SentenceTokenizer,
    private val sentenceImporter: SentenceImporter,
    private val translator: Translator = NoOpTranslator,
) {
    /**
     * Validates and tokenizes [text], and translates it right away so the review view (shown
     * immediately after this returns) can display it like a normal card back - nothing about the
     * sentence changes between here and [importAsCard], so there's no reason to translate twice.
     */
    suspend fun parse(text: String): SingleSentenceParseResult {
        val sentence = when (val check = checkSingleSentence(text)) {
            is SingleSentenceCheck.Error -> return SingleSentenceParseResult.Error(check.message)
            is SingleSentenceCheck.Ok -> check.sentence
        }

        var nextWordId = (wordDao.maxId() ?: 0L) + 1
        val wordIds = HashMap<String, Long>()
        val structure = tokenizer.tokenize(sentence) { surface, _ ->
            wordIds.getOrPut(surface) { wordDao.findByWord(surface)?.id ?: nextWordId++ }
        }

        val translation = translator.translate(sentence)?.takeIf { it.isNotBlank() } ?: ""
        return SingleSentenceParseResult.Ok(sentence, structure, translation)
    }

    /**
     * Writes the sentence to the pool exactly like any other import (new words default
     * not-learned, per the README - they just aren't this card's focus), then creates exactly one
     * card from it with [mainWordIds] as its main words. No scoring, no search, no other sentences
     * or cards touched.
     */
    suspend fun importAsCard(
        sentenceText: String,
        structure: List<SentenceToken>,
        translation: String,
        mainWordIds: Set<Long>,
    ) {
        val sentence = sentenceImporter.importOne(
            ImportSentence(text = sentenceText, translation = translation, structure = structure),
        )
        cardDao.upsertAll(
            listOf(
                CardEntity(
                    sentenceId = sentence.id,
                    text = sentence.text,
                    translation = sentence.translation,
                    structure = sentence.structure,
                    mainWordIds = mainWordIds.toList(),
                ),
            ),
        )
    }
}
