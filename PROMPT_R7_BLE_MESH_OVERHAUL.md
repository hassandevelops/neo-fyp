# Round 7: BLE Mesh Status Screen Overhaul

**Context:**
We need to completely replace the existing `BLEMeshStatusScreen.kt` with a new, highly visual isometric orbital animation layout based on the provided design mockup. 

**IMPORTANT RULE:** Ensure the project uses the older Material3 API `Divider` instead of `HorizontalDivider` if applicable. Also use `PathEffect.dashPathEffect` for drawing dashed lines on a `Canvas`.

---

## Instructions for OpenCode

Please rewrite `app/src/main/java/com/neo/ui/screens/BLEMeshStatusScreen.kt` from scratch according to these layout constraints:

### 1. Root Layout & Header
- Create a root `Box` with `NeoBlack` background and `fillMaxSize()`.
- Inside the box, use a `Column` with `horizontalAlignment = Alignment.CenterHorizontally`.
- **Top App Bar:** Create a custom `Row` (no `TopAppBar` component).
  - Left side: A `Row` containing a Cube icon (`Icons.Default.ViewInAr`) and bold "NEO" text. Add a `clickable` modifier to this row that calls `onBack()`.
  - Right side: A `Notifications` (bell) icon with `TextWhite60` tint.
- **Title:** Add a column containing "Neo Mesh Status" (28sp, Bold, White) and a subtitle "System Diagnostic & Topology" (14sp, Gray).

### 2. Isometric Orbital Canvas Animation
- Define an `infiniteTransition` to drive an `orbitProgress` float (from 0 to 2PI) and a `glowPulse` float (from 0.5f to 1f).
- Create a `Canvas` box of height `220.dp`.
- **Central Core:** Draw two circles at the center: an inner solid `NeoLime` circle and an outer glowing circle using `Brush.radialGradient` with `NeoLime` and varying opacity based on `glowPulse`.
- **Orbits:** Draw 3 elliptical paths (`drawOval`).
  - Use `withTransform` (translate to center, then `rotate` the canvas slightly to simulate an isometric angle, e.g., -15f, 10f, -5f).
  - Use `Stroke` with `PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)` for the dashed lines.
- **Satellites:** Calculate the `x` and `y` positions of small `NeoLime` dots moving along the paths using basic trig (`cos(angle)` and `sin(angle)` multiplied by the ellipse radii) and the `orbitProgress` value.

### 3. Pager Indicator
- Below the Canvas, add a `Row` containing 5 small circles (`6.dp` each). The first circle should be `NeoLime`, and the remaining 4 should be `TextWhite20`.

### 4. 2x2 Metrics Grid
- Create a generic `StatCard` composable function (at the bottom of the file) that accepts: `backgroundColor`, `icon`, `title`, `mainValue`, `subValue`, an optional `badgeText`, and a `showProgressBar` boolean.
- Use a `Column` containing two `Row`s to form a 2x2 grid of these cards (`weight(1f)` for each card).
  - **Card 1 (Top Left):** `NeoCardTeal` background, Network/Hub icon, "Optimal" badge. Title: "Active Nodes", mainValue: `connectedPeers.size.toString()`, subValue: "/ 32".
  - **Card 2 (Top Right):** `NeoGray900` background, BarChart icon. Title: "" (empty), mainValue: "1.2", subValue: " MB/s". Set `showProgressBar = true` to draw a small `NeoLime` bar.
  - **Card 3 (Bottom Left):** `NeoGray900` background, Pulse/GraphicEq icon. Title: "Avg Latency", mainValue: "1", subValue: "".
  - **Card 4 (Bottom Right):** `NeoGray900` background, Signal/Sensors icon. "Optimal" badge (brownish background with `NeoOrange` text). Title: "", mainValue: "12", subValue: " ms".

---
**Compile Check:** Run `./gradlew assembleDebug` after applying to ensure the `PathEffect.dashPathEffect` resolves correctly.
