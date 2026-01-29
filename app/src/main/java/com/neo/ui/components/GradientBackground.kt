package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neo.ui.theme.*

/**
 * Gradient background with animated blur effects
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack)
    ) {
        // Gradient orbs
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = 0.dp)
                .size(384.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeoPurple.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .offset(x = 200.dp, y = 300.dp)
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeoOrange.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .offset(x = 100.dp, y = 600.dp)
                .size(288.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeoTeal.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        content()
    }
}
