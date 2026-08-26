package com.griboedov.sentencecards.ui.importsentences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.TokenKind
import com.griboedov.sentencecards.ui.theme.KnownWordColor
import com.griboedov.sentencecards.ui.theme.MainWordColor
import com.griboedov.sentencecards.ui.theme.furiganaStyleFor
import com.griboedov.sentencecards.ui.theme.japaneseSentenceStyleFor

/**
 * The "single sentence" import review view: shown once a typed sentence has been parsed, in place
 * of the normal [com.griboedov.sentencecards.ui.review.FlashCardView] - a deliberately separate,
 * much simpler view rather than a reuse of it, since the interaction is entirely different:
 *  - always shows the "back" (translation + furigana for every word) - there's no front/flip here,
 *    nothing to hide yet since none of this sentence's words are tracked/reviewed at all until
 *    Import is actually pressed.
 *  - no 4-direction flick menu; a plain tap on a word just toggles it between green (not selected)
 *    and blue (selected as this card's main word - the only choice this screen offers).
 *  - the bottom Import/Cancel row belongs to the screen hosting this view, not to the card itself -
 *    see [ImportSentencesScreen].
 */
@Composable
fun SingleSentenceCardView(
    sentenceText: String,
    structure: List<SentenceToken>,
    translation: String,
    selectedWordIds: Set<Long>,
    onToggleWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sentenceStyle = japaneseSentenceStyleFor(sentenceText.length)
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        for (token in structure) {
                            val isWord = token.tokenKind == TokenKind.WORD && token.id != null
                            val selected = isWord && token.id in selectedWordIds
                            val color = if (isWord) (if (selected) MainWordColor else KnownWordColor) else null
                            val tokenModifier = if (isWord) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onToggleWord(token.id) }
                            } else {
                                Modifier
                            }
                            Box(modifier = tokenModifier) {
                                SingleSentenceToken(token = token, sentenceStyle = sentenceStyle, color = color)
                            }
                        }
                    }
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleSentenceToken(token: SentenceToken, sentenceStyle: TextStyle, color: Color?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(
            text = token.furigana ?: "",
            style = furiganaStyleFor(sentenceStyle),
            color = color ?: Color.Unspecified,
        )
        Text(
            text = token.word,
            style = sentenceStyle,
            color = color ?: Color.Unspecified,
        )
    }
}
