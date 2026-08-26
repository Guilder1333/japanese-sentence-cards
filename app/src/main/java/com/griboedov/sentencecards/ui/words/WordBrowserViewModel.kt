package com.griboedov.sentencecards.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.repository.WordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordBrowserViewModel(private val wordRepository: WordRepository) : ViewModel() {

    val words: StateFlow<List<WordEntity>> = wordRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setToLearn(id: Long, toLearn: Boolean) {
        viewModelScope.launch { wordRepository.setToLearn(id, toLearn) }
    }

    fun setForceFurigana(id: Long, forced: Boolean) {
        viewModelScope.launch { wordRepository.setForceFurigana(id, forced) }
    }
}
