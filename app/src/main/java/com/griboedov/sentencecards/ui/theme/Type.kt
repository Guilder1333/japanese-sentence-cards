package com.griboedov.sentencecards.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)

/** Large centered style used for the Japanese sentence text on the flash card itself. This is the
 * baseline/max size for a short sentence - see [japaneseSentenceStyleFor] for the size actually
 * used, which scales down as the sentence gets longer. */
val JapaneseSentenceStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 48.sp,
    lineHeight = 74.sp,
)

val FuriganaStyle = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
)

/** Never shrink the sentence font below this, however long the sentence gets - it would stop
 * being readable at a glance. */
private const val MinSentenceFontSp = 22f

/** Never grow the sentence font above [JapaneseSentenceStyle]'s own size, however short the
 * sentence is - it would look oversized/cartoonish on the card. */
private val MaxSentenceFontSp = JapaneseSentenceStyle.fontSize.value

/**
 * [JapaneseSentenceStyle], scaled to fit [sentenceLength] (the full sentence's character count):
 * short sentences stay near [MaxSentenceFontSp] to make good use of the (larger) card, long ones
 * shrink towards [MinSentenceFontSp] instead of overflowing it. Line height scales alongside the
 * font size to keep the same visual proportions as the base style.
 */
fun japaneseSentenceStyleFor(sentenceLength: Int): TextStyle {
    // Sentences up to 8 characters get the max size; each character past that shaves a bit off.
    val fontSp = (MaxSentenceFontSp - (sentenceLength - 8).coerceAtLeast(0) * 1.1f)
        .coerceIn(MinSentenceFontSp, MaxSentenceFontSp)
    val lineHeightRatio = JapaneseSentenceStyle.lineHeight.value / JapaneseSentenceStyle.fontSize.value
    return JapaneseSentenceStyle.copy(fontSize = fontSp.sp, lineHeight = (fontSp * lineHeightRatio).sp)
}

/** Furigana font size as a fraction of the sentence font it annotates - roughly [FuriganaStyle]'s
 * original 13/30 relationship to the sentence style's old fixed 30sp size. */
private const val FuriganaScaleRatio = 0.43f

/**
 * [FuriganaStyle], scaled proportionally to [sentenceStyle]'s font size (within its own readable
 * bounds) so furigana keeps pace with the main text as it grows or shrinks.
 */
fun furiganaStyleFor(sentenceStyle: TextStyle): TextStyle {
    val fontSp = (sentenceStyle.fontSize.value * FuriganaScaleRatio).coerceIn(11f, 20f)
    return FuriganaStyle.copy(fontSize = fontSp.sp)
}
