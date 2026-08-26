package com.griboedov.sentencecards.ui.importsentences

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.griboedov.sentencecards.data.importer.BookImporter
import com.griboedov.sentencecards.data.importer.ImportResult
import com.griboedov.sentencecards.data.importer.SentenceImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(
    private val importer: SentenceImporter,
    private val bookImporter: BookImporter,
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    /**
     * File-picker path: structured JSON files are expected to get into the hundreds of megabytes
     * (a whole book) - way too big to comfortably paste, or even to hold fully in memory as a
     * String. This streams straight from the picked file into [SentenceImporter.importStream],
     * which parses and writes to the DB in bounded-size batches instead - see its doc comment.
     */
    fun importFromFile(context: Context, uri: Uri) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { importer.importStream(it) }
                        ?: ImportResult.Failure("Could not open the selected file.")
                } catch (e: Throwable) {
                    // Broad on purpose: an OutOfMemoryError here (not an Exception, so a plain
                    // catch wouldn't see it) should still surface as a message, not crash the app -
                    // even though importStream is specifically designed to avoid hitting one.
                    ImportResult.Failure(e.message ?: "Could not read the selected file.")
                }
            }
            applyResult(result)
            _isImporting.value = false
        }
    }

    /**
     * Plain-text book import: picks a `.txt` file straight up (not already-structured JSON) and
     * runs the same sentence-splitting/tokenizing/tagging tools/import_book.py does, on-device -
     * see [BookImporter]. This can take a while for a whole book (tokenizing plus a dictionary
     * lookup per distinct word), so it shares the same busy/result state as the JSON path.
     */
    fun importBookFromFile(context: Context, uri: Uri) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            val result = try {
                bookImporter.importFile(context, uri)
            } catch (e: Throwable) {
                // Same broad-catch reasoning as importFromFile above.
                ImportResult.Failure(e.message ?: "Could not read the selected file.")
            }
            applyResult(result)
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
