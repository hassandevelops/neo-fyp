package com.neo.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Rounded-rectangle card outline whose **top-right corner** is replaced by a
 * circular **concave notch**, so a circular avatar nests diagonally into the
 * corner and the boundary curves smoothly inward around it.
 *
 * The notch circle (radius [notchRadius]) is centred [notchInset] from both the top
 * and right edges. The card edge flows: top edge → convex fillet → concave
 * avatar-arc → convex fillet → right edge — every junction is a tangent transition,
 * so there are no sharp corners. The arc is part of a circle, so a concentric avatar
 * keeps a uniform gap. Everything is anchored to the top-right corner → responsive.
 */
class NotchedCardShape(
    private val cornerRadius: Dp = 24.dp,
    private val notchRadius: Dp = 34.dp,
    private val notchInset: Dp = 24.dp,
    private val filletRadius: Dp = 12.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        with(density) {
            val w = size.width
            val h = size.height
            val cr = cornerRadius.toPx()
            val r = notchRadius.toPx()
            val rc = filletRadius.toPx()
            val inset = notchInset.toPx()
            val cx = w - inset          // notch centre (symmetric about the corner diagonal)
            val cy = inset

            // Offset (along top edge / down right edge) from the notch centre to each
            // convex fillet's line-tangent point (external tangency of fillet + notch).
            val dSq = (r + rc) * (r + rc) - (rc - cy) * (rc - cy)
            val sq = if (dSq > 0f) sqrt(dSq) else -1f
            val fxL = cx - sq            // left fillet meets the top edge here
            val fyB = cy + sq            // bottom fillet meets the right edge here

            // Fall back to a plain rounded rect if the notch can't fit cleanly.
            if (sq <= 0f || fxL <= cr || fyB >= h - cr) {
                return Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(cr, cr)))
            }

            fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()
            val filletSweep = deg(atan2(cy - rc, sq)) + 90f
            val gTop = deg(atan2(rc - cy, -sq))           // notch arc start (near top edge)
            val gRight = deg(atan2(sq, cy - rc))          // notch arc end (near right edge)
            var notchSweep = gRight - gTop                // go the interior (short) way
            if (notchSweep > 180f) notchSweep -= 360f
            if (notchSweep < -180f) notchSweep += 360f
            val bottomFilletStart = deg(atan2(-sq, rc - cy))

            val path = Path().apply {
                moveTo(cr, 0f)
                lineTo(fxL, 0f)
                // top edge → concave arc (convex fillet)
                arcTo(Rect(fxL - rc, 0f, fxL + rc, 2 * rc), -90f, filletSweep, false)
                // concave avatar arc across the corner
                arcTo(Rect(cx - r, cy - r, cx + r, cy + r), gTop, notchSweep, false)
                // concave arc → right edge (convex fillet)
                arcTo(Rect(w - 2 * rc, fyB - rc, w, fyB + rc), bottomFilletStart, filletSweep, false)
                // right edge + bottom-right corner
                lineTo(w, h - cr)
                arcTo(Rect(w - 2 * cr, h - 2 * cr, w, h), 0f, 90f, false)
                // bottom edge + bottom-left corner
                lineTo(cr, h)
                arcTo(Rect(0f, h - 2 * cr, 2 * cr, h), 90f, 90f, false)
                // left edge + top-left corner
                lineTo(0f, cr)
                arcTo(Rect(0f, 0f, 2 * cr, 2 * cr), 180f, 90f, false)
                close()
            }
            return Outline.Generic(path)
        }
    }
}
