package com.griboedov.sentencecards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.cards.CardGenerator
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.queue.QueueEngine
import com.griboedov.sentencecards.data.queue.ReviewAction
import com.griboedov.sentencecards.data.queue.applyQuizResult
import com.griboedov.sentencecards.data.queue.applyReview
import com.griboedov.sentencecards.data.quiz.readingOptions
import com.griboedov.sentencecards.data.repository.CardRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import com.griboedov.sentencecards.data.repository.WordStatusChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WordDirection { RIGHT, LEFT, DOWN, UP }

/** State of the "Up" 4-direction menu action - dictionary lookup. */
sealed interface DictionaryLookup {
    data object Hidden : DictionaryLookup
    data class Loading(val word: String) : DictionaryLookup
    data class Loaded(val word: String, val entries: List<DictionaryEntry>) : DictionaryLookup
    data class Failed(val word: String, val message: String) : DictionaryLookup
}

data class ReviewUiState(
    val card: CardEntity? = null,
    /** Words referenced by the current card's structure, keyed by word id. */
    val words: Map<Long, WordEntity> = emptyMap(),
    /**
     * The tracked word each of the current card's structure tokens resolves to, keyed by that
     * token's index in [CardEntity.structure]. Indexed by position rather than by word id because
     * a kana word has no id in the structure at all - it is matched by text once promoted (see
     * [ReviewViewModel.trackedWord]) - so a token's own index is the only key that works for both
     * kinds. A token missing from this map is either grammar or a word not (yet) tracked.
     */
    val tokenWords: Map<Int, WordEntity> = emptyMap(),
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    /** wordId -> shuffled reading options, for every quizzable word in the current quiz card. */
    val quizOptions: Map<Long, List<String>> = emptyMap(),
    /** wordId -> that word's correct reading, for every word in [quizOptions] - see [readingOptions]. */
    val quizCorrectReadings: Map<Long, String> = emptyMap(),
    /** wordId -> the option picked so far this round. */
    val quizAnswers: Map<Long, String> = emptyMap(),
    val dictionaryLookup: DictionaryLookup = DictionaryLookup.Hidden,
) {
    val quizAllAnswered: Boolean
        get() = quizOptions.keys.isNotEmpty() && quizOptions.keys.all { it in quizAnswers }
}

/**
 * Drives the flash-card review screen: which card is showing, the priority queue that picks it
 * (see [QueueEngine]), the front/back flip, the word 4-direction menu (including the "Up"
 * dictionary lookup, via [DictionaryRepository]), and the "special kind of flash card" reading
 * quiz that follows marking a sentence Learned.
 *
 * Not reactive/pure end-to-end on purpose: [QueueEngine] carries its own pass state, so the
 * currently-pinned card ([currentCardId]) is tracked imperatively and only re-derived when it
 * stops being eligible (graded away, or becomes learned).
 */
