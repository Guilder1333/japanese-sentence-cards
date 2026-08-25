package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.griboedov.sentencecards.data.dictionary.DictionaryEntry

/**
 * The word menu's "Up" action: looks up the tapped word in the bundled JMdict-derived dictionary
 * (see THIRD_PARTY_NOTICES.md). Renders nothing while [lookup] is [DictionaryLookup.Hidden].
 */
@Composable
fun DictionaryDialog(lookup: DictionaryLookup, onDismiss: () -> Unit) {
    if (lookup is DictionaryLookup.Hidden) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (lookup) {
                    is DictionaryLookup.Loading -> {
                        Text(lookup.word, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                    is DictionaryLookup.Loaded -> {
                        Text(lookup.word, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        if (lookup.entries.isEmpty()) {
                            Text(
                                "No dictionary entry found.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                lookup.entries.forEachIndexed { index, entry ->
                                    if (index > 0) HorizontalDivider()
                                    DictionaryEntryRow(entry)
                                }
                            }
                        }
                        Text(
                            "Data: JMdict/EDICT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    is DictionaryLookup.Failed -> {
                        Text(lookup.word, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Lookup failed: ${lookup.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    DictionaryLookup.Hidden -> Unit
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DictionaryEntryRow(entry: DictionaryEntry) {
    Column {
        Row {
            entry.kanji?.let {
                Text(it, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
            }
            Text(entry.kana, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entry.meaning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}
