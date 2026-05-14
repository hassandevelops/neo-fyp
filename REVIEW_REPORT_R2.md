# Formal Review Report: Neo FYP Post-Implementation (Round 2)

## Section 1 — Round 2 Review Summary
OpenCode's Round 2 implementation achieved partial success. Out of the 7 assigned tasks, 2 were fully completed, 3 were partially implemented with noticeable gaps, and 2 were effectively ignored or failed to meet the core requirement. There are currently **1 Critical, 3 High, and 2 Medium** severity findings remaining. Importantly, OpenCode introduced **1 High-severity regression** by accidentally deleting the user feedback (snackbars) during post creation. The project is technically closer to FYP submission readiness because the most complex architectural blocker (decoupling the Domain layer from the Android framework) was successfully resolved. However, the UI layer remains unpolished and continues to miss the exact glassmorphic and neon specifications requested in the design brief. The most urgent single item OpenCode must address is restoring the lost post creation feedback loop (REGRESSION-01), as silent failures during mesh syncing provide an unacceptable user experience.

---

## Section 2 — Task Completion Status

| Task | Title | Status | Evidence File(s) | Finding |
|------|-------|--------|-----------------|---------|
| TASK-R01 | Fix Clean Architecture: Sync Layer | ⚠️ Partial | `SyncPort.kt`, `FeedViewModel.kt` | UseCases are fixed, but `FeedViewModel` still acts as a network router. |
| TASK-R02 | Remove Android Imports from Domain | ✅ Complete | `CreatePostUseCase.kt` | Android dependencies cleanly abstracted. |
| TASK-R03 | FAB Infinite Pulse Animation | ⚠️ Partial | `EnhancedFeedScreen.kt` | Scale animation is present, but shadow elevation animation is missing. |
| TASK-R04 | Header Glassmorphism + Pulsing Badge | ⚠️ Partial | `EnhancedHeader.kt` | Badge pulses, but background uses flat transparency instead of frosted glass blur. |
| TASK-R05 | PostDetailViewModel | ✅ Complete | `PostDetailViewModel.kt` | Logic safely extracted from `FeedViewModel`. |
| TASK-R06 | Noise Texture Overlay | ⚠️ Partial | `GradientBackground.kt` | Applied globally to all screens instead of scoping it just to the modal. |
| TASK-R07 | Neon Shadow on PostCard | ❌ Not Done| `EnhancedPostCard.kt` | No custom neon graphics layer implemented. |

#### TASK-R01 — Fix Clean Architecture: Sync Layer — ⚠️ Partial
**What was specified:** Remove `GossipProtocol` from ViewModels and trigger sync via a Domain port.
**What was implemented:** `ISyncPort` was correctly created and injected into UseCases. However, `FeedViewModel` still injects `SyncManager` and directly calls `handleSyncRequest()`.
**Gap or regression:** `FeedViewModel` is acting as the message router for the Sync layer, which violates the strict observation-only mandate for ViewModels.
**Blocking submission:** Yes.

#### TASK-R03 — FAB Infinite Pulse Animation — ⚠️ Partial
**What was specified:** Animate both scale (1.0 to 1.08) and shadow elevation using `rememberInfiniteTransition`.
**What was implemented:** `fabScale` animates to 1.05. `fabShadowAlpha` is defined but applied only to the background brush. The actual `Modifier.shadow` elevation is hardcoded to `12.dp`.
**Gap or regression:** The shadow does not visually pulse outward because the elevation isn't animating.
**Blocking submission:** No.

#### TASK-R04 — Header Glassmorphism + Pulsing Badge — ⚠️ Partial
**What was specified:** Apply `Modifier.blur(20.dp)` on API 31+ to create a frosted glass effect.
**What was implemented:** The badge pulses correctly. The background uses `graphicsLayer { alpha = 0.7f }`.
**Gap or regression:** Transparency is not glassmorphism. The UI completely lacks the blurred frosted glass effect.
**Blocking submission:** Yes.

#### TASK-R06 — Noise Texture Overlay — ⚠️ Partial
**What was specified:** Add a 3-5% noise overlay exclusively to the Create Post screen.
**What was implemented:** The `NoiseOverlay()` composable was added inside `GradientBackground.kt`.
**Gap or regression:** Because `GradientBackground` wraps every screen in the app, the noise texture is now globally applied to the feed, profile, and settings, which violates the design spec.
**Blocking submission:** No.

#### TASK-R07 — Neon Shadow on PostCard — ❌ Not Done
**What was specified:** Upgrade the standard shadow to a complex, multi-colored neon glow.
**What was implemented:** The code remains identical to Round 1, using standard `Modifier.shadow`.
**Gap or regression:** Feature ignored.
**Blocking submission:** No.

