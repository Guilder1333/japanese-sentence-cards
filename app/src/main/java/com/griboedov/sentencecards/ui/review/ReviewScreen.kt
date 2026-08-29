package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.griboedov.sentencecards.SentenceCardsApp
import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.queue.ReviewAction
import com.griboedov.sentencecards.ui.theme.BacklogPriority
import com.griboedov.sentencecards.ui.theme.EasyPriority
import com.griboedov.sentencecards.ui.theme.HighestPriority
import com.griboedov.sentencecards.ui.theme.MediumPriority

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as SentenceCardsApp
    val viewModel: ReviewViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ReviewViewModel(app.cardRepository, app.wordRepository, app.dictionaryRepository, app.cardGenerator)
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DictionaryDialog(lookup = state.dictionaryLookup, onDismiss = viewModel::dismissDictionary)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.isEmpty -> Text(
                text = "No cards to review yet.\nImport some sentences, then mark a word " +
                    "\"to learn\" in the word browser or dictionary to generate cards for it.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
            else -> {
                val card = state.card!!
                // Fills the whole screen (no outer scroll): the card/quiz gets whatever's left
                // over via weight(1f) after the badge, hint text and buttons take their natural
                // height, so the card is as big as the screen allows. Long content that doesn't
                // fit inside that space scrolls internally instead (see FlashCardView/QuizCardView).
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QueueBadge(level = card.queueLevel, isQuiz = card.pendingQuiz)

                    if (card.pendingQuiz) {
                        QuizCardView(
                            card = card,
                            words = state.words,
                            options = state.quizOptions,
                            correctReadings = state.quizCorrectReadings,
                            answers = state.quizAnswers,
                            allAnswered = state.quizAllAnswered,
                            onSelect = viewModel::onQuizOptionSelected,
                            onContinue = viewModel::onQuizContinue,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        FlashCardView(
                            card = card,
                            tokenWords = state.tokenWords,
                            flipped = state.isFlipped,
                            onFlip = viewModel::onFlip,
                            onWordTap = viewModel::onWordTapped,
                            onWordDirection = viewModel::onWordDirection,
                            modifier = Modifier.weight(1f),
                        )
                        ReviewButtonsRow(onGrade = viewModel::onGrade)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueBadge(level: QueueLevel, isQuiz: Boolean) {
    val (color, label) = when {
        isQuiz -> MediumPriority to "Quiz"
        level == QueueLevel.HIGHEST -> HighestPriority to "Hard queue"
        level == QueueLevel.MEDIUM -> MediumPriority to "Medium queue"
        level == QueueLevel.EASY -> EasyPriority to "Easy queue"
        else -> BacklogPriority to "Backlog queue"
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ReviewButtonsRow(onGrade: (ReviewAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradeButton("Hard", HighestPriority, Modifier.weight(1f)) { onGrade(ReviewAction.HARD) }
        GradeButton("Medium", MediumPriority, Modifier.weight(1f)) { onGrade(ReviewAction.MEDIUM) }
        GradeButton("Easy", EasyPriority, Modifier.weight(1f)) { onGrade(ReviewAction.EASY) }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Button(
            onClick = { onGrade(ReviewAction.LEARNED) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Learned")
        }
    }
}

@Composable
private fun GradeButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Text(label)
    }
}
