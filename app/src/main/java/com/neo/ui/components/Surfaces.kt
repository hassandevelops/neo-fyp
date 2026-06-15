package com.neo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.neo.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NEO MATTE SURFACE SYSTEM ──────────────────────────────────────────────────
// Solid charcoal cards layered over the near-black background. Separation comes
// from soft drop shadow + spacing — NOT translucent fills or bright borders.
// This replaces the previous "liquid glass" system.
//
// Tones (from Color.kt): SurfaceElevated1 #18181B · 2 #202024 · 3 #28282D
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Matte content surface — solid fill + soft ambient/spot shadow.
 * The default building block for cards, tiles, panels, and grouped rows.
 */
fun Modifier.neoSurface(
    shape: Shape = NeoShapes.card,
    tone: Color = SurfaceElevated1,
    elevation: Dp = NeoElevation.low
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = GlassShadowSoft,
        spotColor = GlassShadow
    )
    .clip(shape)
    .background(tone, shape)

/**
 * Higher-elevation matte surface for modals, sheets, and floating panels.
 * Slightly lighter tone + deeper soft shadow to read as "above" content.
 */
fun Modifier.neoSurfaceElevated(
    shape: Shape = NeoShapes.cardLarge,
    tone: Color = SurfaceElevated2,
    elevation: Dp = NeoElevation.modal
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = GlassShadow,
        spotColor = GlassShadow
    )
    .clip(shape)
    .background(tone, shape)

/**
 * Tactile press feedback — subtle scale-down on press, bouncy spring release.
 * Reusable across any pressable surface for premium micro-interaction feel.
 */
@Composable
fun Modifier.neoPressable(
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "neo_press_scale"
    )
    return this
        .graphicsLayer(scaleX = scale, scaleY = scale)
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

// ── Matte Surface Composables ──────────────────────────────────────────────────

// ═══════════════════════════════════════════════════════════════════════════════
// ─── GLOBAL ORGANIC SURFACE PATTERN ────────────────────────────────────────────
// Oversized, soft, overlapping blobs drawn in tonal variations of the surface's OWN
// colour (lighter tints + darker shades only — no new hues). Reads as depth that is
// "lit from within" rather than a decorative overlay; no transparency/frosted glass.
// Single drawBehind pass (radial gradients = soft edges, no expensive blur). Apply
// AFTER clip/background so blobs are masked to the surface shape; several are placed
// off-edge so they read as flowing past the boundary.
//
// NOT for post cards / long-form reading surfaces.
// ═══════════════════════════════════════════════════════════════════════════════

private enum class BlobTone { LIGHT, DARK, AMBIENT }
private class OrganicBlob(
    val cx: Float, val cy: Float, val r: Float, val tone: BlobTone, val alpha: Float
)

// Asymmetric, varied-scale, partly off-edge. Alpha tiers (primary ~10-12% ·
// secondary ~12-15% · ambient highlights ~7-9%) are paired with STRONG tonal
// separation below — on a near-black surface a weak tint is mathematically
// invisible, so the lift has to come from clearly lighter/darker blob colours.
private val OrganicBlobs = listOf(
    OrganicBlob(0.96f, 0.04f, 0.78f, BlobTone.LIGHT,   0.12f),  // top-right, oversized, off-edge
    OrganicBlob(0.04f, 0.98f, 0.66f, BlobTone.DARK,    0.15f),  // bottom-left, off-edge
    OrganicBlob(0.86f, 0.92f, 0.50f, BlobTone.LIGHT,   0.09f),  // bottom-right
    OrganicBlob(0.20f, 0.16f, 0.38f, BlobTone.AMBIENT, 0.08f),  // upper-left highlight
    OrganicBlob(1.08f, 0.48f, 0.52f, BlobTone.LIGHT,   0.10f),  // right edge, off-edge
    OrganicBlob(0.46f, 1.10f, 0.46f, BlobTone.DARK,    0.13f),  // bottom-centre, off-edge
    OrganicBlob(-0.08f, 0.30f, 0.44f, BlobTone.AMBIENT, 0.07f), // left edge, off-edge
    OrganicBlob(0.62f, 0.40f, 0.26f, BlobTone.LIGHT,   0.06f),  // ambient mid
)

