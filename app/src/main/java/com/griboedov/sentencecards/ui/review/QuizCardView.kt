package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.data.db.SentenceEntity
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.ui.theme.JapaneseSentenceStyle

/**
 * The "special kind of flash card" shown once a sentence is marked Learned: quizzes the reading
 * and meaning of each main word one at a time (README: "Quiz should include all the main words in
 * the sentence... Also successful words removed from main words of the sentence, if there are no
 * more main words... we can assume it as learned").
 */
@Composable
fun QuizCardView(
    card: SentenceEntity,
    words: Map<Long, WordEntity>,
    revealed: Boolean,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wordId = card.quizRemainingWordIds.firstOrNull()
    val word = wordId?.let { words[it] }

    Card(modifier = modifier.fillMaxWidth().aspectRatio(1.3f)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Quiz - ${card.quizRemainingWordIds.size} word(s) left",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "What's the reading and meaning?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            if (word == null) {
                Text("No word to quiz.", textAlign = TextAlign.Center)
                return@Column
            }

            Text(text = word.word, style = JapaneseSentenceStyle, textAlign = TextAlign.Center)

            if (revealed) {
                Text(
                    text = word.furigana.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAnswer(true) }) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text("  Got it right")
                    }
                    OutlinedButton(onClick = { onAnswer(false) }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Text("  Got it wrong")
                    }
                }
            } else {
                Button(onClick = onReveal, modifier = Modifier.padding(top = 24.dp)) {
                    Text("Show answer")
                }
            }
        }
    }
}
