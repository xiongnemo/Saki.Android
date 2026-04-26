package org.hdhmc.saki.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

@Composable
fun rememberBrowseBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        Brush.verticalGradient(
            listOf(
                colorScheme.primary.copy(alpha = 0.16f).compositeOver(colorScheme.background),
                colorScheme.tertiary.copy(alpha = 0.10f).compositeOver(colorScheme.surface),
                colorScheme.background,
            ),
        )
    }
}

fun bottomContentPadding(overlayPadding: Dp) = PaddingValues(bottom = 24.dp + overlayPadding)