/**
 * Global organic surface pattern. Derives a lighter tint and darker shade from
 * [baseColor] and lays down [richness] overlapping soft blobs for premium depth.
 *
 * @param baseColor the surface's own fill colour (tints/shades derived from it).
 * @param richness  number of blobs — cards: 4-6 (rich); buttons/chips: 1-2 (subtle).
 * @param intensity opacity multiplier — 1f for cards, ~0.7-0.8f for controls.
 * @param seed      rotates the blob layout so neighbouring components don't repeat.
 */
fun Modifier.organicPattern(
    baseColor: Color,
    richness: Int = 5,
    intensity: Float = 1f,
    seed: Int = 0,
): Modifier = this.drawBehind {
    // Strong tonal separation so the blobs actually register. Dark surfaces have
    // little headroom toward black, so the dark shade is biased less than the tint.
    val isDark = baseColor.luminance() < 0.5f
    val lighter = lerp(baseColor, Color.White, if (isDark) 0.55f else 0.30f)
    val ambient = lerp(baseColor, Color.White, if (isDark) 0.72f else 0.45f)
    val darker = lerp(baseColor, Color.Black, if (isDark) 0.40f else 0.28f)
    val w = size.width
    val h = size.height
    val maxR = maxOf(w, h)
    val n = richness.coerceIn(1, OrganicBlobs.size)
    for (i in 0 until n) {
        val b = OrganicBlobs[(i + seed) % OrganicBlobs.size]
        val color = when (b.tone) {
            BlobTone.LIGHT -> lighter
            BlobTone.DARK -> darker
            BlobTone.AMBIENT -> ambient
        }
        val a = (b.alpha * intensity).coerceIn(0f, 1f)
        val center = Offset(b.cx * w, b.cy * h)
        val radius = b.r * maxR
        // Soft radial falloff → no hard edge, blends smoothly.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to color.copy(alpha = a),
                    0.55f to color.copy(alpha = a * 0.55f),
                    1.0f to Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

/**
 * Matte content card. Standard elevated charcoal block.
 * [textured] adds a very subtle white bubble texture for premium organic depth.
 */
@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.card,
    tone: Color = SurfaceElevated1,
    elevation: Dp = NeoElevation.low,
    textured: Boolean = true,
    patternSeed: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .neoSurface(shape = shape, tone = tone, elevation = elevation)
            .then(if (textured) Modifier.organicPattern(tone, richness = 5, seed = patternSeed) else Modifier),
        content = content
    )
}

/**
 * Lime accent card — bright lime surface with a subtle darker-lime bubble texture.
 * Used selectively for primary actions / highlighted content. Content should use
 * NeoBlack for contrast.
 */
@Composable
fun NeoLimeCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.card,
    elevation: Dp = NeoElevation.medium,
    textured: Boolean = true,
    patternSeed: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .neoSurface(shape = shape, tone = NeoLime, elevation = elevation)
            .then(if (textured) Modifier.organicPattern(NeoLime, richness = 5, seed = patternSeed) else Modifier),
        content = content
    )
}

/**
 * Higher-elevation matte surface for hero/floating blocks.
 */
@Composable
fun NeoElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = NeoShapes.cardLarge,
    tone: Color = SurfaceElevated2,
    elevation: Dp = NeoElevation.high,
    textured: Boolean = true,
    patternSeed: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .neoSurfaceElevated(shape = shape, tone = tone, elevation = elevation)
            .then(if (textured) Modifier.organicPattern(tone, richness = 6, seed = patternSeed) else Modifier),
        content = content
    )
}
