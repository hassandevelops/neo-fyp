# Architecture & Design Review: Neo Decentralized Social App

## 1. Executive Summary
The Neo Android application is an offline-first, decentralized social media platform utilizing Bluetooth Low Energy (BLE) and a custom Gossip Protocol for peer-to-peer data synchronization. The current codebase serves as a functional prototype but suffers from severe architectural violations, data integrity risks, concurrency bugs, and significant UI/UX gaps compared to the intended design specification. This review outlines these issues layer by layer.

## 2. Layer-by-Layer Breakdown

### 2.1. Presentation Layer (UI & ViewModels)
*   **ISSUE-09: "God Object" ViewModel:** `FeedViewModel` currently handles feed loading, profile management, BLE service binding, comment creation, and post creation. It has too many responsibilities, making it difficult to maintain and test.
*   **ISSUE-18: UI/UX Parity Gap:** The Android Compose implementation drastically diverges from the React/Vite prototype. It lacks the modern "Dark Mode Social Feed" aesthetic, glassmorphism effects, spring-physics animations, and precise color tokens defined in the prototype.
*   **ISSUE-19: State Management:** UI State is somewhat managed but tightly coupled with heavy operations. Navigation is simplistic and lacks deep linking or proper state restoration.

### 2.2. Domain Layer (Use Cases)
*   **ISSUE-14: Clean Architecture Violation:** The domain layer (e.g., `CreatePostUseCase`, `CreateCommentUseCase`) directly imports the `GossipProtocol` from the sync layer. The domain layer should not know about specific sync mechanisms. It should define interfaces (Ports) that the data/sync layers implement (Adapters).
*   **ISSUE-15: Business Logic Leakage:** Rate limiting and crypto logic are intertwined directly inside the use cases rather than being abstracted behind clean interfaces.

### 2.3. Data & Persistence Layer (Room)
*   **ISSUE-12: Destructive Migrations:** `AppDatabase` is configured with `fallbackToDestructiveMigration()`. This is unacceptable for a production decentralized app, as any schema change will wipe all user data, cryptographic keys, and history.
*   **ISSUE-04: Image Storage Anti-Pattern:** Images are currently stored as Base64 strings directly in the `Post` Room entity (`imageData`). This causes massive database bloat, slows down queries, and leads to out-of-memory errors. 
*   **ISSUE-16: Missing Pagination:** `GetFeedUseCase` and `PostDao` return the entire list of posts at once. There is no pagination (e.g., Paging3) implemented, which will crash the app as the dataset grows.

### 2.4. Synchronization & Bluetooth Layer
*   **ISSUE-01: Bonded-Only Discovery:** The `BluetoothService` currently relies on discovering only bonded (paired) devices for SPP connections. A true BLE mesh needs to discover and connect to nearby active nodes seamlessly without manual pairing.
*   **ISSUE-02: Public SPP UUID:** The app uses the public, standard SPP UUID (`00001101-0000-1000-8000-00805F9B34FB`). This will cause interference and connection attempts with non-Neo devices (like headphones). A unique, app-specific UUID is required.
*   **ISSUE-03: Concurrency Race Conditions:** `BluetoothService.connections` uses a standard `mutableMapOf` which is accessed and modified by multiple coroutines concurrently without synchronization, leading to `ConcurrentModificationException`s.
*   **ISSUE-05: Image Sync Loss:** The `Message.PostBroadcast` lacks the `imageData` payload. While `GossipProtocol` attempts to chunk and send images separately, the assembly and correlation logic is fragile and prone to data loss if chunks arrive out of order or connections drop.
*   **ISSUE-07 & 08: Inefficient Sync:** `SyncManager` requests all posts after a certain timestamp blindly. There is no Merkle-tree or bloom filter-based anti-entropy protocol to efficiently determine exactly which messages are missing.
*   **ISSUE-20: Manual Serialization:** `MessageProtocol` uses manual string splitting and Gson serialization. This is brittle and slow compared to modern binary serialization (like Protocol Buffers) or kotlinx.serialization.

### 2.5. Security & Cryptography
*   **ISSUE-17: Key Management:** Keys are generated and stored in EncryptedSharedPreferences. While secure, the fallback from Ed25519 to RSA creates inconsistency in signature sizes and verification logic across peers.

## 3. Required Architectural Changes (ACs)
*   **AC-01:** Rewrite BLE discovery to use standard BLE advertising and scanning for seamless peer discovery, establishing RFCOMM/L2CAP channels only upon handshake.
*   **AC-02:** Migrate from manual string/Gson serialization to `kotlinx.serialization` for reliable message parsing.
*   **AC-03:** Remove Base64 `imageData` from Room. Save images to the internal file system and store only the file URI/hash in the database.
*   **AC-04:** Implement `Paging3` for feed and comments.
*   **AC-05:** Replace `ConcurrentModificationException`-prone maps with `ConcurrentHashMap` or StateFlows in `BluetoothService`.
*   **AC-06:** Split `FeedViewModel` into `FeedViewModel`, `CreatePostViewModel`, and `ProfileViewModel`.
*   **AC-07:** Isolate the Domain layer. Introduce `SyncPort` and `CryptoPort` interfaces.
*   **AC-08:** Remove `fallbackToDestructiveMigration()` and write explicit Room migration scripts.
*   **AC-09:** Perform a complete UI overhaul to match the React prototype's tokens, animations, and layouts.
