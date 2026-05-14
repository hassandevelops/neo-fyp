# Round 6: Synthwave / Cyberpunk UI Overhaul

**Context:**
We need to align the Android Compose app with the final React/Vite design prototypes. The new design uses a pure black background, a single primary neon lime accent (`#CCFF00`), full-bleed images in cards, glassmorphic modals, and simplified bottom navigation with a central FAB.

**Strict Design Rules:**
1. Backgrounds MUST be pure black (`#000000`).
2. Primary accent color MUST be NeoLime (`#CCFF00`).
3. Use `NeoGreen` (`#39FF14`) for live indicators.
4. Remove heavy gradients from backgrounds; use them only for subtle blurs or the `BLEMeshStatusScreen`.

---

## Instructions for OpenCode

### 1. Update `com.neo.ui.theme.Color.kt` & `Theme.kt`
- Redefine `NeoLime` as `Color(0xFFCCFF00)`.
- Define `NeoBlack` as `Color(0xFF000000)`.
- Add `NeoGreen` as `Color(0xFF39FF14)` and `NeoCardTeal` as `Color(0xFF1A2E2A)`.
- In `Theme.kt`, map `primary` to `NeoLime`, `background` to `NeoBlack`, `surface` to `NeoGray900` (`#1A1A1A`), and lock the status bar to `NeoBlack`.

### 2. Redesign `EnhancedPostCard.kt`
- Make the card full-bleed. The image should fill the width and have a height of `220.dp` to `380.dp` with `ContentScale.Crop`.
- Add a vertical gradient scrim (transparent to black 75%) over the bottom of the image.
- Move the author name, avatar, and like/comment row into the bottom scrim area.
- Add a "LIVE" badge (green dot + text) in the top-left corner if `isLive` is true.

### 3. Simplify `EnhancedHeader.kt`
- Set the `Surface` color to `NeoBlack`.
- Left side: Circular avatar placeholder with a `NeoLime` border ring + bold "Neo" wordmark text.
- Right side: Bell icon. Remove the search icon from here.

### 4. Overhaul `EnhancedBottomNavigation.kt` & Feed FAB
- Change the background to pure black.
- Use 4 icon-only tabs (no text labels): Home, Search, Bell, Profile.
- Add a center circular FAB (Floating Action Button). It should be `NeoLime` with an animated outer glow (`shadowElevation`).
- In `EnhancedFeedScreen.kt`, delete the old custom FAB overlay Box from the bottom right, and remove its 80dp spacer.
- In `EnhancedNeoNavigation.kt`, pass `onFabClick = { navController.navigate("create_post") }` to the `EnhancedBottomNavigation`.

### 5. Redesign `ProfileScreen.kt`
- Replace the avatar image/icon with a solid `NeoLime` circular ring border (`3.dp`).
- Replace the multi-card stats row with a single dark card containing 3 columns: PULSES, NODES, FOLLOWERS, separated by a `Divider`.
- Change the "Edit Profile" button to read "Establish Link", make it a fully rounded pill (`50.dp`), and fill it with solid `NeoLime`.

### 6. Redesign `EnhancedCreatePostScreen.kt` (New Post Modal)
- Structure this as a glassmorphic bottom sheet: Background should be black with `0.7f` alpha.
- Inside the sheet, add a `Canvas` with two large blurry circles (one dark brown/orange, one dark teal) behind a dark translucent background.
- Replace the form with a simpler layout: top row with "×" and "New Post", a row with author avatar + "Public visibility", and an `OutlinedTextField` with the placeholder "What's pulsating?".
- Add a full-width, pill-shaped `NeoLime` button at the bottom that says "Post to Neo ▷". Replace `HorizontalDivider` with `Divider` to maintain compatibility with older Compose libraries.

### 7. Simplify `StoryRow.kt`
- Remove the rainbow `sweepGradient`.
- Use a solid `NeoLime` border ring for stories with content, and a solid gray border (`BorderWhite10`) for empty stories.
- Update the "Add Story" button to use a solid `NeoLime` background with a black `+` icon.

---
**Compile Check:** Run `./gradlew assembleDebug` after applying to ensure no API compatibility errors (e.g., using `Divider` instead of `HorizontalDivider`).
