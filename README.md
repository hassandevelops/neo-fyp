# Neo: Decentralized & Peer-to-Peer Social Networking

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue?logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-yellow)

Neo is a fully decentralized social app for Android. Posts, comments, and
reactions travel phone-to-phone over Bluetooth for local mesh and over
libp2p (with a real GossipSub content-routing layer and a Kademlia DHT for
peer discovery) for global sync. There is no project-operated server. Any
user can run their own bootstrap node.

**The canonical documentation is [`docs/CURRENT.md`](docs/CURRENT.md).**
Everything below is a one-page summary.

---

## What it does

- **Bluetooth mesh** — two phones in the same room exchange posts with no
  internet, no servers, no infrastructure of any kind.
- **Cross-network sync** — two phones on two different Wi-Fi networks
  exchange posts through a user-operated bootstrap (a small Go binary
  you can run on any machine with a public IP).
- **Self-hostable** — the bootstrap is shipped in the repo. Run it on
  your laptop with Docker, share its QR code with friends, they point
  their phones at it.
- **Cryptographic identity** — every post is signed with RSA-2048; the
  public key is the identity. No central certificate authority.

## Quick build

Prerequisites: JDK 17 (not 18+), Android SDK API 34, Go 1.22+.

```bash
# 1. Build the Go binary (libp2p host)
cd go-libp2p-wrapper && ./build.sh && cd ..

# 2. Build the Android app
./gradlew assembleDebug

# 3. Install on two physical ARM64 devices
#    (x86 emulators cannot run the ARM64 Go binary)
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Run a bootstrap

See [`docs/host-a-bootstrap.md`](docs/host-a-bootstrap.md). The short
version:

```bash
docker run -d --name neo-bootstrap \
  -p 4001:4001 -p 4002:4002 \
  -v neo-bootstrap-key:/data \
  -e BOOTSTRAP_PORT=4001 -e BOOTSTRAP_HTTP_PORT=4002 \
  neo-bootstrap:latest
```

The bootstrap logs a multiaddr and serves a QR code at
`http://<host>:4002/`. Open the Neo app → Settings → Network → Scan
Bootstrap QR Code, point the camera at it, done.

## Tech stack

- **UI**: Jetpack Compose (Material 3, dark-only)
- **DI**: Hilt
- **Database**: Room (Local-first persistence) + Paging3
- **LAN networking**: BLE advertising + chunked GATT
- **WAN networking**: `go-libp2p` shipped as an ARM64 binary in
  `app/src/main/assets/`, communicating with the Kotlin app via JSON
  lines over localhost TCP
- **Discovery**: Kademlia DHT (global), NSD (Android-side, LAN)
- **Content routing**: GossipSub pub/sub on the `neo-social-v1` topic
- **Serialization**: kotlinx.serialization JSON
- **Security**: RSA-2048 signatures, EncryptedSharedPreferences
  (AES-256-GCM), Ed25519 from Go binary for libp2p identity

## Social layer (profiles · follows · notifications)

Recent work added a full social layer on top of the post/comment/reaction
mesh, plus a profile redesign. All of it propagates peer-to-peer — there is
still no server.

- **Profile sync** — display name, bio, and a small avatar thumbnail now
  travel across the mesh as a signed `ProfileBroadcast`, cached locally in
  a `peer_profiles` table (keyed by DID, *latest-wins* by `updatedAt`).
  Avatars/names/bios of **other** users now render correctly everywhere
  (feed, post detail, search, their profile) instead of falling back to a
  placeholder. Resolution is centralized in `com.neo.ui.util.resolveAvatar`.
- **Follow graph** — a signed, mesh-propagated `FollowBroadcast` + `follows`
  table. Follow/Following button on other users' profiles, real
  follower/following counts, and a `follow` notification delivered to the
  followed user across devices.
- **Notification navigation** — tapping a notification opens the related
  content: like/comment/reply/repost → the post; follow/mention → the
  actor's profile. Deleted content degrades gracefully.
- **In-app banners** — a subtle, auto-dismissing top banner announces new
  notifications while the app is foregrounded; tap routes to the content.
- **Profile redesign** — the Posts / Media / Saved tabs now render the same
  full social post card as the main feed (no more image grid), with working
  like/comment/save and preserved per-tab scroll. Native-styled overflow
  menu; BLE Mesh card reverted to a solid accent surface.

Identity note: posts are authored under the user's **DID** (`did:key`) while
comments/reactions use the **device id**. Profiles/follows are signed with
the identity (DID) key and verified against the key encoded in the DID.
Because comments are device-id-authored but profiles are DID-keyed, other
users' avatars resolve on their *posts* but not yet on their *comments*.

Schema is at Room **v14** (migration `13 → 14` adds `peer_profiles`,
`follows`, and notification routing columns); exported schemas live in
`app/schemas/`.

## License

MIT — see [`LICENSE`](LICENSE).
