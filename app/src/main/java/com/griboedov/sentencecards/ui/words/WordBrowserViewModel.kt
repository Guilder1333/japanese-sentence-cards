package com.griboedov.sentencecards.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import com.griboedov.sentencecards.ui.common.PopupMessage
import com.griboedov.sentencecards.ui.common.cardsAddedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tracked word alongside its dictionary entry (reading/meaning), if one was resolved for it. */
data class WordRow(val word: WordEntity, val dictionaryEntry: DictionaryEntry?)

class WordBrowserViewModel(
    private val wordRepository: WordRepository,
    private val dictionaryRepository: DictionaryRepository,
) : ViewModel() {

    // Batched, not one dictionary lookup per row: reading/meaning no longer live on WordEntity
    // (see WordEntity.dictionaryEntryId), so every emission joins the whole tracked-word list
    // against the bundled dictionary in one query.
    val rows: StateFlow<List<WordRow>> = wordRepository.observeAll()
        .map { words ->
            val entries = dictionaryRepository.getByIds(words.mapNotNull { it.dictionaryEntryId })
            words.map { WordRow(it, it.dictionaryEntryId?.let(entries::get)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _popup = MutableStateFlow<PopupMessage?>(null)
    val popup: StateFlow<PopupMessage?> = _popup.asStateFlow()

    fun setToLearn(word: WordEntity, toLearn: Boolean) {
        viewModelScope.launch {
            val cardCount = wordRepository.setToLearn(word.id, toLearn) ?: return@launch
            _popup.value = cardsAddedMessage(word.word, cardCount)
        }
    }

    fun clearPopup() {
        _popup.value = null
    }

    fun setForceFurigana(id: Long, forced: Boolean) {
        viewModelScope.launch { wordRepository.setForceFurigana(id, forced) }
    }
}
