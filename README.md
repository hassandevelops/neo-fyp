# Neo - Decentralized Social Media

A fully decentralized, offline-first social media application for Android that uses Bluetooth mesh networking to propagate public posts without any central server.

## Features

- ✅ Fully decentralized - no servers required
- ✅ Offline-first - works without internet
- ✅ Bluetooth mesh networking for peer-to-peer communication
- ✅ Cryptographic signatures (Ed25519) for post authenticity
- ✅ Epidemic-style gossip protocol for content propagation
- ✅ Local-first database with Room
- ✅ Modern UI with Jetpack Compose

## Architecture

- **Clean Architecture** with MVVM pattern
- **Data Layer**: Room database, repositories
- **Domain Layer**: Use cases for business logic
- **Presentation Layer**: Jetpack Compose UI, ViewModels
- **Bluetooth Layer**: Foreground service, peer connections
- **Sync Layer**: Gossip protocol, sync manager

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth support
- Location permission (for Bluetooth scanning)
- **Java 17** (for building the project)

## Building

**Important**: This project requires Java 17 to build. See [BUILD.md](BUILD.md) for detailed instructions.

Quick build (with Java 17 configured):
```bash
./gradlew assembleDebug
```

## How It Works

1. **Post Creation**: Users create posts that are cryptographically signed with Ed25519
2. **Local Storage**: Posts are stored in local Room database
3. **Bluetooth Discovery**: App continuously scans for nearby devices running Neo
4. **Peer Connection**: Establishes Bluetooth connections with discovered peers
5. **Gossip Protocol**: Posts propagate through the network with TTL-based forwarding
6. **Anti-Entropy Sync**: Periodic sync requests ensure no posts are missed
7. **Signature Verification**: All received posts are verified before storage

## Security

- Each device generates an Ed25519 keypair on first launch
- Private keys are stored in EncryptedSharedPreferences
- All posts are signed and verified
- Invalid signatures are rejected

## License

MIT License
