package com.iqoo.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lightweight static glass material: layered gradients and hairline border, no live blur. */
@Composable
fun VelocityGlass(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xD83B3D43), Color(0xC2191A1E))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x12FFFFFF))), shape),
        content = content
    )
}
