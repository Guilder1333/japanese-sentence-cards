package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.ui.theme.EasyPriority
import com.griboedov.sentencecards.ui.theme.HighestPriority
import kotlin.math.atan2

/**
 * Purely visual flick-keyboard style overlay for the word 4-direction menu from the README:
 * "Pressing on word should open 4 directions menu - right: know, left: learn, down: force
 * furigana, up: dictionary". It has no click handlers of its own - [FlashCardView] drives it by
 * press-and-hold-then-drag (see `detectDragGesturesAfterLongPress` there): [highlighted] reflects
 * whichever direction the current drag points to, and lifting the finger commits it.
 *
 * Each direction gets a glyph that hints at its meaning rather than a generic arrow: 知 ("chi" -
 * wisdom/knowledge) in green for "mark known", 不 ("fu" - un-/non-, i.e. not yet known) in red for
 * "mark to learn", ふ (hiragana "fu", as in furigana) for "force furigana", and a book for the
 * dictionary lookup.
 *
 * [word] is the text to show in the middle - a tracked word's dictionary form, or the token's own
 * base form for a word that isn't tracked yet. [canForceFurigana] is false for a kana word: there
 * is no reading to annotate 「わかる」 with, so the down slot is drawn greyed out and
 * [FlashCardView] never resolves a downward drag to a direction for one, making the action
 * unreachable rather than silently doing nothing.
 */
@Composable
fun FlickMenu(word: String, furigana: String?, canForceFurigana: Boolean, highlighted: WordDirection?) {
    Surface(shape = CircleShape, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                furigana?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            DirectionSlot(
                alignment = Alignment.TopCenter,
                active = highlighted == WordDirection.UP,
                activeColor = MaterialTheme.colorScheme.secondary,
                contentDescription = "Dictionary",
            ) { tint -> Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = tint) }
            DirectionSlot(
                alignment = Alignment.BottomCenter,
                active = highlighted == WordDirection.DOWN,
                activeColor = MaterialTheme.colorScheme.secondary,
                contentDescription = if (canForceFurigana) "Force furigana" else "Force furigana unavailable",
                enabled = canForceFurigana,
            ) { tint -> DirectionGlyph("ふ", tint) }
            DirectionSlot(
                alignment = Alignment.CenterStart,
                active = highlighted == WordDirection.LEFT,
                activeColor = HighestPriority,
                contentDescription = "Mark to learn",
            ) { tint -> DirectionGlyph("不", tint) }
            DirectionSlot(
                alignment = Alignment.CenterEnd,
                active = highlighted == WordDirection.RIGHT,
                activeColor = EasyPriority,
                contentDescription = "Mark known",
            ) { tint -> DirectionGlyph("知", tint) }
        }
    }
}

/** Ordinary Material disabled-content opacity, for a direction this word does not support. */
private const val DisabledAlpha = 0.38f

/** Nearest of the four directions for a cumulative drag [offset], or null while inside the dead zone. */
fun directionFromOffset(offset: Offset, thresholdPx: Float): WordDirection? {
    if ((offset.x * offset.x + offset.y * offset.y) < thresholdPx * thresholdPx) return null
    val degrees = Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble()))
    return when (degrees) {
        in -45.0..45.0 -> WordDirection.RIGHT
        in 45.0..135.0 -> WordDirection.DOWN
        in -135.0..-45.0 -> WordDirection.UP
        else -> WordDirection.LEFT
    }
}

@Composable
private fun DirectionGlyph(text: String, tint: Color) {
    Text(text, color = tint, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
}

@Composable
private fun BoxScope.DirectionSlot(
    alignment: Alignment,
    active: Boolean,
    activeColor: Color,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable (tint: Color) -> Unit,
) {
    val background = if (active && enabled) activeColor else MaterialTheme.colorScheme.surfaceVariant
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
        active -> Color.White
        else -> activeColor
    }
    Surface(
        shape = CircleShape,
        color = background,
        modifier = Modifier
            .align(alignment)
            .padding(4.dp)
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            content(foreground)
        }
    }
}
