package com.griboedov.sentencecards.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

private val nextPopupId = AtomicLong()

/**
 * One message for [FadingPopup]. [id] is unique per instance, so showing the very same text twice
 * in a row (e.g. marking the same word to-learn again) still restarts the popup.
 */
data class PopupMessage(val text: String, val isError: Boolean = false, val id: Long = nextPopupId.incrementAndGet())

/**
 * The feedback for marking a word to-learn: how many sentences with it now back cards (see
 * [com.griboedov.sentencecards.data.cards.CardGenerator.generateCardsForWord]). Zero is flagged as
 * an error - the word is tracked, but there's nothing to review it with until a sentence
 * containing it gets imported.
 */
fun cardsAddedMessage(word: String, count: Int) = PopupMessage(
    text = if (count == 1) {
        "1 sentence with $word was added to cards"
    } else {
        "$count sentences with $word were added to cards"
    },
    isError = count == 0,
)

/**
 * A small toast-like popup that fades in, stays for a couple of seconds, then fades out and calls
 * [onDismiss] so the owner can clear [message]. Meant to be placed at the bottom of a screen's
 * root Box. Drawn with a plain background rather than a Surface so it never swallows taps meant
 * for whatever is underneath it.
 */
@Composable
fun FadingPopup(message: PopupMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // Keeps the last message around while it fades out, after [message] itself is already null.
    var shown by remember { mutableStateOf<PopupMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        shown = message
        visible = true
        delay(VISIBLE_MILLIS)
        visible = false
        delay(FADE_MILLIS.toLong())
        currentOnDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(FADE_MILLIS)),
        exit = fadeOut(tween(FADE_MILLIS)),
    ) {
        val msg = shown ?: return@AnimatedVisibility
        val colors = MaterialTheme.colorScheme
        Text(
            text = msg.text,
            color = if (msg.isError) colors.onError else colors.inverseOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .background(if (msg.isError) colors.error else colors.inverseSurface, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private const val VISIBLE_MILLIS = 2_000L
private const val FADE_MILLIS = 400
