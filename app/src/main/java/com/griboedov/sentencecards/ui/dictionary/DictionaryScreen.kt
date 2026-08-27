package com.griboedov.sentencecards.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry
import com.griboedov.sentencecards.data.repository.WordStatusChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Browses the full bundled dictionary (as opposed to the Word Browser, which only shows words
 * already tracked internally). Each result can be added into the internal words database as
 * known / to-learn / force-furigana. Tapping a result opens a full-screen detail view with the
 * complete, untruncated meaning - the list row itself only shows a short preview.
 */
@Composable
fun DictionaryScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SentenceCardsApp
    val viewModel: DictionaryScreenViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DictionaryScreenViewModel(app.dictionaryRepository, app.wordRepository, app.japaneseTokenizer) }
        },
    )
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var selectedEntry by remember { mutableStateOf<DictionaryEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search kanji, reading, or meaning") },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        // Clearing also empties the results list, not just the text field.
                        IconButton(onClick = viewModel::clearQuery) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                val text = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                                // Pasting searches immediately, rather than waiting for a second tap.
                                if (!text.isNullOrEmpty()) viewModel.pasteAndSearch(text)
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste and search")
                        }
                    }
                    IconButton(onClick = viewModel::search) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )

        if (isSearching) {
            Text(
                "Searching...",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (hasSearched) {
            Text(
                "${results.size} result(s)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(results, key = { it.kanji.orEmpty() + "|" + it.kana }) { entry ->
                DictionaryResultRow(
                    entry = entry,
                    onAdd = { status -> viewModel.addWord(entry, status) },
                    onClick = { selectedEntry = entry },
                )
            }
        }

        message?.let { msg ->
            Snackbar(modifier = Modifier.padding(top = 8.dp)) { Text(msg) }
            LaunchedEffect(msg) {
                delay(2_000)
                viewModel.clearMessage()
            }
        }
    }

    selectedEntry?.let { entry ->
        DictionaryDetailScreen(
            entry = entry,
            onBack = { selectedEntry = null },
            onAdd = { status ->
                viewModel.addWord(entry, status)
                selectedEntry = null
            },
        )
    }
}

@Composable
private fun DictionaryResultRow(entry: DictionaryEntry, onAdd: (WordStatusChoice) -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            EntryHeader(entry)
            Text(
                text = entry.meaning,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            AddButtonsRow(onAdd = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** Full, untruncated view of one entry - opened by tapping its row in the results list. */
@Composable
private fun DictionaryDetailScreen(entry: DictionaryEntry, onBack: () -> Unit, onAdd: (WordStatusChoice) -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    EntryHeader(entry)
                }
                Text(
                    text = entry.meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 20.dp),
                )
                AddButtonsRow(onAdd = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }
    }
}

@Composable
private fun EntryHeader(entry: DictionaryEntry) {
    Row(verticalAlignment = Alignment.Bottom) {
        entry.kanji?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
        }
        Text(entry.kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddButtonsRow(onAdd: (WordStatusChoice) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onAdd(WordStatusChoice.TO_LEARN) }, modifier = Modifier.weight(1f)) {
            Text("To learn")
        }
        OutlinedButton(onClick = { onAdd(WordStatusChoice.KNOWN) }, modifier = Modifier.weight(1f)) {
            Text("Known")
        }
        OutlinedButton(onClick = { onAdd(WordStatusChoice.FORCE_FURIGANA) }, modifier = Modifier.weight(1f)) {
            Text("Force furigana")
        }
    }
}
