package com.griboedov.sentencecards.ui.theme

import androidx.compose.ui.graphics.Color
import com.griboedov.sentencecards.data.db.WordEntity

/**
 * Color-codes a word by review status:
 *  - a sentence's main words (the ones it was picked to teach) always read as [MainWordColor],
 *    regardless of known/unknown state, since that's this card's actual study focus;
 *  - otherwise [KnownWordColor] means the word is marked known ([WordEntity.toLearn] false) and
 *    [UnknownWordColor] means it's marked to learn ([WordEntity.toLearn] true).
 *
 * Untracked tokens (particles, katakana - never stored as a [WordEntity]) return null, meaning
 * "use the default text color".
 */
fun wordStatusColor(word: WordEntity?, isMainWord: Boolean = false): Color? = when {
    word == null -> null
    isMainWord -> MainWordColor
    word.toLearn -> UnknownWordColor
    else -> KnownWordColor
}
