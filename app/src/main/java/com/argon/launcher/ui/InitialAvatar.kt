package com.argon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

private val tilePalette = listOf(
    Color(0xFF3F8F5C), Color(0xFF8C6418), Color(0xFF4F5A55),
    Color(0xFF3F6E8F), Color(0xFF8F3F6E), Color(0xFF6E8F3F)
)

/** A small, deterministic (same name -> same color) accent color, so repeat items stay visually stable. */
fun colorForSeed(seed: String): Color = tilePalette[seed.hashCode().absoluteValue % tilePalette.size]

/**
 * A colored tile/circle showing [seed]'s first letter - used as a placeholder avatar for
 * accounts, and as the fallback behind a mod's real icon while it loads or if it's missing.
 * Anything passed as [content] (e.g. a loaded image) draws on top of the letter.
 */
@Composable
fun InitialAvatar(
    seed: String,
    size: Dp = 44.dp,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(colorForSeed(seed)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            seed.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        content()
    }
}
