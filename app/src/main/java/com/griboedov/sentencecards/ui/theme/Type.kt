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

/** Large centered style used for the Japanese sentence text on the flash card itself. */
val JapaneseSentenceStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 30.sp,
    lineHeight = 46.sp,
)

val FuriganaStyle = TextStyle(
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
)
