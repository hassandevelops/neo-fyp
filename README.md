# Neo: Decentralized & Peer-to-Peer Social Networking

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue?logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Architecture](https://img.shields.io/badge/Architecture-Clean-orange)

**Neo** is a fully decentralized, offline-first social media application built for Android. It enables communication and content sharing in environments without internet access or central server infrastructure by utilizing advanced Bluetooth Mesh networking and cryptographic protocols.

---

## 🌟 Key Features

*   **Serverless Communication**: No central server or internet connection required.
*   **Bluetooth Mesh Networking**: Multi-hop message propagation via BLE.
*   **Offline-First Architecture**: Seamlessly syncs content whenever peers are nearby.
*   **End-to-End Integrity**: Every post is cryptographically signed using **Ed25519**.
*   **Gossip Protocol**: Efficient epidemic-style content distribution with anti-entropy sync.
*   **Modern UI/UX**: Built with **Jetpack Compose** following Material 3 guidelines.

## 🚀 Getting Started

### Prerequisites
- Android 8.0 (API 26) or higher
- Bluetooth LE support
- Java 17 for building

### Installation
1.  Clone the repository:
    ```bash
    git clone https://github.com/hassandevelops/neo-fyp.git
    ```
2.  Open in Android Studio (Iguana or newer recommended).
3.  Build and run on a physical device (Emulators do not support BLE mesh well).

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Dependency Injection**: Hilt
- **Database**: Room (Local-first persistence)
- **Networking**: Bluetooth Low Energy (BLE) Mesh
- **Concurrency**: Kotlin Coroutines & Flow
- **Security**: Ed25519 Signatures, EncryptedSharedPreferences

## 📐 Architecture

Neo follows **Clean Architecture** principles to ensure scalability and testability:

- **Data Layer**: Repositories, Room DAOs, and BLE Service implementations.
- **Domain Layer**: Pure business logic and use cases.
- **Presentation Layer**: State-driven UI using ViewModels and Compose.
- **Sync Engine**: A custom implementation of a Gossip Protocol for decentralized data eventual consistency.

## 🤝 Contributing

Contributions are welcome! If you're interested in decentralized tech, mesh networks, or peer-to-peer protocols, feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Part of the Neo Project - Decentralizing the Social Web.*
