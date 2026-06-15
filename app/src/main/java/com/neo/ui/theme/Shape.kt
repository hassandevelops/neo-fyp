package com.neo.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NEO SHAPE SYSTEM ──────────────────────────────────────────────────────────
// Large, soft corner radii — the "soft-block" language. One source of truth so
// every surface shares the same rounded geometry. No sharp rectangles.
// ═══════════════════════════════════════════════════════════════════════════════

object NeoShapes {
    /** Fully-rounded pill — primary/secondary buttons, chips, tags, toggles. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** Small chip/tag — same as pill but expressed for clarity at call sites. */
    val chip: Shape = RoundedCornerShape(percent = 50)

    /** Inline control (search field, small input). */
    val control: Shape = RoundedCornerShape(16.dp)

    /** Standard content card. */
    val card: Shape = RoundedCornerShape(24.dp)

    /** Large / hero card. */
    val cardLarge: Shape = RoundedCornerShape(28.dp)

    /** Extra-large card — post cards (rounder on all four corners). */
    val cardXL: Shape = RoundedCornerShape(32.dp)

    /** Bento stat tile. */
    val tile: Shape = RoundedCornerShape(24.dp)

    /** Bottom sheet / modal — rounded top only. */
    val sheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    /** Circular — avatars, circular icon buttons, FAB. */
    val circle: Shape = CircleShape

    // Material3 Shapes — wired into NeoTheme so default Material components
    // (Button, Card, MenuItem, AlertDialog, ...) inherit large radii.
    val material = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small      = RoundedCornerShape(16.dp),
        medium     = RoundedCornerShape(20.dp),
        large      = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
}
