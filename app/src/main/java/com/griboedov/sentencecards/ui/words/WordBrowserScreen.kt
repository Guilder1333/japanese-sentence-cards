package com.griboedov.sentencecards.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.data.knowledge.KnowledgeLevel
import com.griboedov.sentencecards.data.knowledge.knowledgeLevel
import com.griboedov.sentencecards.ui.theme.BacklogPriority
import com.griboedov.sentencecards.ui.theme.EasyPriority
import com.griboedov.sentencecards.ui.theme.HighestPriority
import com.griboedov.sentencecards.ui.theme.MediumPriority

@Composable
fun WordBrowserScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SentenceCardsApp
    val viewModel: WordBrowserViewModel = viewModel(
        factory = viewModelFactory {
            initializer { WordBrowserViewModel(app.wordRepository) }
        },
    )
    val words by viewModel.words.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filtered = remember(words, query) {
        if (query.isBlank()) {
            words
        } else {
            words.filter {
                it.word.contains(query) || it.translation.contains(query, ignoreCase = true) ||
                    it.furigana?.contains(query) == true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Filter words") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        Text(
            text = "${filtered.size} of ${words.size} words",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(filtered, key = { it.id }) { word ->
                WordRow(
                    word = word,
                    onToLearnChange = { viewModel.setToLearn(word.id, it) },
                    onHideFuriganaChange = { viewModel.setHideFurigana(word.id, it) },
                )
            }
        }
    }
}

@Composable
private fun WordRow(
    word: WordEntity,
    onToLearnChange: (Boolean) -> Unit,
    onHideFuriganaChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    word.furigana?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(word.word, style = MaterialTheme.typography.headlineSmall)
                    Text(word.translation, style = MaterialTheme.typography.bodyMedium)
                }
                KnowledgeBadge(word.knowledgeLevel())
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricText("Shown", word.timesShown)
                MetricText("Furigana", word.timesFuriganaShown)
                MetricText("Translation", word.timesTranslationShown)
                MetricText("Quiz OK", word.quizSuccess)
                MetricText("Quiz X", word.quizFails)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("To learn", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = word.toLearn, onCheckedChange = onToLearnChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide furigana", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = word.hideFurigana, onCheckedChange = onHideFuriganaChange)
                }
            }
        }
    }
}

@Composable
private fun MetricText(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun KnowledgeBadge(level: KnowledgeLevel) {
    val color = when (level) {
        KnowledgeLevel.NEW -> HighestPriority
        KnowledgeLevel.LEARNING -> MediumPriority
        KnowledgeLevel.FAMILIAR -> MediumPriority
        KnowledgeLevel.STRONG -> EasyPriority
        KnowledgeLevel.KNOWN -> BacklogPriority
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = level.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
