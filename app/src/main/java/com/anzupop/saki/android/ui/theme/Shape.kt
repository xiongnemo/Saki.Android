package com.anzupop.saki.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SakiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(
        topStart = 26.dp,
        topEnd = 12.dp,
        bottomEnd = 26.dp,
        bottomStart = 12.dp,
    ),
    large = RoundedCornerShape(
        topStart = 34.dp,
        topEnd = 16.dp,
        bottomEnd = 34.dp,
        bottomStart = 16.dp,
    ),
    extraLarge = RoundedCornerShape(
        topStart = 40.dp,
        topEnd = 20.dp,
        bottomEnd = 40.dp,
        bottomStart = 20.dp,
    ),
)
