package com.anzupop.saki.android.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.MaterialTheme

@Composable
fun rememberAppTabBackgroundBrush(selectedTab: AppTab): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme, selectedTab) {
        when (selectedTab) {
            AppTab.BROWSE -> Brush.verticalGradient(
                listOf(
                    colorScheme.primary.copy(alpha = 0.16f).compositeOver(colorScheme.background),
                    colorScheme.tertiary.copy(alpha = 0.10f).compositeOver(colorScheme.surface),
                    colorScheme.background,
                ),
            )

            AppTab.SETTINGS -> Brush.verticalGradient(
                listOf(
                    colorScheme.secondary.copy(alpha = 0.16f).compositeOver(colorScheme.background),
                    colorScheme.primary.copy(alpha = 0.08f).compositeOver(colorScheme.surface),
                    colorScheme.background,
                ),
            )
        }
    }
}
