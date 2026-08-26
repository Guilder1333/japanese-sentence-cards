package com.griboedov.sentencecards.ui.importsentences

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp

/**
 * Bulk import of sentences, three ways in:
 *  - an already-structured JSON file (README schema) - fast, no on-device parsing needed.
 *  - a plain-text book file - split into sentences, tokenized, and tagged on-device (see
 *    [com.griboedov.sentencecards.data.importer.BookImporter]); the in-app equivalent of running
 *    `tools/import_book.py` offline, for when that isn't an option.
 *  - a single sentence typed directly - parsed the same way, but instead of an automatic
 *    scoring/search pass, the user picks exactly which of its words this one card should teach
 *    (see [SingleSentenceCardView]) before it's written.
 */
@Composable
fun ImportSentencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as SentenceCardsApp
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportViewModel(app.importer, app.bookImporter, app.singleSentenceImporter) }
        },
    )
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val resultMessage by viewModel.resultMessage.collectAsStateWithLifecycle()
    val singleSentenceInput by viewModel.singleSentenceInput.collectAsStateWithLifecycle()
    val singleSentenceError by viewModel.singleSentenceError.collectAsStateWithLifecycle()
    val singleSentenceReview by viewModel.singleSentenceReview.collectAsStateWithLifecycle()

    val jsonFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromFile(context, uri)
    }
    val bookFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBookFromFile(context, uri)
    }

    val review = singleSentenceReview
    if (review != null) {
        // A completely separate view/flow from the three import sections below, while a sentence
        // is parsed and awaiting the user's main-word picks - see SingleSentenceCardView's doc
        // comment for why this isn't just a variant of the normal review FlashCardView.
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleSentenceCardView(
                sentenceText = review.sentenceText,
                structure = review.structure,
                translation = review.translation,
                selectedWordIds = review.selectedWordIds,
                onToggleWord = viewModel::toggleSingleSentenceWord,
                modifier = Modifier.weight(1f),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::cancelSingleSentenceImport,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::confirmSingleSentenceImport,
                    // Enabled only once at least one word is picked as this card's main word - a
                    // card with no main words would have nothing to teach or ever review.
                    enabled = review.selectedWordIds.isNotEmpty() && !isImporting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Import")
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = "Or type a single sentence directly - you'll pick which of its words this " +
                "card should teach before it's added.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = singleSentenceInput,
            onValueChange = viewModel::onSingleSentenceInputChange,
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
            label = { Text("Japanese sentence") },
            enabled = !isImporting,
        )
        singleSentenceError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(
            onClick = viewModel::parseSingleSentence,
            enabled = singleSentenceInput.isNotBlank() && !isImporting,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Import sentence")
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
