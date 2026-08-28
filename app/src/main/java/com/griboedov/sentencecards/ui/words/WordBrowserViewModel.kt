package com.griboedov.sentencecards.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setToLearn(id: Long, toLearn: Boolean) {
        viewModelScope.launch { wordRepository.setToLearn(id, toLearn) }
    }

    fun setForceFurigana(id: Long, forced: Boolean) {
        viewModelScope.launch { wordRepository.setForceFurigana(id, forced) }
    }
}
