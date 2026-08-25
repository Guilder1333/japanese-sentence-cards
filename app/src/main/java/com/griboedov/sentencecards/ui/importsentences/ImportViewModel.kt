package com.griboedov.sentencecards.ui.importsentences

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.importer.ImportResult
import com.griboedov.sentencecards.data.importer.SentenceImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(private val importer: SentenceImporter) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    /** Pasted-JSON path, used by the sample loader and the (small-scale) text box. */
    fun import(rawJson: String) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            applyResult(importer.importJson(rawJson))
            _isImporting.value = false
        }
    }

    /**
     * File-picker path: structured JSON files are expected to get into the megabytes, too big to
     * comfortably paste - this reads the file directly instead of routing it through the (small,
     * editable) text box, which would otherwise have to hold the whole thing in UI state.
     */
    fun importFromFile(context: Context, uri: Uri) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
            }
            if (content == null) {
                _resultMessage.value = "Could not read the selected file."
            } else {
                applyResult(importer.importJson(content))
            }
            _isImporting.value = false
        }
    }

    private fun applyResult(result: ImportResult) {
        _resultMessage.value = when (result) {
            is ImportResult.Success ->
                "Imported ${result.sentences} sentence(s), added ${result.newWords} new word(s)."
            is ImportResult.Failure -> "Import failed: ${result.message}"
        }
    }

    fun clearMessage() {
        _resultMessage.value = null
    }
}
