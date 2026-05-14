# Implementation Plan: Neo Architecture & UI Overhaul

## Overview
This document outlines the step-by-step roadmap for OpenCode to resolve the architectural flaws, stability issues, and UI/UX gaps in the Neo Android application. The plan is divided into 4 sequential phases.

---

## Phase 1: Data Integrity & Critical Fixes

**Goal:** Prevent data loss and database crashes.

1.  **Remove Destructive Migrations:**
    *   In `AppDatabase.kt`, remove `fallbackToDestructiveMigration()`.
    *   Implement explicit Room `Migration` classes for future schema changes.
2.  **Refactor Image Storage:**
    *   Update the `Post` model and DAO to remove the `imageData` string (Base64).
    *   Add `imageUri` to store local file paths.
    *   Implement a Migration script to handle existing Base64 data by writing it to disk and updating the DB.
    *   Update `GossipProtocol` to assemble chunks and save them to internal storage rather than updating a database string.
3.  **Fix Concurrent Collections:**
    *   In `BluetoothService.kt`, change `private val connections = mutableMapOf<String, PeerConnection>()` to a `ConcurrentHashMap` or protect it with a Mutex to resolve `ConcurrentModificationException` during peer discovery.

---

## Phase 2: Architectural & Networking Improvements

**Goal:** Adhere to Clean Architecture and stabilize the BLE Mesh.

1.  **Dismantle the "God Object" (`FeedViewModel.kt`):**
    *   Extract post creation logic into a new `CreatePostViewModel`.
    *   Extract comment and reaction logic into a `PostDetailViewModel`.
    *   Keep `FeedViewModel` strictly focused on observing the feed and basic mesh stats.
2.  **Clean up the Domain Layer:**
    *   Remove `GossipProtocol` (a Sync layer component) from `CreatePostUseCase.kt` and `CreateCommentUseCase.kt`.
    *   Create an interface `ISyncBroadcaster` in the domain layer. Implement this interface in the Sync layer and inject it into the use cases.
3.  **Refactor Serialization:**
    *   Replace manual string concatenation and Gson in `MessageProtocol.kt` with `kotlinx.serialization` for robust payload parsing.
4.  **Fix BLE Discovery UUID:**
    *   Replace the standard SPP UUID in `BluetoothService.kt` with a unique, app-specific UUID (e.g., `a1b2c3d4-e5f6-7890-1234-56789abcdef0`) to prevent interference with random Bluetooth devices.

---

## Phase 3: UI/UX Overhaul (Visual Parity)

**Goal:** Match the provided React/Vite "Dark Mode Social Feed" prototype.

1.  **Update Design Tokens:**
    *   Modify `Color.kt` to include the exact hex codes from the prototype (e.g., NeoBlack `#000000`, NeoPurple `#8B5CF6`).
    *   Update `Type.kt` and `Theme.kt` accordingly.
2.  **Build Core Components:**
    *   **Header:** Add the "NEXUS" branding, gradient logo box, and pulsing notification dot.
    *   **EnhancedPostCard:** Implement the glassmorphic background (`SurfaceWhite5`), the complex neon shadow gradients (`Modifier.shadow` or `graphicsLayer`), and the "LIVE" badge logic.
    *   **FAB:** Create the pulsing gradient Floating Action Button using `rememberInfiniteTransition`.
    *   **StoryRow:** Implement the horizontal scrolling avatar list for the top of the feed.
3.  **Create the BLE Mesh Status Screen:**
    *   Build a new screen replicating `BLEMeshStatus.tsx`.
    *   Implement the animated wave backgrounds, expanding pulse rings, and dynamic grid metrics.
4.  **Refine Interactions:**
    *   Add Compose `spring` animations to all button presses and screen transitions.

---

## Phase 4: Polish & Delivery

1.  **Pagination:** Implement `Paging3` for the Feed to ensure the app doesn't crash when loading hundreds of posts.
2.  **Testing:** Conduct physical device testing (minimum 3 Android phones) to verify BLE Mesh discovery, gossip routing, and chunked image transfer.
3.  **Cleanup:** Remove debug logs, optimize imports, and finalize documentation for the FYP submission.
