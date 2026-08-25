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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp

private const val SAMPLE_IMPORT_JSON = """[
  {
    "translation": "This word is English.",
    "structure": [
      { "word": "この", "translation": "this", "kind": 2 },
      { "word": "言葉", "furigana": "ことば", "translation": "word", "kind": 1, "id": 1234 },
      { "word": "は", "translation": "(topic marker)", "kind": 2 },
      { "word": "イギリス", "translation": "England/British", "kind": 3 },
      { "word": "語", "furigana": "ご", "translation": "language", "kind": 1, "id": 12354 }
    ]
  }
]"""

/**
 * Bulk import of already-structured sentences (README JSON schema). Plain-text import via the
 * adapted parsing script is a later addition - this screen only accepts the structured form.
 *
 * Two ways in: pick a JSON file (the expected path once these get into the megabytes - too big to
 * comfortably paste), or paste a small one directly for quick tests.
 */
@Composable
fun ImportSentencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as SentenceCardsApp
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportViewModel(app.importer) }
        },
    )
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val resultMessage by viewModel.resultMessage.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromFile(context, uri)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Import a JSON file of already-structured sentences, or paste a small one " +
                "below. Plain-text import (auto-parsing) is coming later.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = { filePicker.launch(arrayOf("*/*")) },
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
        Text("Or paste JSON directly (for small tests):", style = MaterialTheme.typography.labelMedium)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 8.dp),
            label = { Text("Structured sentences JSON") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { text = SAMPLE_IMPORT_JSON },
                modifier = Modifier.weight(1f),
            ) {
                Text("Load sample")
            }
            Button(
                onClick = { viewModel.import(text) },
                enabled = text.isNotBlank() && !isImporting,
                modifier = Modifier.weight(1f),
            ) {
                Text("Import pasted text")
            }
        }

        resultMessage?.let { message ->
            Snackbar(
                modifier = Modifier.padding(top = 16.dp),
                action = { OutlinedButton(onClick = viewModel::clearMessage) { Text("Dismiss") } },
            ) {
                Text(message)
            }
            LaunchedEffect(message) {
                if (message.startsWith("Imported")) text = ""
            }
        }
    }
}
