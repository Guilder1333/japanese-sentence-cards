package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.ui.theme.EasyPriority
import com.griboedov.sentencecards.ui.theme.HighestPriority
import com.griboedov.sentencecards.ui.theme.JapaneseSentenceStyle
import com.griboedov.sentencecards.ui.theme.wordStatusColor

/**
 * The "special kind of flash card" shown once a sentence is marked Learned: a reading quiz for
 * every main word at once (README: "Quiz should include all the main words in the sentence").
 * Each word gets 2-4 multiple-choice reading options; picking one locks it in and reveals right
 * vs wrong immediately, but nothing is graded in the data layer until [onContinue] is pressed with
 * every word answered. Meaning isn't quizzed yet - extracting it automatically isn't
 * straightforward, so for now this only covers reading.
 *
 * No furigana appears anywhere on this card (not even force-furigana words) - showing it would
 * give the answer away.
 */
@Composable
fun QuizCardView(
    card: SentenceEntity,
    words: Map<Long, WordEntity>,
    options: Map<Long, List<String>>,
    answers: Map<Long, String>,
    allAnswered: Boolean,
    onSelect: (Long, String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Quiz - what's the reading?", style = MaterialTheme.typography.labelLarge)

            FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
                for (token in card.structure) {
                    val word = token.id?.let { words[it] }
                    val isMainWord = token.id != null && token.id in card.mainWordIds
                    Text(
                        text = token.word,
                        style = JapaneseSentenceStyle,
                        color = wordStatusColor(word, isMainWord) ?: Color.Unspecified,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }

            if (options.isEmpty()) {
                Text("No readings left to quiz.", style = MaterialTheme.typography.bodyMedium)
            }

            for ((wordId, wordOptions) in options) {
                val word = words[wordId] ?: continue
                QuizQuestion(
                    word = word,
                    options = wordOptions,
                    selected = answers[wordId],
                    onSelect = { option -> onSelect(wordId, option) },
                )
            }

            Button(onClick = onContinue, enabled = allAnswered, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun QuizQuestion(
    word: WordEntity,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(word.word, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in options) {
                val isCorrectOption = option == word.furigana
                val colors = when {
                    selected == null -> ButtonDefaults.filledTonalButtonColors()
                    isCorrectOption -> ButtonDefaults.buttonColors(containerColor = EasyPriority, contentColor = Color.White)
                    option == selected -> ButtonDefaults.buttonColors(containerColor = HighestPriority, contentColor = Color.White)
                    else -> ButtonDefaults.filledTonalButtonColors()
                }
                Button(onClick = { if (selected == null) onSelect(option) }, colors = colors) {
                    Text(option)
                }
            }
        }
    }
}
