package com.neo.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Core Brand Colors ────────────────────────────────────────────────────────
// Unified premium green — one accent everywhere
val NeoLime   = Color(0xFFB8E600)  // Slightly muted from CCFF00 for premium feel
val NeoLimeDark = Color(0xFF9FCC00)  // Darker lime — bubble texture / pressed depth on lime cards
val NeoGreen  = Color(0xFF39FF14)  // Live indicators only
val NeoRed    = Color(0xFFFF453A)  // Errors, alerts

// Brand gradient tokens (Purple → Orange → Teal)
val NeoPurple = Color(0xFF8B5CF6)
val NeoOrange = Color(0xFFF97316)
val NeoTeal   = Color(0xFF14B8A6)

// ─── Backgrounds ─────────────────────────────────────────────────────────────
// Premium dark charcoal palette — NOT pure black
val NeoBlack      = Color(0xFF0C0C0E)  // Deep charcoal base — softer than #000
val NeoDarkGray   = Color(0xFF141416)  // Elevated background layer 1
val NeoGray900    = Color(0xFF1C1C1F)  // Card surface — warm charcoal
val NeoGray800    = Color(0xFF252528)  // Elevated card / hover
val NeoGray700    = Color(0xFF303033)  // Tertiary surface

val NeoCardTeal   = Color(0xFF1A2E2A)  // Teal-tinted card (BLE mesh stats)

// ─── Matte Surfaces (the "soft-block" system) ────────────────────────────────
// Solid charcoal tones layered over NeoBlack. Separation comes from depth
// (soft shadow) + spacing, NOT borders. These supersede the liquid-glass fills.
//   SurfaceElevated1/2/3 (defined below) are the canonical card tones.
val NeoHairline   = Color(0x0FFFFFFF)  // 6% white — optional subtle divider only

// ─── Organic Surface Pattern ─────────────────────────────────────────────────
// The global surface pattern (Modifier.organicPattern) derives its blob colours by
// tinting/shading each surface's own base colour at draw time — so no fixed tokens
// are needed here.

// ─── Light Mode ──────────────────────────────────────────────────────────────
val NeoLightBackground = Color(0xFFF5F5F5)
val NeoLightSurface    = Color(0xFFFFFFFF)

// ─── Transparent Surfaces (legacy compat) ────────────────────────────────────
val SurfaceWhite5  = Color(0x0DFFFFFF)
val SurfaceWhite10 = Color(0x1AFFFFFF)
val SurfaceWhite20 = Color(0x33FFFFFF)

// ─── Borders (legacy compat) ─────────────────────────────────────────────────
val BorderWhite5  = Color(0x0DFFFFFF)
val BorderWhite10 = Color(0x1AFFFFFF)
val BorderWhite20 = Color(0x33FFFFFF)

// ─── Text ────────────────────────────────────────────────────────────────────
val TextWhite   = Color(0xFFFFFFFF)
val TextWhite80 = Color(0xCCFFFFFF)
val TextWhite60 = Color(0x99FFFFFF)
val TextWhite40 = Color(0x66FFFFFF)
val TextWhite20 = Color(0x33FFFFFF)

// Light mode text
val TextBlack   = Color(0xFF000000)
val TextBlack80 = Color(0xCC000000)
val TextBlack60 = Color(0x99000000)
val TextBlack40 = Color(0x66000000)

// ═══════════════════════════════════════════════════════════════════════════════
// ─── MATTE SURFACE DEPTH ─────────────────────────────────────────────────────
// The matte "soft-block" system separates surfaces through soft shadow + spacing.
// These are the shadow colors used by neoSurface / neoSurfaceElevated.
// ═══════════════════════════════════════════════════════════════════════════════
val GlassShadow       = Color(0x66000000)  // Drop shadow (40%) — stronger depth
val GlassShadowSoft   = Color(0x33000000)  // Subtle ambient shadow (20%)

// ─── Accent Glows ────────────────────────────────────────────────────────────
val NeoLimeGlow10   = Color(0x1AB8E600)  // Subtle lime backdrop
val NeoLimeGlow20   = Color(0x33B8E600)  // Medium lime glow
val NeoLimeGlow30   = Color(0x4DB8E600)  // Strong lime glow
val NeoTealGlow     = Color(0x1A14B8A6)  // Teal accent glow
val NeoPurpleGlow   = Color(0x1A8B5CF6)  // Purple accent glow

// ─── Premium Surface Colors ─────────────────────────────────────────────────
// Layered surfaces for depth hierarchy (used beneath glass)
val SurfaceElevated1 = Color(0xFF18181B)  // First elevation layer
val SurfaceElevated2 = Color(0xFF202024)  // Second elevation layer
val SurfaceElevated3 = Color(0xFF28282D)  // Third elevation layer

// ─── Status ──────────────────────────────────────────────────────────────────
val StatusOnline  = Color(0xFF22C55E)
val StatusOffline = Color(0xFFEF4444)
val StatusWarning = Color(0xFFF59E0B)
