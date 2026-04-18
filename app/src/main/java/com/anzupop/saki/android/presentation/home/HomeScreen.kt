package com.anzupop.saki.android.presentation.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anzupop.saki.android.ui.theme.SakiAndroidTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var revealed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        revealed = true
    }

    val heroAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "heroAlpha",
    )
    val heroOffset by animateIntOffsetAsState(
        targetValue = if (revealed) IntOffset.Zero else IntOffset(0, 90),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "heroOffset",
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .graphicsLayer {
                            translationY = heroOffset.y.toFloat()
                        }
                        .alpha(heroAlpha),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Saki.Android",
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        text = "Phase 1 foundation is in place for Hilt, Room, Retrofit, and Media3.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(PHASE_ONE_PILLARS) { pillar ->
                PillarCard(
                    title = pillar.title,
                    description = pillar.description,
                    accentShape = pillar.shape,
                )
            }
        }
    }
}

@Composable
private fun PillarCard(
    title: String,
    description: String,
    accentShape: Shape,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = accentShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(accentShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Pillar(
    val title: String,
    val description: String,
    val shape: Shape,
)

private val PHASE_ONE_PILLARS = listOf(
    Pillar(
        title = "Dependency Injection",
        description = "Hilt is bootstrapped with an application class, activity entry point, and shared coroutine dispatchers.",
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 16.dp, bottomEnd = 34.dp, bottomStart = 16.dp),
    ),
    Pillar(
        title = "Networking",
        description = "Retrofit and OkHttp are added with a Hilt module that provides a reusable client and builder for endpoint-aware APIs.",
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 12.dp, bottomEnd = 26.dp, bottomStart = 12.dp),
    ),
    Pillar(
        title = "Playback + Data",
        description = "Media3 and Room are installed and the project tree is ready for the database schema and playback service in the next phases.",
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 20.dp, bottomEnd = 40.dp, bottomStart = 20.dp),
    ),
)

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SakiAndroidTheme {
        HomeScreen()
    }
}
