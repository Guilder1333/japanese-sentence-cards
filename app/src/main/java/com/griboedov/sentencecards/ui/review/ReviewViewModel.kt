package com.griboedov.sentencecards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.db.CardEntity
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
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    /** wordId -> shuffled reading options, for every quizzable word in the current quiz card. */
    val quizOptions: Map<Long, List<String>> = emptyMap(),
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
) : ViewModel() {
    private val queueEngine = QueueEngine()

    private var allCards: List<CardEntity> = emptyList()
    private var allWords: Map<Long, WordEntity> = emptyMap()
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
                refresh()
            }
        }
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

        val cardWords = card.structure.mapNotNull { token -> token.id?.let { allWords[it] } }.associateBy { it.id }
        val sameCard = card.id == previousCardId

        _uiState.update { prev ->
            ReviewUiState(
                card = card,
                words = cardWords,
                isFlipped = if (sameCard) prev.isFlipped else false,
                isLoading = false,
                isEmpty = false,
                quizOptions = when {
                    !card.pendingQuiz -> emptyMap()
                    sameCard -> prev.quizOptions
                    else -> buildQuizOptions(card)
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
            recordShown(card, cardWords)
        }
    }

    /** One multiple-choice reading question per main word that actually has a furigana to quiz. */
    private fun buildQuizOptions(card: CardEntity): Map<Long, List<String>> {
        val pool = allWords.values
        return card.mainWordIds.mapNotNull { id ->
            val word = allWords[id] ?: return@mapNotNull null
            val options = readingOptions(word, pool) ?: return@mapNotNull null
            id to options
        }.toMap()
    }

    /**
     * Front-of-card display rule: furigana only for words with forceFurigana set (and not
     * hidden), and never for this sentence's main words - mirrors [FlashCardView]'s CardFront.
     */
    private fun recordShown(card: CardEntity, cardWords: Map<Long, WordEntity>) {
        viewModelScope.launch {
            cardRepository.update(card.copy(shownTimes = card.shownTimes + 1))
            val wordIds = card.structure.mapNotNull { it.id }
            val furiganaShownIds = card.structure.mapNotNull { token ->
                val id = token.id ?: return@mapNotNull null
                if (id in card.mainWordIds) return@mapNotNull null
                id.takeIf { cardWords[id]?.let { it.forceFurigana && !it.hideFurigana } == true }
            }
            wordRepository.recordShown(wordIds, furiganaShownIds)
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

    /** Tap and the "Up" flick direction do the same thing - see [lookupDictionary]. */
    fun onWordTapped(wordId: Long) {
        viewModelScope.launch { wordRepository.recordTranslationShown(wordId) }
        lookupDictionary(wordId)
    }

    fun onWordDirection(wordId: Long, direction: WordDirection) {
        when (direction) {
            WordDirection.RIGHT -> viewModelScope.launch { wordRepository.markKnown(wordId) }
            WordDirection.LEFT -> viewModelScope.launch { wordRepository.markToLearn(wordId) }
            WordDirection.DOWN -> viewModelScope.launch { wordRepository.hideFurigana(wordId) }
            WordDirection.UP -> lookupDictionary(wordId)
        }
    }

    private fun lookupDictionary(wordId: Long) {
        val word = allWords[wordId] ?: return
        _uiState.update { it.copy(dictionaryLookup = DictionaryLookup.Loading(word.word)) }
        viewModelScope.launch {
            val result = try {
                DictionaryLookup.Loaded(word.word, dictionaryRepository.lookup(kanji = word.word, kana = word.furigana))
            } catch (e: Exception) {
                DictionaryLookup.Failed(word.word, e.message ?: e::class.simpleName ?: "Lookup failed")
            }
            _uiState.update { it.copy(dictionaryLookup = result) }
        }
    }

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
                val word = allWords[wordId]
                val correct = word != null && state.quizAnswers[wordId] == word.furigana
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