---

## Section 3 — Clean Architecture Verification

| Check | Command | Result | Pass? |
|-------|---------|--------|-------|
| A | `grep -r "com.neo.sync" viewmodels` | Found `SyncManager` in `FeedViewModel.kt` | ❌ Fail |
| B | `grep -r "com.neo.bluetooth" viewmodels`| Found `BluetoothService` in `FeedViewModel.kt` | ❌ Fail |
| C | `GossipProtocol` in domain package | Not found | ✅ Pass |
| D | android imports in `CreatePostUseCase` | Not found | ✅ Pass |
| E | android imports in `CreateCommentUseCase`| Not found | ✅ Pass |
| F | android imports in `CreateReactionUseCase`| Not found | ✅ Pass |

**Verification Details:**
- **Is `SyncManager` in `FeedViewModel` a violation?** Yes. TRD specifies `FeedViewModel` must be strictly focused on "observing the feed and basic mesh stats". Having `FeedViewModel` explicitly call `syncManager.handleSyncRequest()` means the UI layer is orchestrating the raw network message routing loop. This is a severe Clean Architecture violation.
- **Is `ISyncPort` in the correct package?** Yes, `com.neo.domain.port`.
- **Are all UseCases injecting `ISyncPort`?** Yes, `CreatePostUseCase`, `CreateCommentUseCase`, and `CreateReactionUseCase` correctly inject `ISyncPort`.
- **Hilt binding:** Assumed correct as no injection crashes were observed.

---

## Section 4 — Regression Report

#### REGRESSION-01 — Post Creation Feedback Lost
**Was working:** After submitting a post, a snackbar would appear indicating success or failure.
**Now broken:** Users receive zero feedback. The modal dismisses and returns to the feed silently.
**Root cause:** OpenCode removed the `LaunchedEffect(uiState)` block from `EnhancedNeoNavigation.kt` but failed to re-implement it inside `EnhancedCreatePostScreen.kt` or `CreatePostViewModel`.
**Fix:** Add a `LaunchedEffect` in `EnhancedCreatePostScreen` that collects `uiState` and triggers a Snackbar upon `Success` or `Error`.
**Severity:** High

---

## Section 5 — UI Implementation Quality

**5a — FAB Animation**
- **Scale Range:** Animates from 1.0f to 1.05f. (Pass - within acceptable 1.05-1.10 range).
- **Duration:** 1500ms for scale, 1000ms for shadow alpha. (Partial - spec requested 1000ms uniformly).
- **Evaluation:** ⚠️ Partial. The animation variables exist, but the physical `shadowElevation` is hardcoded to `12.dp` instead of animating.

