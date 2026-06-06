# Neo: Decentralized & Peer-to-Peer Social Networking

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue?logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Architecture](https://img.shields.io/badge/Architecture-Clean-orange)

**Neo** is a fully decentralized social application built for Android. It enables communication and content sharing without any central server — using Bluetooth Mesh for proximity (LAN) and libp2p GossipSub over DHT for internet-scale (WAN) peer-to-peer connectivity.

---

## 🌟 Key Features

*   **Zero-Server Communication**: No central servers, no cloud, no backend — ever.
*   **Bluetooth Mesh (LAN)**: Multi-hop BLE + Classic RFCOMM for device-to-device sync within ~10m.
*   **libp2p GossipSub (WAN)**: Internet-scale pub/sub mesh over DHT bootstrap — devices on different WiFi networks auto-discover and sync.
*   **Offline-First Architecture**: All data lives in local Room SQLite; sync fires automatically when any peer is reachable.
*   **Cryptographic Identity**: Every post is signed with RSA-2048; public key = identity.
*   **Gossip Protocol**: Epidemic-style content distribution with anti-entropy sync and deduplication.
*   **Cyberpunk UI**: Jetpack Compose with neon glows, glassmorphism, and Material 3 dark-only theme.

## 🚀 Getting Started

### Prerequisites
- Android 8.0 (API 29 recommended for full BLE + DHT support)
- Bluetooth LE + Classic support
- WiFi and internet access (for WAN mesh via libp2p)
- Java 17 for building

### Installation
1.  Clone the repository:
    ```bash
    git clone https://github.com/hassandevelops/neo-fyp.git
    ```
2.  Open in Android Studio (Iguana or newer recommended).
3.  Build and run on a physical device (Emulators do not support BLE mesh).

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3, dark-only)
- **DI**: Hilt
- **Database**: Room (Local-first persistence) + Paging3
- **LAN Networking**: BLE advertising/scanning + Classic Bluetooth RFCOMM
- **WAN Networking**: `jvm-libp2p` — DHT (Kademlia), mDNS, GossipSub v1.2, PEX
- **Serialization**: kotlinx.serialization JSON
- **Concurrency**: Kotlin Coroutines & Flow
- **Security**: RSA-2048 Signatures, EncryptedSharedPreferences (AES256-GCM)

## 📐 Architecture

Neo follows **Clean Architecture** with strict three-layer separation:

- **Data Layer**: Room DAOs, repositories, Bluetooth/LibP2p services, ImageFileStore.
- **Domain Layer**: Pure business logic — use cases and port interfaces (`ISyncPort`, `IImageCompressorPort`).
- **Presentation Layer**: State-driven UI using ViewModels, StateFlow, and Jetpack Compose.
- **Sync Engine**: Hybrid — LAN gossip over Bluetooth RFCOMM + WAN pub/sub over libp2p GossipSub.

## 🤝 Contributing

Contributions are welcome! If you're interested in decentralized tech, mesh networks, or peer-to-peer protocols, feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Part of the Neo Project — Decentralizing the Social Web.*
