package com.griboedov.sentencecards.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.griboedov.sentencecards.data.db.WordEntity
import kotlin.math.atan2

/**
 * Purely visual flick-keyboard style overlay for the word 4-direction menu from the README:
 * "Pressing on word should open 4 directions menu - right: know, left: learn, down: hide
 * furigana, up: dictionary". It has no click handlers of its own - [FlashCardView] drives it by
 * press-and-hold-then-drag (see `detectDragGesturesAfterLongPress` there): [highlighted] reflects
 * whichever direction the current drag points to, and lifting the finger commits it.
 */
@Composable
fun FlickMenu(word: WordEntity, highlighted: WordDirection?) {
    Surface(shape = CircleShape, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.size(216.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(word.word, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                word.furigana?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            DirectionIcon(Icons.AutoMirrored.Filled.MenuBook, "Dictionary (TODO)", Alignment.TopCenter, highlighted == WordDirection.UP)
            DirectionIcon(Icons.Filled.KeyboardArrowDown, "Hide furigana", Alignment.BottomCenter, highlighted == WordDirection.DOWN)
            DirectionIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mark to learn", Alignment.CenterStart, highlighted == WordDirection.LEFT)
            DirectionIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mark known", Alignment.CenterEnd, highlighted == WordDirection.RIGHT)
        }
    }
}

/** Nearest of the four directions for a cumulative drag [offset], or null while inside the dead zone. */
fun directionFromOffset(offset: Offset, thresholdPx: Float): WordDirection? {
    if ((offset.x * offset.x + offset.y * offset.y) < thresholdPx * thresholdPx) return null
    val degrees = Math.toDegrees(atan2(offset.y.toDouble(), offset.x.toDouble()))
    return when {
        degrees in -45.0..45.0 -> WordDirection.RIGHT
        degrees in 45.0..135.0 -> WordDirection.DOWN
        degrees in -135.0..-45.0 -> WordDirection.UP
        else -> WordDirection.LEFT
    }
}

@Composable
private fun BoxScope.DirectionIcon(icon: ImageVector, label: String, alignment: Alignment, active: Boolean) {
    val background = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = background,
        modifier = Modifier.align(alignment).padding(4.dp).size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = foreground)
        }
    }
}
