package com.griboedov.sentencecards.ui.importsentences

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp

/**
 * Bulk import of sentences, two ways in:
 *  - an already-structured JSON file (README schema) - fast, no on-device parsing needed.
 *  - a plain-text book file - split into sentences, tokenized, and tagged on-device (see
 *    [com.griboedov.sentencecards.data.importer.BookImporter]); the in-app equivalent of running
 *    `tools/import_book.py` offline, for when that isn't an option.
 */
@Composable
fun ImportSentencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as SentenceCardsApp
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportViewModel(app.importer, app.bookImporter) }
        },
    )
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val resultMessage by viewModel.resultMessage.collectAsStateWithLifecycle()

    val jsonFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromFile(context, uri)
    }
    val bookFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBookFromFile(context, uri)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Import a JSON file of already-structured sentences.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { jsonFilePicker.launch(arrayOf("*/*")) },
            enabled = !isImporting,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Import from file...")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = "Or import a plain-text book file directly - it's split into sentences and " +
                "tagged on-device. This can take a while for a whole book.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedButton(
            onClick = { bookFilePicker.launch(arrayOf("text/plain", "*/*")) },
            enabled = !isImporting,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Import book (plain text)...")
            }
        }

        resultMessage?.let { message ->
            Snackbar(
                modifier = Modifier.padding(top = 16.dp),
                action = { OutlinedButton(onClick = viewModel::clearMessage) { Text("Dismiss") } },
            ) {
                Text(message)
            }
        }
    }
}
