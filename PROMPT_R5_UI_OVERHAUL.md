# Neo FYP — UI Overhaul & Crash Fix Prompt (Round 5)
### For: OpenCode

---

## YOUR ROLE
You are the **Lead Android Developer (OpenCode)**. The architecture reviewer (Antigravity) and the product owner have determined that the current UI is causing critical Out-Of-Memory (OOM) crashes and does not meet the desired visual aesthetic. 

Your objective in this round is to execute a complete **UI Theme Overhaul** based on new reference designs, and to immediately fix the background rendering crash.

---

## CONTEXT & CRITICAL ISSUES

1. **The Crash (OOM / ANR):** The app is crashing or freezing because `NoiseOverlay` is attempting to draw hundreds of thousands of circles on a `Canvas` during every recomposition. This component is active inside `GradientBackground.kt` and `EnhancedCreatePostScreen.kt`. It must be deleted.
2. **The "Bubbles":** The user has explicitly rejected the gradient "orbs" (bubbles) behind the screens. They want a **flat, solid background** (pure black for dark mode, clean white/light grey for light mode).
3. **The Color Palette:** The current Purple/Cyan gradient theme has been rejected. The new design language is a minimalist dark/light theme with sharp, high-contrast **Neon Green** and **Neon Red** accents.

---

## TASK LIST (EXECUTE IN ORDER)

### TASK-R5-01: Eradicate the Background Crash
**Severity: CRITICAL**
**Files to modify:** `GradientBackground.kt`, `EnhancedCreatePostScreen.kt`

1. Open `GradientBackground.kt`.
2. Delete the `NoiseOverlay` composable function entirely. 
3. Remove the three "gradient orb" `Box` elements from `GradientBackground`. 
4. The `GradientBackground` component should now just be a simple wrapper that uses `Modifier.background(MaterialTheme.colorScheme.background)`.
5. Open `EnhancedCreatePostScreen.kt` and delete the call to `NoiseOverlay()` at the bottom of the root Box.

### TASK-R5-02: Overhaul the Core Theme Palette
**Severity: HIGH**
**Files to modify:** `Color.kt`, `Theme.kt`

1. Open `Color.kt`. Replace the existing brand colors with the new Minimalist Neon palette:
   - `NeoGreen` = `Color(0xFF4ADE80)` (for primary actions / "your message" bubbles)
   - `NeoLime` = `Color(0xFFCCFF00)` (for high-contrast headers/badges)
   - `NeoRed` = `Color(0xFFFF453A)` (for declines / destructive actions / alerts)
   - `NeoDarkGray` = `Color(0xFF111111)`
   - `NeoLightSurface` = `Color(0xFFFFFFFF)`
   - `NeoLightBackground` = `Color(0xFFF5F5F5)`
   *(You may retain necessary grey scales, but delete unused brights like NeoPink/NeoCyan).*
2. Open `Theme.kt`. 
   - Update `DarkColorScheme` to use pure Black or `NeoDarkGray` for backgrounds. Map `primary` to `NeoGreen` or `NeoLime`.
   - Update `LightColorScheme` to use `NeoLightBackground` for backgrounds and `NeoLightSurface` for cards/surfaces.
   - Ensure text colors contrast properly for both modes.

### TASK-R5-03: Redesign the Post Card (Minimalist UI)
**Severity: HIGH**
**Files to modify:** `EnhancedPostCard.kt`

1. Remove the background `Box` that uses `Brush.radialGradient` (the fake neon glow you added in R4).
2. The card should be a sleek, solid, rounded rectangle using `MaterialTheme.colorScheme.surface` or a dark grey color (`#1C1C1E` for dark mode). 
3. Ensure corner radius is prominent (e.g., `24.dp`).
4. Update the interactable buttons (Like, Comment) to use the new `NeoGreen` or `NeoRed` colors for their active states, rather than the old orange/purple.

### TASK-R5-04: Simplify the Header
**Severity: MEDIUM**
**Files to modify:** `EnhancedHeader.kt`

1. The header should no longer rely on heavy blur overlays to obscure the ugly background bubbles, because the background is now solid.
2. Remove the `.blur()` modifier completely. 
3. Make the header background slightly transparent if you wish, or just match the solid app background color.
4. Apply the new `NeoLime` or `NeoGreen` to badges or profile borders instead of the old gradient.

---

## RULES OF ENGAGEMENT
- **Do NOT run `compileDebugKotlin` until all files are updated**, as deleting `NoiseOverlay` and changing `Color.kt` properties will temporarily break references in other files.
- **Support Both Modes:** Ensure your changes gracefully map to `isSystemInDarkTheme()`.
- **Stay Minimal:** Do not add new complex drawing operations. The user explicitly wants a clean, flat, minimalist UI like Tinder or modern social apps.
