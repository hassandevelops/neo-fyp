# About Neo

## The Vision
In an era of increasing digital surveillance and centralized control, **Neo** was born out of the need for a truly resilient social platform. Our mission is to provide a means of communication that is independent of internet service providers, cellular networks, and centralized servers.

Neo is designed for:
- **Disaster Recovery**: Communicating when infrastructure is down.
- **Privacy-Conscious Users**: Sharing content without a middleman.
- **Remote Areas**: Connecting communities where the internet is a luxury.

## Technical Philosophy

### Decentralization by Default
Unlike "decentralized" platforms that still rely on federated servers (like Mastodon or BlueSky), Neo is **Peer-to-Peer (P2P)** at the physical layer. Data moves directly from phone to phone.

### Eventual Consistency
In a mesh network, nodes come and go. Neo uses an **Epidemic Gossip Protocol** combined with **Anti-Entropy Synchronization** to ensure that all nodes eventually reach the same state, even if they are never connected to everyone at once.

### Trustless Verification
Since there is no central authority to verify users, Neo utilizes **Public-Key Cryptography**. Every post is a self-contained unit of truth, signed by the creator's private key. Identity is tied to the cryptographic keypair, making impersonation mathematically impossible without the private key.

## Project Status
Neo is currently a Final Year Project (FYP) focused on proving the viability of high-level social features over low-bandwidth BLE mesh networks.

---
*Developed by Hassan & The Neo Team.*
