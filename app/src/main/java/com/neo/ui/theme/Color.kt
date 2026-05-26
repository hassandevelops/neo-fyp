package com.neo.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Core Brand Colors ────────────────────────────────────────────────────────
// Unified premium green — one accent everywhere
val NeoLime   = Color(0xFFB8E600)  // Slightly muted from CCFF00 for premium feel
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
// ─── LIQUID GLASS DESIGN SYSTEM ──────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Glass Surface Fills ─────────────────────────────────────────────────────
// Multi-tonal glass: slight warm tint prevents "cheap clear plastic" look
val GlassWhite4   = Color(0x0AFFFFFF)  // Ambient glass fill (large areas)
val GlassWhite8   = Color(0x14FFFFFF)  // Standard glass fill
val GlassWhite12  = Color(0x1FFFFFFF)  // Elevated glass fill
val GlassWhite16  = Color(0x29FFFFFF)  // Interactive hover state
val GlassWhite20  = Color(0x33FFFFFF)  // Pressed state / strong glass
val GlassWhite25  = Color(0x40FFFFFF)  // Maximum glass density

// Warm glass tints — adds realism (glass is never perfectly neutral)
val GlassWarm     = Color(0x0AFFE8D6)  // Subtle warm undertone
val GlassCool     = Color(0x0AD6E8FF)  // Subtle cool undertone

// ─── Glass Borders ───────────────────────────────────────────────────────────
// Top-light / bottom-dark simulates directional lighting on glass edges
val GlassBorderLight  = Color(0x40FFFFFF)  // Top edge — bright highlight (25%)
val GlassBorderMid    = Color(0x26FFFFFF)  // Side edges (15%)
val GlassBorderSubtle = Color(0x1AFFFFFF)  // Bottom edge — shadow side (10%)
val GlassBorderGlow   = Color(0x4DB8E600)  // Active state glow (NeoLime 30%)

// ─── Glass Reflections & Highlights ──────────────────────────────────────────
// These create the "light catching glass" illusion
val GlassReflectionTop     = Color(0x26FFFFFF)  // Top specular band (15%)
val GlassReflectionSoft    = Color(0x0DFFFFFF)  // Diffuse body reflection (5%)
val GlassReflectionEdge    = Color(0x33FFFFFF)  // Sharp edge catch light (20%)
val GlassHighlightCenter   = Color(0x0DFFFFFF)  // Soft center glow (5%)

// ─── Glass Shadows & Depth ──────────────────────────────────────────────────
val GlassShadow       = Color(0x66000000)  // Drop shadow (40%) — stronger depth
val GlassShadowSoft   = Color(0x33000000)  // Subtle ambient shadow (20%)
val GlassInnerGlow    = Color(0x1AFFFFFF)  // Inner top highlight (10%)
val GlassInnerShadow  = Color(0x14000000)  // Inner bottom shadow for 3D feel (8%)

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
