package com.neo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.theme.*

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NEO SOFT-BLOCK CONTROLS ───────────────────────────────────────────────────
// Reusable components encoding the reference's signature elements: lime pill CTAs,
// matte pills, chips/tags, bento stat tiles, circular accent buttons, and section
// headers. Shared everywhere so the visual language stays consistent.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Primary call-to-action — full lime pill with dark bold label.
 * Use for the single most important action on a surface (broadcast, save, next).
 */
@Composable
fun NeoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false
) {
    val container = if (enabled) NeoLime else NeoGray700
    val content = if (enabled) NeoBlack else TextWhite40
    val base = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .clip(NeoShapes.pill)
        .background(container)
        .organicPattern(container, richness = 2, intensity = 0.8f)
        .then(if (enabled) Modifier.neoPressable(onClick = onClick) else Modifier)
        .padding(horizontal = NeoSpacing.xxl, vertical = 14.dp)
    Row(
        modifier = base,
        horizontalArrangement = Arrangement.spacedBy(NeoSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(text = text, color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Secondary action — matte charcoal pill with white label.
 */
@Composable
fun NeoSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fillMaxWidth: Boolean = false
) {
    val base = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .clip(NeoShapes.pill)
        .background(SurfaceElevated2)
        .organicPattern(SurfaceElevated2, richness = 2, intensity = 0.8f)
        .neoPressable(onClick = onClick)
        .padding(horizontal = NeoSpacing.xxl, vertical = 14.dp)
    Row(
        modifier = base,
        horizontalArrangement = Arrangement.spacedBy(NeoSpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TextWhite, modifier = Modifier.size(20.dp))
        }
        Text(text = text, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Small rounded-pill chip / tag for metadata (peer counts, labels, categories).
 * Set [accent] for a lime-tinted variant that draws the eye.
 */
@Composable
fun NeoChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val container = if (accent) NeoLimeGlow20 else SurfaceElevated2
    val contentColor = if (accent) NeoLime else TextWhite80
    Row(
        modifier = modifier
            .clip(NeoShapes.chip)
            .background(container)
            .organicPattern(container, richness = 1, intensity = 0.7f)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = NeoSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(NeoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(text = text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bento stat tile — icon + value + label in a matte block. Compose these in a
 * grid/row for modular dashboards (BLE metrics, profile counts).
 */
@Composable
fun NeoStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val valueColor = if (accent) NeoLime else TextWhite
    NeoCard(
        modifier = modifier.then(
            if (onClick != null) Modifier.neoPressable(onClick = onClick) else Modifier
        ),
        shape = NeoShapes.tile,
        tone = SurfaceElevated1
    ) {
        Column(
            modifier = Modifier.padding(NeoSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(NeoSpacing.sm)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (accent) NeoLimeGlow20 else SurfaceElevated3),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (accent) NeoLime else TextWhite80,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(text = value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = TextWhite60, fontSize = 13.sp)
        }
    }
}

/**
 * Circular icon button — the reference's "cut-in" accent action.
 * Lime variant for primary actions; matte variant for neutral ones.
 */
@Composable
fun NeoCircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    accent: Boolean = true,
    size: Dp = 44.dp
) {
    val container = if (accent) NeoLime else SurfaceElevated2
    val tint = if (accent) NeoBlack else TextWhite
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .organicPattern(container, richness = 2, intensity = 0.8f)
            .neoPressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Consistent section header — title with optional trailing action.
 */
@Composable
fun NeoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                color = NeoLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onActionClick
                )
            )
        }
    }
}
