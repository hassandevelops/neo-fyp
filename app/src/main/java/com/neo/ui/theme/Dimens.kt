package com.neo.ui.theme

import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// ─── NEO SPACING & ELEVATION ───────────────────────────────────────────────────
// Consistent spacing rhythm — generous padding, intentional negative space.
// Replaces ad-hoc padding numbers scattered across screens.
// ═══════════════════════════════════════════════════════════════════════════════

object NeoSpacing {
    val xs   = 4.dp    // tight icon/text gaps
    val sm   = 8.dp    // small gaps
    val md   = 12.dp   // default inner gap
    val lg   = 16.dp   // standard screen/edge padding
    val xl   = 20.dp   // card inner padding
    val xxl  = 24.dp   // generous card padding / block gaps
    val section = 32.dp // gap between major sections
}

object NeoElevation {
    val flat     = 0.dp
    val low      = 4.dp   // resting content card
    val medium    = 8.dp   // raised / interactive card
    val high     = 16.dp  // floating panels
    val modal    = 24.dp  // sheets, dialogs
}
