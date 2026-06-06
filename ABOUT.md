# About Neo

## The Vision
In an era of increasing digital surveillance and centralized control, **Neo** was born out of the need for a truly resilient social platform. Our mission is to provide a means of communication that is independent of internet service providers, cellular networks, and centralized servers.

Neo is designed for:
- **Disaster Recovery**: Communicating when infrastructure is down.
- **Privacy-Conscious Users**: Sharing content without a middleman.
- **Remote Areas**: Connecting communities where internet is a luxury.
- **Internet-Scale Mesh**: Devices on different WiFi networks sync via libp2p GossipSub over DHT — no central relay, no cloud.

## Technical Philosophy

### Decentralization by Default
Unlike "decentralized" platforms that still rely on federated servers (like Mastodon or BlueSky), Neo is **Peer-to-Peer (P2P)** at the physical layer. Data moves directly from phone to phone — over Bluetooth when nearby, or over the internet via libp2p's encrypted transport when apart.

### Dual Transport: LAN + WAN
Neo operates on two independent transport layers:
- **LAN (Bluetooth Mesh)**: BLE advertising/scanning discovers nearby peers; Classic RFCOMM sockets carry all message payloads. Works underground, in disasters, anywhere Bluetooth works.
- **WAN (libp2p GossipSub)**: Devices bootstrap into the IPFS DHT via public bootstrap peers, discover each other's PeerId via mDNS (same network) or PEX (Peer Exchange across networks), then build a GossipSub mesh overlay for pub/sub message delivery. No central relay — peers gossip directly over encrypted libp2p streams.

### Eventual Consistency
In a mesh network, nodes come and go. Neo uses an **Epidemic Gossip Protocol** combined with **Anti-Entropy Synchronization** to ensure that all nodes eventually reach the same state, even if they are never connected to everyone at once. The sync engine works identically over Bluetooth and libp2p — posts traverse whichever transport is available.

### Trustless Verification
Since there is no central authority to verify users, Neo utilizes **Public-Key Cryptography**. Every post is a self-contained unit of truth, signed by the creator's private key (RSA-2048, keyAlgorithm propagated in messages for cross-algorithm compatibility). Identity is tied to the cryptographic keypair, making impersonation mathematically impossible without the private key.

## Project Status
Neo is currently a Final Year Project (FYP) focused on proving the viability of high-level social features over fully decentralized, multi-transport mesh networks. The system supports Bluetooth LAN mesh and libp2p WAN mesh, with DHT bootstrap, mDNS LAN discovery, GossipSub topic-based routing, and Peer Exchange for cross-network address propagation.

---
*Developed by Hassan & The Neo Team.*
