package com.griboedov.sentencecards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.queue.QueueEngine
import com.griboedov.sentencecards.data.queue.ReviewAction
import com.griboedov.sentencecards.data.queue.applyReview
import com.griboedov.sentencecards.data.queue.gradeQuizWord
import com.griboedov.sentencecards.data.repository.SentenceRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WordDirection { RIGHT, LEFT, DOWN, UP }

data class ReviewUiState(
    val card: SentenceEntity? = null,
    /** Words referenced by the current card's structure, keyed by word id. */
    val words: Map<Long, WordEntity> = emptyMap(),
    val isFlipped: Boolean = false,
    val quizRevealed: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
) {
    val currentQuizWordId: Long? get() = card?.quizRemainingWordIds?.firstOrNull()
}

/**
 * Drives the flash-card review screen: which card is showing, the priority queue that picks it
 * (see [QueueEngine]), the front/back flip, the word 4-direction menu, and the "special kind of
 * flash card" quiz that follows marking a sentence Learned.
 *
 * Not reactive/pure end-to-end on purpose: [QueueEngine] carries its own pass state, so the
 * currently-pinned card ([currentCardId]) is tracked imperatively and only re-derived when it
 * stops being eligible (graded away, or becomes learned).
 */
class ReviewViewModel(
    private val sentenceRepository: SentenceRepository,
    private val wordRepository: WordRepository,
) : ViewModel() {
    private val queueEngine = QueueEngine()

    private var allSentences: List<SentenceEntity> = emptyList()
    private var allWords: Map<Long, WordEntity> = emptyMap()
    private var currentCardId: Long? = null
    private var lastCountedShownId: Long? = null

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(sentenceRepository.observeAll(), wordRepository.observeAll()) { sentences, words ->
                sentences to words
            }.collect { (sentences, words) ->
                allSentences = sentences
                allWords = words.associateBy { it.id }
                refresh()
            }
        }
    }

    private fun refresh() {
        val stillEligible = currentCardId?.let { id -> allSentences.find { it.id == id && !it.learned } }
        val card = stillEligible ?: queueEngine.nextCard(allSentences)?.also { currentCardId = it.id }

        if (card == null) {
            currentCardId = null
            _uiState.value = ReviewUiState(isLoading = false, isEmpty = true)
            return
        }

        val cardWords = card.structure.mapNotNull { token -> token.id?.let { allWords[it] } }.associateBy { it.id }
        _uiState.update { prev ->
            val sameCard = prev.card?.id == card.id
            ReviewUiState(
                card = card,
                words = cardWords,
                isFlipped = if (sameCard) prev.isFlipped else false,
                quizRevealed = if (sameCard) prev.quizRevealed else false,
                isLoading = false,
                isEmpty = false,
            )
        }

        if (lastCountedShownId != card.id) {
            lastCountedShownId = card.id
            recordShown(card, cardWords)
        }
    }

    /** Front-of-card display rule: furigana only for words with forceFurigana set (and not hidden). */
    private fun recordShown(card: SentenceEntity, cardWords: Map<Long, WordEntity>) {
        viewModelScope.launch {
            sentenceRepository.update(card.copy(shownTimes = card.shownTimes + 1))
            val wordIds = card.structure.mapNotNull { it.id }
            val furiganaShownIds = card.structure.mapNotNull { token ->
                token.id?.takeIf { id -> cardWords[id]?.let { it.forceFurigana && !it.hideFurigana } == true }
            }
            wordRepository.recordShown(wordIds, furiganaShownIds)
        }
    }

    fun onFlip() {
        val card = _uiState.value.card ?: return
        if (card.pendingQuiz) return // quiz cards use reveal/grade instead of flipping
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun onGrade(action: ReviewAction) {
        val card = _uiState.value.card ?: return
        if (card.pendingQuiz) return
        viewModelScope.launch {
            val updated = card.applyReview(action)
            sentenceRepository.update(updated)
            queueEngine.onCardGraded(card.id)
            currentCardId = null
            allSentences = allSentences.map { if (it.id == card.id) updated else it }
            refresh()
        }
    }

    fun onWordTapped(wordId: Long) {
        viewModelScope.launch { wordRepository.recordTranslationShown(wordId) }
    }

    fun onWordDirection(wordId: Long, direction: WordDirection) {
        viewModelScope.launch {
            when (direction) {
                WordDirection.RIGHT -> wordRepository.markKnown(wordId)
                WordDirection.LEFT -> wordRepository.markToLearn(wordId)
                WordDirection.DOWN -> wordRepository.hideFurigana(wordId)
                WordDirection.UP -> Unit // dictionary lookup: TODO per README
            }
        }
    }

    fun onQuizReveal() {
        _uiState.update { it.copy(quizRevealed = true) }
    }

    fun onQuizAnswer(correct: Boolean) {
        val card = _uiState.value.card ?: return
        val wordId = card.quizRemainingWordIds.firstOrNull() ?: return
        viewModelScope.launch {
            wordRepository.recordQuizResult(wordId, correct)
            val updated = card.gradeQuizWord(wordId, correct)
            sentenceRepository.update(updated)
            if (updated.learned) {
                queueEngine.onCardGraded(card.id)
                currentCardId = null
            }
            allSentences = allSentences.map { if (it.id == card.id) updated else it }
            _uiState.update { it.copy(quizRevealed = false) }
            refresh()
        }
    }
}
