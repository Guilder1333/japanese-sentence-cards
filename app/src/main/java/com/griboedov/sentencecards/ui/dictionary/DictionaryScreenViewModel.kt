package com.griboedov.sentencecards.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.importer.JapaneseTokenizer
import com.griboedov.sentencecards.data.repository.WordRepository
import com.griboedov.sentencecards.data.repository.WordStatusChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Dictionary tab: browses the full bundled JMdict-derived dictionary (as opposed to the Word
 * Browser, which only shows words already tracked in the internal database). Adding a result here
 * creates or updates a row in that internal database via [WordRepository.addFromDictionary].
 */
class DictionaryScreenViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val wordRepository: WordRepository,
    private val tokenizer: JapaneseTokenizer,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<DictionaryEntry>>(emptyList())
    val results: StateFlow<List<DictionaryEntry>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /** Clears the query and immediately empties the results list too, rather than leaving stale hits shown. */
    fun clearQuery() {
        _query.value = ""
        _results.value = emptyList()
        _hasSearched.value = false
    }

    /** Pastes clipboard [text] into the query and searches right away. */
    fun pasteAndSearch(text: String) {
        _query.value = text
        search()
    }

    /**
     * Splits [q] into its constituent words (by dictionary/base form, not however each is
     * inflected as typed - see [JapaneseTokenizer.splitIntoSearchTerms]) and searches all of them,
     * combining the results - so pasting a whole sentence finds every word it contains instead of
     * being matched as one literal (and almost always fruitless) string.
     */
    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _isSearching.value = true
            val terms = tokenizer.splitIntoSearchTerms(q)
            _results.value = dictionaryRepository.searchWords(terms)
            _isSearching.value = false
            _hasSearched.value = true
        }
    }

    fun addWord(entry: DictionaryEntry, status: WordStatusChoice) {
        viewModelScope.launch {
            val word = entry.kanji ?: entry.kana
            wordRepository.addFromDictionary(word, entry.id, status)
            _message.value = "Added $word as ${status.label}"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