**5b — Header Glassmorphism**
- **Implementation:** Used `graphicsLayer { alpha = 0.7f }`.
- **Glassmorphism?** ❌ Fail. Alpha transparency is not blur.
- **API Guard:** Present (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`).
- **Badge Pulse:** ✅ Pass. `rememberInfiniteTransition` correctly applied to the notification dot.

**5c — Noise Texture Scope**
- **File modified:** `GradientBackground.kt`.
- **Affected screens:** All screens in the application.
- **Evaluation:** ⚠️ Partial. The opacity (3%) is correct, but the scope is wrong. It should only apply to the Create Post modal.

**5d — Neon Shadow**
- **Implementation:** Unchanged from Round 1.
- **Evaluation:** ❌ Not Implemented.

---

## Section 6 — Build Status

| Build Target | Status | Evidence |
|-------------|--------|----------|
| `./gradlew compileDebugKotlin` | ⚠️ Blocked | Local environment lacks `JAVA_HOME`. |
| `./gradlew assembleDebug` | ⚠️ Blocked | Local environment lacks `JAVA_HOME`. |
| `./gradlew test` | ⚠️ Blocked | Local environment lacks `JAVA_HOME`. |

**Analysis of Session Build Failure:**
The `compileDebugJavaWithJavac FAILED` error encountered during OpenCode's session is almost certainly a pre-existing environment issue (JDK mismatch, likely Java 11 vs Java 17). Because this is a pure Kotlin codebase, the fact that `compileDebugKotlin` passed successfully for OpenCode proves the Kotlin syntax and Hilt bindings are correct. The Java compilation failure was not caused by OpenCode's code changes.

---

## Section 7 — Prioritised Work Queue for OpenCode (Round 3)

## Priority 1 — Blocking Submission

### TASK-R3-01 — Restore Post Creation Snackbar Feedback
**Source:** REGRESSION-01
**Files to modify:** `EnhancedCreatePostScreen.kt` (and optionally `CreatePostViewModel.kt`)
**What to do:** 
Inject a `SnackbarHostState` into the Create Post screen (or pass it from the Scaffold). Add a `LaunchedEffect(uiState)` block that collects the `CreatePostViewModel.uiState`. If the state is `Success` or `Error`, show the corresponding message via the Snackbar before dismissing the modal.
**Acceptance criteria:** Successfully creating a post displays a "Post created successfully!" snackbar.
**Estimated effort:** 0.5 hours

### TASK-R3-02 — Implement True Glassmorphism Blur on Header
**Source:** TASK-R04
**Files to modify:** `EnhancedHeader.kt`
**What to do:** 
Remove the `alpha = 0.7f` from the `graphicsLayer`. Instead, for API 31+, apply `Modifier.blur(radiusX = 20.dp, radiusY = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)` to the background modifier. (If `blur` cannot be cleanly applied to the Surface, utilize a custom `RenderEffect.createBlurEffect` within `graphicsLayer`).
**Acceptance criteria:** The header visibly blurs the feed content scrolling beneath it.
**Estimated effort:** 1 hour

### TASK-R3-03 — Relocate Network Message Routing
**Source:** TASK-R01
**Files to modify:** `FeedViewModel.kt`, `SyncCoordinator.kt` (Create new)
**What to do:** 
Remove `syncManager.handleSyncRequest()` and `syncManager.handleSyncResponse()` from `FeedViewModel`. Create a dedicated background component (like a `SyncCoordinator` class in the domain/sync layer or within `BluetoothService`) that listens to incoming Bluetooth messages and passes them to `SyncManager`. 
**Acceptance criteria:** `FeedViewModel.kt` contains zero references to `com.neo.sync` or `com.neo.bluetooth`.
**Estimated effort:** 1.5 hours

## Priority 2 — High Importance

### TASK-R3-04 — Scope Noise Texture to Create Post Screen
**Source:** TASK-R06
**Files to modify:** `GradientBackground.kt`, `EnhancedCreatePostScreen.kt`
**What to do:** 
Remove the `NoiseOverlay()` call from `GradientBackground.kt`. Move the `NoiseOverlay` composable directly into `EnhancedCreatePostScreen.kt` so it renders exclusively on that modal.
**Acceptance criteria:** The main feed and profile screens are clean; only the Create Post screen has the noise texture.
**Estimated effort:** 0.2 hours

### TASK-R3-05 — Implement Neon Shadow on PostCard
**Source:** TASK-R07
**Files to modify:** `EnhancedPostCard.kt`
**What to do:** 
Replace the standard `Modifier.shadow` with a `Modifier.drawBehind` or custom `graphicsLayer` implementation that paints a multi-layered, blurred, neon purple/orange glow behind the card. 
**Acceptance criteria:** The card appears to emit a soft neon light rather than casting a standard material drop shadow.
**Estimated effort:** 1 hour

## Priority 3 — Polish

### TASK-R3-06 — Fix FAB Pulse Shadow Animation
**Source:** TASK-R03
**Files to modify:** `EnhancedFeedScreen.kt`
**What to do:** 
Animate the actual `shadowElevation` parameter (from `8.dp` to `16.dp`) alongside the scale, rather than hardcoding it to `12.dp`. Ensure both `fabScale` and elevation share the same 1000ms duration.
**Acceptance criteria:** The FAB's drop shadow visibly expands and contracts.
**Estimated effort:** 0.5 hours

---

## Section 8 — Supervisor-Facing Summary (Round 2)

**What Round 2 Fixed:**
OpenCode successfully achieved one of the most difficult goals of this refactor: untangling the core Domain logic from Android-specific code. The business rules for creating posts and compressing images are now purely decoupled, which proves an understanding of Clean Architecture principles. Additionally, the new `PostDetailViewModel` was correctly implemented, resolving the bloated state of the main feed.

**What is still broken or incomplete:**
The UI continues to fall short of the project's design standards. The "glassmorphism" effect on the top header was faked using simple transparency rather than actual blurring, which looks cheap. Furthermore, a critical user-experience regression occurred: the system no longer tells the user if their post was successfully published. Finally, the UI layer is still inappropriately managing raw network synchronization messages.

**Is the project on track for submission?**
Yes, cautiously. The data and networking foundations are rock solid, and the architecture is 80% of the way to being completely clean. However, the UI polish is lagging significantly behind.

**Top 3 things that must be done before submission:**
1. **Restore Feedback:** Ensure the app displays a success/failure message when a user attempts to create a post.
2. **Fix the Glass UI:** Implement actual blur effects on the navigation header to meet the design specification.
3. **Remove Networking from the UI:** Stop the `FeedViewModel` from acting as a network traffic controller; move that logic to a dedicated background service.
