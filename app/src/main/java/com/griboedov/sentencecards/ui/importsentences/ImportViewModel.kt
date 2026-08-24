package com.griboedov.sentencecards.ui.importsentences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.importer.ImportResult
import com.griboedov.sentencecards.data.importer.SentenceImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(private val importer: SentenceImporter) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    fun import(rawJson: String) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            _resultMessage.value = when (val result = importer.importJson(rawJson)) {
                is ImportResult.Success ->
                    "Imported ${result.sentences} sentence(s), added ${result.newWords} new word(s)."
                is ImportResult.Failure -> "Import failed: ${result.message}"
            }
            _isImporting.value = false
        }
    }

    fun clearMessage() {
        _resultMessage.value = null
    }
}
