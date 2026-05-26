package com.neo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neo.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// ─── LIQUID GLASS MODIFIER SYSTEM ──────────────────────────────────────────────
// Performance-optimized glass rendering for use ONLY on elevated, floating
// interactive surfaces (modals, cards, special buttons).
//
// Rendering layers (combined into single drawBehind for performance):
//   1. Drop shadow (via shadow modifier — GPU-accelerated)
//   2. Glass fill (via background modifier)
//   3. Inner gradient + specular band + directional border (single drawBehind)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Premium liquid glass card — optimized single-pass rendering.
 * Use ONLY on floating elevated surfaces, NOT on layout chrome.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    fillColor: Color = GlassWhite12,
    shadowElevation: Dp = 12.dp,
    cornerRadius: Float = 60f
): Modifier = this
    // Shadow (GPU-accelerated, no overdraw cost)
    .shadow(
        elevation = shadowElevation,
        shape = shape,
        ambientColor = GlassShadow,
        spotColor = GlassShadowSoft
    )
    .clip(shape)
    .background(fillColor, shape)
    // Single drawBehind pass — all glass layers combined for performance
    .drawBehind {
        val cr = CornerRadius(cornerRadius, cornerRadius)

        // Combined inner gradient: specular top + subtle bottom shadow
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to GlassReflectionEdge,       // Bright specular top
                    0.04f to GlassReflectionTop,        // Quick fade
                    0.12f to GlassReflectionSoft,       // Soft body glow
                    0.4f to Color.Transparent,           // Clear middle
                    0.9f to GlassInnerShadow,           // Subtle bottom shadow
                    1.0f to GlassInnerShadow
                )
            ),
            cornerRadius = cr
        )

        // Directional border — top bright, bottom dim (simulates overhead light)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to GlassBorderLight,
                    0.3f to GlassBorderMid,
                    0.7f to GlassBorderSubtle,
                    1.0f to Color(0x08FFFFFF)
                )
            ),
            cornerRadius = cr,
            style = Stroke(width = 1.5f)
        )
    }

/**
 * Elevated liquid glass for modals and overlays.
 * Higher opacity fill + deeper shadow.
 */
fun Modifier.liquidGlassElevated(
    shape: Shape = RoundedCornerShape(28.dp),
    cornerRadius: Float = 84f
): Modifier = this
    .shadow(
        elevation = 24.dp,
        shape = shape,
        ambientColor = GlassShadow,
        spotColor = GlassShadow
    )
    .clip(shape)
    .background(Color(0xD91C1C1F), shape)
    .drawBehind {
        val cr = CornerRadius(cornerRadius, cornerRadius)

        // Combined specular + body + border in single pass
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to GlassReflectionEdge,
                    0.03f to GlassReflectionTop,
                    0.08f to GlassWhite4,
                    0.4f to Color.Transparent,
                    0.9f to GlassInnerShadow,
                    1.0f to GlassInnerShadow
                )
            ),
            cornerRadius = cr
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to GlassBorderLight,
                    0.4f to GlassBorderSubtle,
                    1.0f to Color(0x05FFFFFF)
                )
            ),
            cornerRadius = cr,
            style = Stroke(width = 1.5f)
        )
    }

// ── Interactive Glass ────────────────────────────────────────────────────────

/**
 * Glass button with tactile press feedback.
 */
@Composable
fun Modifier.liquidGlassButton(
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glass_btn_scale"
    )

    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .liquidGlass(
            shape = shape,
            fillColor = GlassWhite16,
            shadowElevation = if (isPressed) 4.dp else 10.dp
        )
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick() }
            )
        }
}

// ── Glass Composables ────────────────────────────────────────────────────────

/**
 * Premium liquid glass card composable.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fillColor: Color = GlassWhite12,
    shadowElevation: Dp = 12.dp,
    cornerRadius: Float = 60f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            fillColor = fillColor,
            shadowElevation = shadowElevation,
            cornerRadius = cornerRadius
        ),
        content = content
    )
}

/**
 * Glass surface with optional accent glow.
 * For special elevated cards that need brand color presence.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fillColor: Color = GlassWhite12,
    shadowElevation: Dp = 16.dp,
    cornerRadius: Float = 72f,
    accentGlow: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = GlassShadow,
                spotColor = GlassShadowSoft
            )
            .clip(shape)
            .background(fillColor, shape)
            .drawBehind {
                val cr = CornerRadius(cornerRadius, cornerRadius)

                // Combined specular + body gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to GlassReflectionEdge,
                            0.04f to GlassReflectionTop,
                            0.12f to GlassReflectionSoft,
                            0.4f to Color.Transparent,
                            0.9f to GlassInnerShadow,
                            1.0f to GlassInnerShadow
                        )
                    ),
                    cornerRadius = cr
                )

                // Optional accent glow from bottom
                if (accentGlow != Color.Transparent) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(accentGlow, Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 1.1f),
                            radius = size.width * 0.8f
                        ),
                        cornerRadius = cr
                    )
                }

                // Directional border
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to GlassBorderLight,
                            0.3f to GlassBorderMid,
                            0.7f to GlassBorderSubtle,
                            1.0f to Color(0x08FFFFFF)
                        )
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.5f)
                )
            },
        content = content
    )
}

// ── Legacy Compatibility ─────────────────────────────────────────────────────

@Suppress("UNUSED_PARAMETER")
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(20.dp),
    fillColor: Color = GlassWhite12,
    borderColor: Color = GlassBorderLight,
    borderWidth: Dp = 0.5.dp,
    shadowElevation: Dp = 12.dp,
    innerGlowFraction: Float = 0.3f
): Modifier = liquidGlass(shape = shape, fillColor = fillColor, shadowElevation = shadowElevation)

@Suppress("UNUSED_PARAMETER")
fun Modifier.glassElevated(
    shape: Shape = RoundedCornerShape(28.dp),
    fillColor: Color = Color(0xD91C1C1F),
    borderColor: Color = GlassBorderLight,
    shadowElevation: Dp = 24.dp
): Modifier = liquidGlassElevated(shape = shape, cornerRadius = 84f)

@Suppress("UNUSED_PARAMETER")
fun Modifier.glassChrome(
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this  // No-op — chrome elements should be solid now

// liquidGlassChrome kept for any remaining references but simplified
fun Modifier.liquidGlassChrome(
    isTop: Boolean = true
): Modifier = this  // No-op — chrome should be solid