class ReviewViewModel(
    private val cardRepository: CardRepository,
    private val wordRepository: WordRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val cardGenerator: CardGenerator,
) : ViewModel() {
    private val queueEngine = QueueEngine()

    private var allCards: List<CardEntity> = emptyList()
    private var allWords: Map<Long, WordEntity> = emptyMap()
    /**
     * [allWords] keyed by [WordEntity.word] instead of id, for resolving kana words - those are
     * never given an id in a sentence's structure, so the only thing linking such a token to its
     * words-table row (once promoted) is the dictionary form both are keyed on.
     */
    private var allWordsByText: Map<String, WordEntity> = emptyMap()
    /** wordId -> dictionary-form reading, resolved from [WordEntity.dictionaryEntryId] - see [resolveReadings]. */
    private var allWordReadings: Map<Long, String> = emptyMap()
    private var currentCardId: Long? = null
    private var lastCountedShownId: Long? = null

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(cardRepository.observeAll(), wordRepository.observeAll()) { cards, words ->
                cards to words
            }.collect { (cards, words) ->
                allCards = cards
                allWords = words.associateBy { it.id }
                allWordsByText = words.associateBy { it.word }
                allWordReadings = resolveReadings(words)
                refresh()
            }
        }
    }

    /**
     * Batched, not per-word: the reading quiz's distractor pool needs every tracked word's reading
     * in memory at once (see [buildQuizOptions]), so this resolves the whole list's
     * [WordEntity.dictionaryEntryId]s in one query rather than one dictionary lookup per word.
     */
    private suspend fun resolveReadings(words: List<WordEntity>): Map<Long, String> {
        val entryIds = words.mapNotNull { it.dictionaryEntryId }
        val entries = dictionaryRepository.getByIds(entryIds)
        return words.mapNotNull { word ->
            word.dictionaryEntryId?.let(entries::get)?.kana?.let { reading -> word.id to reading }
        }.toMap()
    }

    private fun refresh() {
        val previousCardId = currentCardId
        val stillEligible = currentCardId?.let { id -> allCards.find { it.id == id && !it.learned } }
        val card = stillEligible ?: queueEngine.nextCard(allCards)?.also { currentCardId = it.id }

        if (card == null) {
            currentCardId = null
            _uiState.value = ReviewUiState(isLoading = false, isEmpty = true)
            return
        }

        val tokenWords = card.structure.withIndex()
            .mapNotNull { (index, token) -> trackedWord(token)?.let { index to it } }
            .toMap()
        val cardWords = tokenWords.values.associateBy { it.id }
        val sameCard = card.id == previousCardId

        _uiState.update { prev ->
            ReviewUiState(
                card = card,
                words = cardWords,
                tokenWords = tokenWords,
                isFlipped = if (sameCard) prev.isFlipped else false,
                isLoading = false,
                isEmpty = false,
                quizOptions = when {
                    !card.pendingQuiz -> emptyMap()
                    sameCard -> prev.quizOptions
                    else -> buildQuizOptions(card)
                },
                quizCorrectReadings = when {
                    !card.pendingQuiz -> emptyMap()
                    sameCard -> prev.quizCorrectReadings
                    else -> card.mainWordIds.mapNotNull { id -> allWordReadings[id]?.let { id to it } }.toMap()
                },
                quizAnswers = if (sameCard) prev.quizAnswers else emptyMap(),
                // Rebuilt from scratch on every sentences/words change (e.g. any metric write,
                // including the one this very dialog's word-tap triggers) - without carrying this
                // forward, an open dictionary dialog would get silently reset to Hidden right
                // after opening.
                dictionaryLookup = if (sameCard) prev.dictionaryLookup else DictionaryLookup.Hidden,
            )
        }

        if (lastCountedShownId != card.id) {
            lastCountedShownId = card.id
            recordShown(card, tokenWords)
        }
    }

    /**
     * The words-table row [token] refers to, or null if there is not one.
     *
     * A kind=WORD token carries its row's id directly. A kana word never does - it is not tracked
     * at import time (see [TokenKind]) - so it is matched by [SentenceToken.baseText] instead,
     * which finds a row only once the user has promoted that word from the review screen
     * ([onWordDirection]). That is what makes a promoted kana word start showing its
     * to-learn/known colour on the card, exactly like a kanji word does.
     */
    private fun trackedWord(token: SentenceToken): WordEntity? = when {
        token.id != null -> allWords[token.id]
        token.tokenKind.isKanaWord -> allWordsByText[token.baseText]
        else -> null
    }

    /** One multiple-choice reading question per main word that actually has a known reading to quiz. */
    private fun buildQuizOptions(card: CardEntity): Map<Long, List<String>> {
        val pool = allWordReadings.values
        return card.mainWordIds.mapNotNull { id ->
            val correct = allWordReadings[id] ?: return@mapNotNull null
            val options = readingOptions(correct, pool) ?: return@mapNotNull null
            id to options
        }.toMap()
    }

    /**
     * Front-of-card display rule: furigana only for kind=WORD tokens with forceFurigana set, and
     * never for this sentence's main words - mirrors [FlashCardView]'s CardFront. A promoted kana
     * word can carry forceFurigana (the word browser can still set it), but there is no reading to
     * draw over kana, so it never counts as furigana actually shown.
     */
    private fun recordShown(card: CardEntity, tokenWords: Map<Int, WordEntity>) {
        viewModelScope.launch {
            cardRepository.update(card.copy(shownTimes = card.shownTimes + 1))
            val wordIds = tokenWords.values.mapTo(mutableSetOf()) { it.id }
            val furiganaShownIds = tokenWords.mapNotNull { (index, word) ->
                if (word.id in card.mainWordIds) return@mapNotNull null
                if (card.structure[index].tokenKind != TokenKind.WORD) return@mapNotNull null
                word.id.takeIf { word.forceFurigana }
            }
            wordRepository.recordShown(wordIds, furiganaShownIds)
        }
        retryTranslationIfNeeded(card)
    }

    /**
     * A card can end up without a translation if the DeepL request failed when it was first
     * created (e.g. no network at the time) - see [CardGenerator]'s doc comment. Rather than
     * leaving it blank forever, every time such a card is shown this retries the translation in
     * the background; [CardGenerator.translateCardIfNeeded] persists the result to both the
     * sentence pool and the card, and the latter flows back into [uiState] via [cardRepository]'s
     * observed [CardEntity] flow the same way any other card update does.
     */
    private fun retryTranslationIfNeeded(card: CardEntity) {
        if (card.translation.isNotBlank()) return
        viewModelScope.launch {
            val translated = cardGenerator.translateCardIfNeeded(card)
            if (translated.translation.isNotBlank()) cardRepository.update(translated)
        }
    }

    fun onFlip() {
        val card = _uiState.value.card ?: return
        if (card.pendingQuiz) return // quiz cards use the reading quiz instead of flipping
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun onGrade(action: ReviewAction) {
        val card = _uiState.value.card ?: return
        if (card.pendingQuiz) return
        viewModelScope.launch {
            val updated = card.applyReview(action)
            cardRepository.update(updated)
            queueEngine.onCardGraded(card.id)
            currentCardId = null
            allCards = allCards.map { if (it.id == card.id) updated else it }
            refresh()
        }
    }

    /**
     * Tap and the "Up" flick direction do the same thing - see [lookupDictionary]. Takes the
     * tapped [SentenceToken] rather than a word id because a kana word does not have one: every
     * word action here works off the token and resolves the words-table row itself, if any (see
     * [trackedWord]).
     */
    fun onWordTapped(token: SentenceToken) {
        trackedWord(token)?.let { word -> viewModelScope.launch { wordRepository.recordTranslationShown(word.id) } }
        lookupDictionary(token)
    }

    /**
     * Commits one 4-direction menu flick. Kana words get the same four directions a kanji word
     * does, with one exception: "force furigana" is meaningless for a word already written in kana
     * and is refused here, matching [FlickMenu] showing that direction as unavailable for them.
     *
     * Marking a not-yet-tracked kana word known or to-learn *promotes* it: per the README kana
     * words are assumed known and never imported into the words table, so the first time the user
     * says otherwise about one is when its row gets created (see
     * [WordRepository.addFromDictionary]).
     */
    fun onWordDirection(token: SentenceToken, direction: WordDirection) {
        if (!token.tokenKind.isWord) return
        when (direction) {
            WordDirection.RIGHT -> setStatus(token, WordStatusChoice.KNOWN)
            WordDirection.LEFT -> setStatus(token, WordStatusChoice.TO_LEARN)
            WordDirection.DOWN -> {
                if (token.tokenKind.isKanaWord) return
                val word = trackedWord(token) ?: return
                viewModelScope.launch { wordRepository.forceFurigana(word.id) }
            }
            WordDirection.UP -> lookupDictionary(token)
        }
    }

    private fun setStatus(token: SentenceToken, status: WordStatusChoice) {
        val tracked = trackedWord(token)
        viewModelScope.launch {
            when {
                tracked == null -> {
                    // An untracked kana word: resolve its dictionary entry (best effort - a word
                    // JMdict does not have is still worth tracking) and create the row.
                    val entryId = lookupEntries(token, token.baseText).firstOrNull()?.id
                    wordRepository.addFromDictionary(token.baseText, entryId, status)
                }
                status == WordStatusChoice.KNOWN -> wordRepository.markKnown(tracked.id)
                else -> wordRepository.markToLearn(tracked.id)
            }
        }
    }

    private fun lookupDictionary(token: SentenceToken) {
        if (!token.tokenKind.isWord) return
        val tracked = trackedWord(token)
        val text = tracked?.word ?: token.baseText
        _uiState.update { it.copy(dictionaryLookup = DictionaryLookup.Loading(text)) }
        viewModelScope.launch {
            val result = try {
                // Prefer the exact entry already resolved for this word (see
                // WordEntity.dictionaryEntryId) over a fresh search, which can land on a different
                // homograph than the one actually tracked; only fall back to a fresh search when
                // nothing was resolved at tracking time - always the case for a kana word that has
                // not been promoted yet.
                val entries = tracked?.dictionaryEntryId?.let { id -> dictionaryRepository.getById(id)?.let(::listOf) }
                    ?: lookupEntries(token, text)
                DictionaryLookup.Loaded(text, entries)
            } catch (e: Exception) {
                DictionaryLookup.Failed(text, e.message ?: e::class.simpleName ?: "Lookup failed")
            }
            _uiState.update { it.copy(dictionaryLookup = result) }
        }
    }

    /**
     * Searches the bundled dictionary for [text] on the index matching the token's script: a kana
     * word only ever appears in JMdict's reading index, a kanji word in its kanji index, and
     * searching the wrong one just comes back empty.
     */
    private suspend fun lookupEntries(token: SentenceToken, text: String) =
        if (token.tokenKind.isKanaWord) dictionaryRepository.lookup(kanji = null, kana = text)
        else dictionaryRepository.lookup(kanji = text, kana = null)

    fun dismissDictionary() {
        _uiState.update { it.copy(dictionaryLookup = DictionaryLookup.Hidden) }
    }

    /** Records (but doesn't yet grade) the chosen reading for one quiz word - locked in once picked. */
    fun onQuizOptionSelected(wordId: Long, option: String) {
        _uiState.update { state ->
            if (state.quizAnswers.containsKey(wordId)) state
            else state.copy(quizAnswers = state.quizAnswers + (wordId to option))
        }
    }

    /**
     * Grades every answered word once the whole round is complete: correct words drop out of the
     * card's main word list, incorrect ones send the card to the hard queue for another attempt,
     * and an empty main word list marks the card learned (see [applyQuizResult]).
     */
    fun onQuizContinue() {
        val state = _uiState.value
        val card = state.card ?: return
        if (!card.pendingQuiz || !state.quizAllAnswered) return

        viewModelScope.launch {
            val correctIds = mutableSetOf<Long>()
            for (wordId in card.mainWordIds) {
                val options = state.quizOptions[wordId]
                if (options == null) {
                    correctIds += wordId // no furigana to quiz - nothing to test, passes through
                    continue
                }
                val correctReading = state.quizCorrectReadings[wordId]
                val correct = correctReading != null && state.quizAnswers[wordId] == correctReading
                wordRepository.recordQuizResult(wordId, correct)
                if (correct) correctIds += wordId
            }
            val updated = card.applyQuizResult(correctIds)
            cardRepository.update(updated)
            queueEngine.onCardGraded(card.id)
            currentCardId = null
            allCards = allCards.map { if (it.id == card.id) updated else it }
            refresh()
        }
    }
}
