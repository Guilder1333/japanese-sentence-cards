package com.griboedov.sentencecards.ui.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.SentenceToken
import com.griboedov.sentencecards.data.db.WordEntity
import com.griboedov.sentencecards.ui.theme.furiganaStyleFor
import com.griboedov.sentencecards.ui.theme.japaneseSentenceStyleFor
import com.griboedov.sentencecards.ui.theme.wordStatusColor

/**
 * The normal (non-quiz) flash card: front shows plain Japanese text plus forced furigana only;
 * tapping flips it to the back, which shows the translation and furigana for every word except
 * ones with hidden furigana. The back does not flip back on tap - words there open a flick-style
 * 4-direction menu (press and hold, then drag - see [FlickMenu]) instead. A plain tap on a word
 * does the same thing as flicking it up: both open the dictionary lookup.
 */
@Composable
fun FlashCardView(
    card: CardEntity,
    words: Map<Long, WordEntity>,
    flipped: Boolean,
    onFlip: () -> Unit,
    onWordTap: (Long) -> Unit,
    onWordDirection: (Long, WordDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(targetValue = if (flipped) 180f else 0f, animationSpec = tween(400), label = "cardFlip")

    // Which side is showing, derived from `rotation` but only changing value once per flip
    // (at the 90 degree midpoint) instead of every animation frame. Reading `rotation` directly
    // in composition (as this used to) forced CardFront/CardBack - a FlowRow of many Text nodes -
    // to fully recompose ~24 times over the course of one flip, which was the source of the
    // visible lag; `rotation` itself is now only read inside the graphicsLayer draw-phase lambda
    // below, which doesn't trigger recomposition at all.
    val showBack by remember { derivedStateOf { rotation > 90f } }

    // Flick gesture state, lifted here (rather than per-word) so the highlight popup is drawn as
    // a plain layer within this same composition/window - not a separate Popup window, which
    // would risk swallowing the drag events that are still arriving at the word's pointerInput.
    var flickWord by remember(card.id) { mutableStateOf<WordEntity?>(null) }
    // The tapped token's own (possibly inflected) reading - see CardBack below - rather than a
    // dictionary-form reading looked up by id, since FlickMenu should show exactly what's on the
    // card, not a citation-form reading that may not even match how this occurrence reads.
    var flickFurigana by remember(card.id) { mutableStateOf<String?>(null) }
    var flickDirection by remember(card.id) { mutableStateOf<WordDirection?>(null) }

    Card(
        // Height comes entirely from the incoming modifier (ReviewScreen gives this a weight so
        // the card fills the screen alongside the badge/buttons); only width is set here.
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !showBack,
                onClick = onFlip,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            if (!showBack) {
                // Scrollable so a long sentence (or one that hit the font-size floor) can still be
                // read in full instead of overflowing the card's fixed height.
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    CardFront(card, words)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center,
                ) {
                    CardBack(
                        card = card,
                        words = words,
                        onWordTap = { word -> onWordTap(word.id) },
                        onFlickStart = { word, furigana -> flickWord = word; flickFurigana = furigana; flickDirection = null },
                        onFlickDirectionChange = { flickDirection = it },
                        onFlickEnd = { commit ->
                            val word = flickWord
                            val direction = flickDirection
                            if (commit && word != null && direction != null) {
                                onWordDirection(word.id, direction)
                            }
                            flickWord = null
                            flickFurigana = null
                            flickDirection = null
                        },
                    )
                    flickWord?.let { word -> FlickMenu(word = word, furigana = flickFurigana, highlighted = flickDirection) }
                }
            }
        }
    }
}

@Composable
private fun CardFront(card: CardEntity, words: Map<Long, WordEntity>) {
    val sentenceStyle = japaneseSentenceStyleFor(card.text.length)
    FlowRow(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        for (token in card.structure) {
            val word = token.id?.let { words[it] }
            val isMainWord = token.id != null && token.id in card.mainWordIds
            // Front furigana rule: hidden by default, shown only for words still needing the
            // crutch (forceFurigana - either still unconfirmed, or explicitly forced back on) -
            // and never for this sentence's main/quizzed words, since those are exactly what the
            // reading quiz is testing recall of.
            val showFurigana = word != null && word.forceFurigana && !isMainWord
            TokenText(
                token = token,
                furigana = if (showFurigana) token.furigana else null,
                sentenceStyle = sentenceStyle,
                color = wordStatusColor(word, isMainWord),
            )
        }
    }
}

/** Minimum drag distance, in dp, before a flick counts as a direction rather than a cancel. */
private val FlickThreshold = 24.dp

@Composable
private fun CardBack(
    card: CardEntity,
    words: Map<Long, WordEntity>,
    onWordTap: (WordEntity) -> Unit,
    onFlickStart: (WordEntity, furigana: String?) -> Unit,
    onFlickDirectionChange: (WordDirection?) -> Unit,
    onFlickEnd: (committed: Boolean) -> Unit,
) {
    val sentenceStyle = japaneseSentenceStyleFor(card.text.length)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
            for (token in card.structure) {
                val word = token.id?.let { words[it] }
                val isMainWord = token.id != null && token.id in card.mainWordIds
                val tokenModifier = if (word != null) {
                    Modifier
                        // Plain tap: dictionary lookup (same as flicking up). A separate
                        // pointerInput block, since detectDragGesturesAfterLongPress below
                        // leaves short taps untouched.
                        .pointerInput(word.id) {
                            detectTapGestures(onTap = { onWordTap(word) })
                        }
                        // Press and hold, then drag like a flick keyboard: direction is
                        // recomputed from the total drag offset on every move, and committed
                        // only once the finger lifts.
                        .pointerInput(word.id) {
                            val thresholdPx = FlickThreshold.toPx()
                            var total = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    total = Offset.Zero
                                    onFlickStart(word, token.furigana)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    total += amount
                                    onFlickDirectionChange(directionFromOffset(total, thresholdPx))
                                },
                                onDragEnd = { onFlickEnd(true) },
                                onDragCancel = { onFlickEnd(false) },
                            )
                        }
                } else {
                    Modifier
                }
                Box(modifier = tokenModifier) {
                    // The back is the reveal side - furigana always shows here regardless of
                    // forceFurigana, which only governs the front.
                    TokenText(
                        token = token,
                        furigana = token.furigana,
                        sentenceStyle = sentenceStyle,
                        color = wordStatusColor(word, isMainWord),
                    )
                }
            }
        }
        Text(
            text = card.translation,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TokenText(token: SentenceToken, furigana: String?, sentenceStyle: TextStyle, color: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(
            text = furigana ?: "",
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
