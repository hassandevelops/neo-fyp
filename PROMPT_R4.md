# Neo FYP — OpenCode Implementation Prompt (Round 4)

**Context:** You are the primary developer for the Neo decentralized social application. We are in the final phase of development before FYP submission. The Antigravity architecture reviewer has audited your Round 3 implementation. While you successfully scoped the noise texture and fixed the FAB animations, the Round 3 audit revealed a Critical networking regression and several UI lifecycle/rendering failures that must be fixed immediately.

Your goal for this session is to execute the **Prioritised Work Queue for Round 4** precisely as instructed below. 

### ⚠️ STRICT RULES FOR THIS SESSION ⚠️
1. **Do not deviate from these exact instructions.** Read the "What to do" section for each task carefully.
2. **Do not assume a task is complete just because the project compiles.** You must verify the logic. For example, in Round 3, you placed `onDismiss()` in an `onClick` handler which instantly cancelled the screen's state before the snackbar could render.
3. **Do not refactor unrelated code.** Focus solely on these 4 tasks.

---

## The Prioritised Work Queue

### TASK-R4-01 — Restore Mesh Sync Networking (CRITICAL REGRESSION)
**Context:** In Round 3, you correctly removed the Clean Architecture violation by taking `SyncManager` out of `FeedViewModel`. However, you never injected `SyncManager` anywhere else. Because `SyncManager.setBluetoothService()` is now never called, the gossip network is completely dead and the app is isolated.
**Files to modify:** `MainActivity.kt`
**What to do:** 
1. Open `MainActivity.kt`.
2. Add `@Inject lateinit var syncManager: SyncManager` to the class properties.
3. Locate the `serviceConnection.onServiceConnected()` callback.
4. Immediately after `bluetoothService = binder.getService()`, add the line: `syncManager.setBluetoothService(binder.getService())` to restore mesh network capabilities.

### TASK-R4-02 — Fix Create Post Snackbar Feedback
**Context:** In Round 3, you added the `LaunchedEffect` for the snackbar, but you left `onDismiss()` inside the "Post" button's `onClick` lambda. Because `onDismiss()` navigates away instantly, the screen unmounts, cancelling the `LaunchedEffect` before the ViewModel can emit a success state, preventing the snackbar from ever showing.
**Files to modify:** `EnhancedCreatePostScreen.kt`
**What to do:** 
1. Open `EnhancedCreatePostScreen.kt` and locate the "Post" `Button` composable. 
2. In its `onClick` lambda, **delete** the line `onDismiss()`.
3. Locate the `LaunchedEffect(uiState)` block. Inside the `is CreatePostViewModel.UiState.Success` branch, add `onDismiss()` immediately *after* the `snackbarHostState.showSnackbar(message = state.message)` call. This ensures the user sees the feedback before the screen navigates away.

### TASK-R4-03 — Implement True Glassmorphism Backdrop
**Context:** In Round 3, you applied `Modifier.blur()` to the root `Box` that wraps the entire header. In Compose, this blurs the component itself, rendering the "NEXUS" text and icons illegible. We need the header to be sharp, but the content *behind* it to be blurred.
**Files to modify:** `EnhancedHeader.kt`
**What to do:** 
1. Remove `Modifier.blur()` and the alpha transparency from the root `Box` modifier.
2. Restructure the header to use a root `Box` with two children: an empty backdrop container, and the content `Row`.
3. Use this exact structure:
```kotlin
Box(modifier = modifier.fillMaxWidth()) {
    // 1. The frosted glass backdrop (must use RenderEffect for true backdrop blur on SDK 31+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(modifier = Modifier
            .matchParentSize()
            .graphicsLayer { 
                renderEffect = android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.CLAMP) 
                clip = true
            }
            .background(NeoBlack.copy(alpha = 0.4f))
        )
    } else {
        Box(modifier = Modifier.matchParentSize().background(NeoBlack.copy(alpha = 0.85f)))
    }
    
    // 2. The perfectly sharp content sitting on top
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // ... put the existing NEXUS text and icons here ...
    }
}
```

### TASK-R4-04 — Implement Neon Shadow on PostCard
**Context:** In Round 3, you used a standard `Modifier.shadow()` with higher opacity. A standard material shadow casts a flat directional drop-shadow. The PRD requires a vibrant, multi-colored, glowing neon aura around the card.
**Files to modify:** `EnhancedPostCard.kt`
**What to do:** 
1. Remove the `.shadow()` modifier entirely from the `Card` component.
2. Wrap the card's internal content `Box` with an outer `Box`.
3. Give this new outer `Box` a `Modifier.matchParentSize()`.
4. Apply a `Brush.radialGradient` background to the outer Box using `NeoPurple` and `NeoOrange`, and then apply `Modifier.blur(24.dp)` to it so it spreads outwards like a glow.
5. Place this glowing Box directly behind the main content Box inside the Card.

---
**Final Instruction:** Once you have completed these 4 tasks, verify that the project still builds using `./gradlew compileDebugKotlin`. End your session and report back.
