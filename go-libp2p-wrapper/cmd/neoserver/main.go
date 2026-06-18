package main

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/libp2p/go-libp2p"
	pubsub "github.com/libp2p/go-libp2p-pubsub"
	"github.com/libp2p/go-libp2p/core/crypto"
	"github.com/libp2p/go-libp2p/core/event"
	"github.com/libp2p/go-libp2p/core/host"
	"github.com/libp2p/go-libp2p/core/network"
	"github.com/libp2p/go-libp2p/core/peer"
	"github.com/libp2p/go-libp2p/core/peerstore"
	"github.com/libp2p/go-libp2p/core/protocol"
	"github.com/libp2p/go-libp2p/p2p/discovery/mdns"
	"github.com/libp2p/go-libp2p/p2p/host/eventbus"
	"github.com/libp2p/go-libp2p/p2p/host/routed"
	"github.com/libp2p/go-libp2p/p2p/protocol/circuitv2/relay"

	"github.com/ipfs/go-cid"
	ds "github.com/ipfs/go-datastore"
	dsync "github.com/ipfs/go-datastore/sync"
	dht "github.com/libp2p/go-libp2p-kad-dht"
	ma "github.com/multiformats/go-multiaddr"
	"github.com/multiformats/go-multihash"
)

const (
	NeoProtocolID = "/neo/gossip/1.0.0"
	NeoTopic      = "neo-social-v1"
	Version       = "1.0.0"
)

// -------- IPC protocol (JSON lines over TCP) --------

type Command struct {
	Cmd       string `json:"cmd"`
	PrivKey   string `json:"privKey,omitempty"`
	Port      int    `json:"port,omitempty"`
	Multiaddr string `json:"multiaddr,omitempty"`
	PeerID    string `json:"peerID,omitempty"`
	Message   string `json:"message,omitempty"`
	Topic     string `json:"topic,omitempty"`
}

type Event struct {
	Event    string `json:"event"`
	PeerID   string `json:"peerID,omitempty"`
	Message  string `json:"message,omitempty"`
	Topic    string `json:"topic,omitempty"`
	Addrs    string `json:"addrs,omitempty"`
	Count    int    `json:"count"`
	Error    string `json:"error,omitempty"`
	Status   string `json:"status,omitempty"`   // AutoNAT reachability: public|private|unknown
	LanAddrs string `json:"lanAddrs,omitempty"` // comma-sep LAN multiaddrs (same-Wi-Fi)
	WanAddrs string `json:"wanAddrs,omitempty"` // comma-sep public/relay multiaddrs (cross-network)
	CanHost  bool   `json:"canHost,omitempty"`  // true if this node is reachable enough to host peers
}

// -------- Bootstrap peers --------
// Configurable via:
//  1. File: "bootstrap.conf" - one multiaddr per line
//  2. Env: NEO_BOOTSTRAP - comma-separated multiaddrs
//  3. Fallback: hardcoded default
//
// No public/default bootstrap peers. Neo does not touch public IPFS
// infrastructure: same-Wi-Fi discovery is handled by mDNS/Android NSD, and
// cross-network sync goes through a bootstrap the user runs and scans via QR
// (set_bootstrap / the neo://bootstrap deep link). This keeps the user off the
// global public DHT, so the node never exposes its IP to random internet peers.
var defaultBootstrapPeers = []string{}

var bootstrapPeers = loadBootstrapPeers()

func loadBootstrapPeers() []string {
	// Try config file first
	if data, err := os.ReadFile("bootstrap.conf"); err == nil {
		var peers []string
		for _, line := range strings.Split(string(data), "\n") {
			line = strings.TrimSpace(line)
			if line != "" && !strings.HasPrefix(line, "#") {
				peers = append(peers, line)
			}
		}
		if len(peers) > 0 {
			log.Printf("bootstrap: loaded %d peers from bootstrap.conf", len(peers))
			return peers
		}
	}
	// Try env var
	if env := os.Getenv("NEO_BOOTSTRAP"); env != "" {
		peers := strings.Split(env, ",")
		var valid []string
		for _, p := range peers {
			p = strings.TrimSpace(p)
			if p != "" {
				valid = append(valid, p)
			}
		}
		if len(valid) > 0 {
			log.Printf("bootstrap: loaded %d peers from NEO_BOOTSTRAP env", len(valid))
			return valid
		}
	}
	// Fallback to default
	log.Printf("bootstrap: using default peers")
	return defaultBootstrapPeers
}

// getBootstrapRelayIP extracts the IP from the first bootstrap peer multiaddr
// for use in p2p-circuit relay addresses
func getBootstrapRelayIP() string {
	// Use the first CUSTOM bootstrap peer's IP for relay address construction.
	// For public (DNS) bootstrap peers, fall back to the node's own outbound IP.
	for _, s := range bootstrapPeers {
		if !strings.HasPrefix(s, "/dns") {
			addr, err := ma.NewMultiaddr(s)
			if err != nil {
				continue
			}
			host, _ := ma.SplitFirst(addr)
			if host != nil {
				return host.Value()
			}
		}
	}
	// Fall back to node's detected IP
	if ip := getOutboundIP(); ip != "" {
		return ip
	}
	return "127.0.0.1"
}

// -------- libp2p node wrapper --------

type Libp2pNode struct {
	host          host.Host
	dht           *dht.IpfsDHT
	ctx           context.Context
	cancel        context.CancelFunc
	port          int
	eventWriter   func(Event)
	activeStreams map[string]network.Stream
	lock          sync.RWMutex
	dialing       map[string]bool // peers with an in-flight connect attempt
	dialMu        sync.Mutex
	bootPeerIDs   []peer.ID  // all connected bootstrap peers (both public and custom)
	customBootIDs []peer.ID  // subset that support /neo/px/1.0.0 and /neo/proxy/1.0.0
	customBootMu  sync.Mutex // guards customBootIDs
	mdnsService   mdns.Service
	pxMu          sync.Mutex
	peerAddrs     map[peer.ID][]string
	pubsub        *pubsub.PubSub
	topic         *pubsub.Topic
	topicSub      *pubsub.Subscription
	reachability  network.Reachability // AutoNAT verdict; guarded by lock
}

// reachabilityString maps a libp2p reachability verdict to the wire string
// shared with the Kotlin app and surfaced in the UI.
func reachabilityString(r network.Reachability) string {
	switch r {
	case network.ReachabilityPublic:
		return "public"
	case network.ReachabilityPrivate:
		return "private"
	default:
		return "unknown"
	}
}

// sendEvent writes a JSON event to the Kotlin app via TCP
func (n *Libp2pNode) sendEvent(evt Event) {
	if n.eventWriter != nil {
		n.eventWriter(evt)
	}
}

// isPrivateAddr reports whether a multiaddr string is a LAN/private/loopback
// address (only reachable on the same network) vs a public, internet-routable
// one. Used to decide which address to advertise in the Connect-to-me QR.
func isPrivateAddr(s string) bool {
	host := ""
	if a, err := ma.NewMultiaddr(s); err == nil {
		if v, err := a.ValueForProtocol(ma.P_IP4); err == nil {
			host = v
		} else if v, err := a.ValueForProtocol(ma.P_IP6); err == nil {
			host = v
		}
	}
	if host == "" {
		return true // can't tell → treat as non-shareable-public
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return true
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsUnspecified()
}

// HandlePeerFound is called by mDNS when a peer on the LAN is discovered
// LAN peers connect directly without needing the bootstrap proxy
func (n *Libp2pNode) HandlePeerFound(pi peer.AddrInfo) {
	if pi.ID == n.host.ID() {
		return
	}
	n.lock.RLock()
	_, already := n.activeStreams[pi.ID.String()]
	n.lock.RUnlock()
	if already {
		return
	}
	log.Printf("mdns: found LAN peer %s with %d addrs", pi.ID, len(pi.Addrs))
	cctx, cancel := context.WithTimeout(n.ctx, 15*time.Second)
	defer cancel()
	if err := n.host.Connect(cctx, pi); err != nil {
		log.Printf("mdns: connect %s: %v", pi.ID, err)
		return
	}
	log.Printf("mdns: connected to LAN peer %s directly (no proxy)", pi.ID)
}

func (n *Libp2pNode) handleProxyStream(s network.Stream) {
	log.Printf("proxy: handler entered, remote=%s", s.Conn().RemotePeer())
	// The header is a single newline-terminated JSON object (the initiator uses
	// json.Encode, which appends '\n'). Read exactly that line so any gossip
	// bytes that follow stay buffered for the destination path.
	buffered := bufio.NewReader(s)
	headerLine, err := buffered.ReadString('\n')
	if err != nil && headerLine == "" {
		log.Printf("proxy: read header: %v", err)
		s.Close()
		return
	}
	var msg map[string]interface{}
	if jerr := json.Unmarshal([]byte(strings.TrimSpace(headerLine)), &msg); jerr != nil {
		log.Printf("proxy: header decode error: %v", jerr)
		s.Close()
		return
	}

	targetStr, _ := msg["target"].(string)
	srcStr, _ := msg["src"].(string)

	// FINAL DESTINATION: target is us (or unset/legacy). Service the tunnel as a
	// gossip session under the SOURCE peer id so posts reach the app and replies
	// flow back over the same tunnel. This is the fix that lets posts traverse a
	// bootstrap-relayed connection.
	if targetStr == "" || targetStr == n.host.ID().String() {
		peerID := srcStr
		if peerID == "" {
			peerID = s.Conn().RemotePeer().String()
		}
		log.Printf("proxy: destination reached, servicing tunnel as gossip from %s", peerID)
		n.serviceProxyTunnel(peerID, s, buffered)
		return
	}

	// RELAY: forward to the target, re-sending the header so the target learns
	// the original src. (A phone can relay too, though normally the bootstrap does.)
	target, err := peer.Decode(targetStr)
	if err != nil {
		log.Printf("proxy: invalid target: %v", err)
		s.Close()
		return
	}
	ctx, cancel := context.WithTimeout(n.ctx, 10*time.Second)
	targetStream, err := n.host.NewStream(ctx, target, protocol.ID("/neo/proxy/1.0.0"))
	cancel()
	if err != nil {
		log.Printf("proxy: dial target %s: %v", target, err)
		s.Close()
		return
	}
	hdr, _ := json.Marshal(msg)
	targetStream.Write(append(hdr, '\n'))
	log.Printf("proxy: relaying to target %s", target)
	var wg sync.WaitGroup
	wg.Add(2)
	go func() { defer wg.Done(); io.Copy(targetStream, buffered) }()
	go func() { defer wg.Done(); io.Copy(s, targetStream) }()
	wg.Wait()
	s.Close()
	targetStream.Close()
}

// serviceProxyTunnel services a proxied stream as a gossip session: emits
// peer_connected for peerID, forwards inbound lines (from the buffered reader,
// which may already hold gossip bytes pulled in with the header) as message
// events, and registers the stream so sendMessage replies over the same tunnel.
// serviceProxyTunnel services an INBOUND proxied stream as a READ-ONLY gossip
// delivery channel. We deliberately do NOT register it in activeStreams: the
// reverse direction of a relayed stream does not deliver reads to the far end,
// so replies must go out over THIS node's OWN outbound tunnel (opened by the
// discovery loop), not back through an inbound one. We emit peer_connected so
// the app sees the peer, pump inbound lines, and never touch the send path.
func (n *Libp2pNode) serviceProxyTunnel(peerID string, s network.Stream, buffered *bufio.Reader) {
	defer s.Close()
	n.sendEvent(Event{Event: "peer_connected", PeerID: peerID})

	for {
		line, err := buffered.ReadString('\n')
		if err != nil {
			if err != io.EOF {
				log.Printf("proxy tunnel read error from %s: %v", peerID, err)
			}
			break
		}
		line = strings.TrimSuffix(line, "\n")
		if len(line) == 0 {
			continue
		}
		n.sendEvent(Event{Event: "message", PeerID: peerID, Message: line})
	}
}

func (n *Libp2pNode) sendMessage(peerID string, message string) error {
	n.lock.RLock()
	s, ok := n.activeStreams[peerID]
	n.lock.RUnlock()

	if ok && s != nil {
		_, err := s.Write([]byte(message + "\n"))
		if err == nil {
			return nil
		}
		n.lock.Lock()
		delete(n.activeStreams, peerID)
		n.lock.Unlock()
	}
	return fmt.Errorf("stream not available")
}

func (n *Libp2pNode) handleStream(s network.Stream) {
	n.serviceStream(s)
}

// serviceStream registers a /neo/gossip stream as the active stream for its
// peer, emits peer_connected, and pumps inbound lines to the Kotlin app until
// the stream closes. Used for BOTH inbound streams (handleStream) and the
// outbound stream opened by promoteBootstrapToPeer — without a reader loop an
// outbound stream is never serviced and sendMessage writes silently fail.
// tryBeginDial returns true if no connection attempt is already in flight for
// peerID and no active stream exists. It marks the peer as dialing. Prevents
// the discovery loop from racing multiple concurrent tunnels to the same peer
// (which flap and leave connectedPeers momentarily empty). Call endDial when done.
func (n *Libp2pNode) tryBeginDial(peerID string) bool {
	n.lock.RLock()
	_, hasStream := n.activeStreams[peerID]
	n.lock.RUnlock()
	if hasStream {
		return false
	}
	n.dialMu.Lock()
	defer n.dialMu.Unlock()
	if n.dialing[peerID] {
		return false
	}
	n.dialing[peerID] = true
	return true
}

func (n *Libp2pNode) endDial(peerID string) {
	n.dialMu.Lock()
	delete(n.dialing, peerID)
	n.dialMu.Unlock()
}

func (n *Libp2pNode) serviceStream(s network.Stream) {
	n.serviceStreamFor(s.Conn().RemotePeer().String(), s)
}

// serviceStreamFor is like serviceStream but uses an explicit peer id. This is
// required for proxied/relayed tunnels, where s.Conn().RemotePeer() is the
// relay (bootstrap), not the actual peer on the other end of the tunnel.
func (n *Libp2pNode) serviceStreamFor(peerID string, s network.Stream) {
	defer s.Close()
	defer func() {
		n.lock.Lock()
		if n.activeStreams[peerID] == s {
			delete(n.activeStreams, peerID)
		}
		n.lock.Unlock()
		n.sendEvent(Event{Event: "peer_disconnected", PeerID: peerID})
	}()

	n.lock.Lock()
	if existing, exists := n.activeStreams[peerID]; exists && existing != s {
		existing.Close()
	}
	n.activeStreams[peerID] = s
	n.lock.Unlock()

	n.sendEvent(Event{Event: "peer_connected", PeerID: peerID})

	reader := bufio.NewReader(s)
	log.Printf("serviceStreamFor: %s reader loop START", peerID)
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err != io.EOF {
				log.Printf("read error from %s: %v", peerID, err)
			}
			log.Printf("serviceStreamFor: %s reader loop END (%v)", peerID, err)
			break
		}
		line = strings.TrimSuffix(line, "\n")
		if len(line) == 0 {
			continue
		}
		n.sendEvent(Event{Event: "message", PeerID: peerID, Message: line})
	}
}

func (n *Libp2pNode) handlePeerExchange(s network.Stream) {
	defer s.Close()
	var ann map[string]interface{}
	if err := json.NewDecoder(s).Decode(&ann); err != nil {
		log.Printf("px: decode error: %v", err)
		return
	}
	peerIDStr, ok := ann["peer_id"].(string)
	if !ok {
		log.Printf("px: missing peer_id")
		return
	}
	addrsInterface, ok := ann["addrs"].([]interface{})
	if !ok {
		log.Printf("px: missing addrs")
		return
	}
	var addrs []string
	for _, a := range addrsInterface {
		if addrStr, ok := a.(string); ok {
			addrs = append(addrs, addrStr)
		}
	}
	pid, err := peer.Decode(peerIDStr)
	if err != nil {
		log.Printf("px: invalid peer id %s: %v", peerIDStr, err)
		return
	}
	if pid == n.host.ID() {
		return
	}
	log.Printf("px: received announcement for %s with %d addrs", pid, len(addrs))

	// Store and relay to all other connected peers
	n.pxMu.Lock()
	n.peerAddrs[pid] = addrs
	for otherPid := range n.peerAddrs {
		if otherPid == pid {
			continue
		}
		go func(target peer.ID) {
			ctx, cancel := context.WithTimeout(n.ctx, 5*time.Second)
			defer cancel()
			pxStream, err := n.host.NewStream(ctx, target, protocol.ID("/neo/px/1.0.0"))
			if err != nil {
				return
			}
			defer pxStream.Close()
			relayAddrs := make([]string, len(addrs))
			copy(relayAddrs, addrs)
			relayAddr := fmt.Sprintf("/p2p-circuit/p2p/%s", pid.String())
			relayAddrs = append(relayAddrs, relayAddr)
			relayAnn := map[string]interface{}{
				"peer_id": pid.String(),
				"addrs":   relayAddrs,
			}
			if err := json.NewEncoder(pxStream).Encode(relayAnn); err != nil {
				log.Printf("px: relay encode to %s: %v", target, err)
				return
			}
			log.Printf("px: relayed %s to %s with relay addr", pid, target)
		}(otherPid)
	}
	n.pxMu.Unlock()

	// Also attempt direct connection
	var filtered []ma.Multiaddr
	for _, a := range addrs {
		maddr, err := ma.NewMultiaddr(a)
		if err != nil {
			continue
		}
		s := maddr.String()
		if strings.Contains(s, "/ip4/127.0.0.1") || strings.Contains(s, "/ip6/::1") {
			continue
		}
		filtered = append(filtered, maddr)
	}
	if len(filtered) == 0 {
		log.Printf("px: no non-loopback addrs for %s", pid)
		return
	}
	// Add announced addresses to the libp2p peerstore so future
	// host.Connect(peer.AddrInfo{ID: pid}) calls in the discovery loop
	// can find relay / circuit addresses. Without this, the bare-ID
	// Connect at the top of the discovery loop has no addresses to
	// work with and the relay fallback is unreachable for two NAT'd
	// peers that only learned each other's addresses via PX.
	for _, maddr := range filtered {
		n.host.Peerstore().AddAddr(pid, maddr, peerstore.PermanentAddrTTL)
	}
	log.Printf("px: stored %d addrs in peerstore for %s", len(filtered), pid)
	go func() {
		cctx, cancel := context.WithTimeout(n.ctx, 60*time.Second)
		defer cancel()
		if err := n.host.Connect(cctx, peer.AddrInfo{ID: pid, Addrs: filtered}); err != nil {
			log.Printf("px: connect %s: %v", pid, err)
		} else {
			log.Printf("px: connected to %s", pid)
		}
	}()
}

func (n *Libp2pNode) broadcastMessage(message string) int {
	conns := n.host.Network().Conns()
	count := 0
	for _, c := range conns {
		pid := c.RemotePeer().String()
		if err := n.sendMessage(pid, message); err == nil {
			count++
		}
	}
	return count
}

func (n *Libp2pNode) connectedPeers() []string {
	conns := n.host.Network().Conns()
	seen := make(map[string]struct{})
	var result []string
	for _, c := range conns {
		id := c.RemotePeer().String()
		if _, ok := seen[id]; !ok {
			seen[id] = struct{}{}
			result = append(result, id)
		}
	}
	return result
}

func (n *Libp2pNode) dhtCount() int {
	rt := n.dht.RoutingTable()
	if rt == nil {
		return 0
	}
	return rt.Size()
}

func (n *Libp2pNode) listenAddresses() []string {
	pid := n.host.ID().String()
	addrs := n.host.Addrs()
	result := make([]string, 0, len(addrs)+1)
	hasNonLoopback := false
	for _, a := range addrs {
		s := a.String()
		if strings.Contains(s, "/ip4/0.0.0.0") || strings.Contains(s, "/ip6/::") {
			ip := getOutboundIP()
			if ip != "" {
				s = strings.Replace(s, "/ip4/0.0.0.0", "/ip4/"+ip, 1)
				s = strings.Replace(s, "/ip6/::", "/ip4/"+ip, 1)
				hasNonLoopback = true
			}
		} else if !strings.Contains(s, "/ip4/127.0.0.1") && !strings.Contains(s, "/ip6/::1") {
			hasNonLoopback = true
		}
		if !strings.Contains(s, "/p2p/") {
			s += "/p2p/" + pid
		}
		result = append(result, s)
	}
	// If all addresses are loopback (route table restricted on Android),
	// try to determine the real outbound IP and add it.
	if !hasNonLoopback {
		if ip := getOutboundIP(); ip != "" {
			lanAddr := fmt.Sprintf("/ip4/%s/tcp/%d/p2p/%s", ip, n.port, pid)
			result = append(result, lanAddr)
		}
	}
	return result
}

// -------- Initialization --------

func newNode(ctx context.Context, privKeyBytes []byte, port int) (*Libp2pNode, error) {
	keyFile := "neoserver.key"
	var privKey crypto.PrivKey

	// Try loading existing key from file
	if data, err := os.ReadFile(keyFile); err == nil && len(data) > 0 {
		privKey, err = crypto.UnmarshalPrivateKey(data)
		if err != nil {
			privKey = nil
		}
	}

	if privKey == nil {
		if len(privKeyBytes) == 64 {
			var err error
			privKey, err = crypto.UnmarshalEd25519PrivateKey(privKeyBytes)
			if err != nil {
				return nil, fmt.Errorf("unmarshal privkey: %w", err)
			}
		} else {
			var err error
			privKey, _, err = crypto.GenerateEd25519Key(rand.Reader)
			if err != nil {
				return nil, fmt.Errorf("generate new key: %w", err)
			}
		}
		// Persist key for next run
		if data, err := crypto.MarshalPrivateKey(privKey); err == nil {
			os.WriteFile(keyFile, data, 0600)
		}
	}

	if port == 0 {
		port = 9876
	}

	listenAddr := fmt.Sprintf("/ip4/0.0.0.0/tcp/%d", port)

	basicHost, err := libp2p.New(
		libp2p.ListenAddrStrings(listenAddr),
		libp2p.Identity(privKey),
		libp2p.DefaultTransports,
		libp2p.DefaultMuxers,
		libp2p.DefaultSecurity,
		// Keep the ability to USE a circuit relay and hole-punch, so a
		// user-operated Neo bootstrap can relay for NAT'd peers. We intentionally
		// do NOT enable AutoRelay against public IPFS relays — Neo no longer
		// touches public IPFS infrastructure. Cross-network delivery goes through
		// a bootstrap the user runs (scanned via QR), keeping the node off the
		// public DHT and not exposing the user's IP to random internet peers.
		libp2p.EnableRelay(),
		libp2p.EnableHolePunching(),
		libp2p.NATPortMap(),
		// AutoNAT: lets this node learn its own reachability (public vs private)
		// and answer reachability probes for other Neo peers. This is what drives
		// the "super-peer" role detection surfaced in the app.
		libp2p.EnableNATService(),
		// AutoRelay against the USER'S OWN bootstrap(s) — not public IPFS. The
		// peer source yields whatever bootstrap the user configured, so a NAT'd
		// phone reserves a relay slot there and becomes dialable via /p2p-circuit.
		// This is what lets two NAT'd phones sync through a self-hosted bootstrap.
		libp2p.EnableAutoRelayWithPeerSource(autoRelayPeerSource),
	)
	if err != nil {
		return nil, fmt.Errorf("new host: %w", err)
	}

	// Create DHT with private routing table filter (accepts LAN peers)
	dstore := dsync.MutexWrap(ds.NewMapDatastore())
	bootInfos := parseBootstrapPeers()
	log.Printf("bootstrap peers: %d", len(bootInfos))
	kdht, err := dht.New(ctx, basicHost,
		dht.Datastore(dstore),
		dht.BootstrapPeers(bootInfos...),
		dht.Mode(dht.ModeServer),
	)
	if err != nil {
		return nil, fmt.Errorf("new dht: %w", err)
	}

	routedHost := routedhost.Wrap(basicHost, kdht)

	node := &Libp2pNode{
		host:          routedHost,
		dht:           kdht,
		ctx:           ctx,
		port:          port,
		activeStreams: make(map[string]network.Stream),
		dialing:       make(map[string]bool),
		bootPeerIDs:   make([]peer.ID, 0),
		customBootIDs: make([]peer.ID, 0),
		peerAddrs:     make(map[peer.ID][]string),
	}

	// Register stream handlers BEFORE connecting to bootstrap
	routedHost.SetStreamHandler(protocol.ID(NeoProtocolID), node.handleStream)
	routedHost.SetStreamHandler(protocol.ID("/neo/px/1.0.0"), node.handlePeerExchange)
	routedHost.SetStreamHandler(protocol.ID("/neo/proxy/1.0.0"), node.handleProxyStream)

	// NotifyBundle: record remote connection addresses for NAT recall
	routedHost.Network().Notify(&network.NotifyBundle{
		ConnectedF: func(_ network.Network, c network.Conn) {
			remotePID := c.RemotePeer()
			remoteAddr := c.RemoteMultiaddr()
			routedHost.Peerstore().AddAddr(remotePID, remoteAddr, 24*time.Hour)
			log.Printf("notifiee: peer=%s connected from %s", remotePID, remoteAddr)
		},
		DisconnectedF: func(_ network.Network, c network.Conn) {
			log.Printf("notifiee: peer=%s disconnected", c.RemotePeer())
		},
	})

	// Event subscription: add every new peer to DHT routing table
	sub, err := routedHost.EventBus().Subscribe(&event.EvtPeerConnectednessChanged{}, eventbus.BufSize(256))
	if err != nil {
		log.Printf("subscribe: %v", err)
	} else {
		go func() {
			defer sub.Close()
			for evt := range sub.Out() {
				connEvt, ok := evt.(event.EvtPeerConnectednessChanged)
				if !ok {
					continue
				}
				if connEvt.Connectedness == network.Connected {
					time.Sleep(2 * time.Second)
					pid := connEvt.Peer
					found := kdht.RoutingTable().Find(pid) == pid
					if !found {
						added, err := kdht.RoutingTable().TryAddPeer(pid, true, true)
						log.Printf("TryAddPeer from event: added=%v err=%v rt_size=%d", added, err, kdht.RoutingTable().Size())
					}
				}
			}
		}()
	}

	// AutoNAT reachability subscription: when libp2p determines whether this
	// node is publicly reachable, record it and push the verdict to the app.
	// A "public" node can serve as a relay/bootstrap for other Neo peers.
	if rsub, rerr := routedHost.EventBus().Subscribe(&event.EvtLocalReachabilityChanged{}, eventbus.BufSize(8)); rerr != nil {
		log.Printf("reachability subscribe: %v", rerr)
	} else {
		go func() {
			defer rsub.Close()
			for evt := range rsub.Out() {
				rEvt, ok := evt.(event.EvtLocalReachabilityChanged)
				if !ok {
					continue
				}
				node.lock.Lock()
				node.reachability = rEvt.Reachability
				node.lock.Unlock()
				status := reachabilityString(rEvt.Reachability)
				log.Printf("reachability changed: %s", status)
				node.sendEvent(Event{Event: "reachability", Status: status})
			}
		}()
	}

	// mDNS for LAN discovery
	// NOTE: zeroconf uses netlink syscalls to enumerate network interfaces.
	// Android sandbox blocks these (route ip+net: netlinkrib: permission denied).
	// mDNS works on desktop/laptop but not on Android phones with this lib.
	// For LAN discovery on Android, use Kotlin NSD API (Android NSD/DNS-SD) instead.
	mdnsSvc := mdns.NewMdnsService(routedHost, mdns.ServiceName, node)
	if err := mdnsSvc.Start(); err != nil {
		log.Printf("mdns: start failed: %v", err)
	} else {
		log.Printf("mdns: started, discovering LAN peers")
		node.mdnsService = mdnsSvc
	}

	// Connect to bootstrap peers and add them to DHT routing table
	bootInfos = parseBootstrapPeers()
	log.Printf("bootstrap peers: %d", len(bootInfos))
	if len(bootInfos) > 0 {
		bootCtx, bootCancel := context.WithTimeout(ctx, 15*time.Second)
		for _, pi := range bootInfos {
			log.Printf("connecting to bootstrap %s", pi.ID)
			if err := routedHost.Connect(bootCtx, pi); err != nil {
				log.Printf("bootstrap %s: %v", pi.ID, err)
			} else {
				log.Printf("connected to bootstrap %s", pi.ID)
				// Store bootstrap peer ID for later use
				node.bootPeerIDs = append(node.bootPeerIDs, pi.ID)
				// Add to DHT routing table so Bootstrap can use them
				added, err := kdht.RoutingTable().TryAddPeer(pi.ID, true, true)
				log.Printf("TryAddPeer: added=%v, err=%v, rt_size=%d", added, err, kdht.RoutingTable().Size())
				// If this bootstrap is another Neo node, promote it to a full
				// gossip peer so posts sync directly over the connection.
				go node.promoteBootstrapToPeer(pi.ID)
			}
		}
		bootCancel()
	}

	// Test routing table contents after adding bootstrap
	log.Printf("bootstrapped: rt_size=%d, host_addrs=%v", kdht.RoutingTable().Size(), basicHost.Addrs())

	// Offer relay service (if publicly reachable)
	go func() {
		_, err := relay.New(routedHost)
		if err != nil {
			log.Printf("relay service: %v", err)
		}
	}()

	// Set up GossipSub for content routing. This is the global content
	// fabric: any phone subscribed to the topic receives every message
	// published by every other subscribed phone, delivered through the
	// libp2p mesh (and through the IPFS AutoRelay for NAT'd peers).
	ps, err := pubsub.NewGossipSub(
		ctx,
		routedHost,
		pubsub.WithMessageSignaturePolicy(pubsub.StrictSign),
	)
	if err != nil {
		return nil, fmt.Errorf("new gossipsub: %w", err)
	}
	node.pubsub = ps

	t, err := ps.Join(NeoTopic)
	if err != nil {
		return nil, fmt.Errorf("join topic %s: %w", NeoTopic, err)
	}
	node.topic = t

	nodeSub, err := t.Subscribe()
	if err != nil {
		return nil, fmt.Errorf("subscribe topic %s: %w", NeoTopic, err)
	}
	node.topicSub = nodeSub
	log.Printf("gossipsub: joined topic %s", NeoTopic)

	// Drain pubsub messages and forward them to the Kotlin app as
	// `topic_message` events. We skip messages that originated from our
	// own peer ID so we don't echo our own broadcasts back to ourselves.
	go func() {
		for {
			msg, err := nodeSub.Next(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("gossipsub: next: %v", err)
				continue
			}
			if msg.ReceivedFrom == routedHost.ID() {
				continue
			}
			node.sendEvent(Event{
				Event:   "topic_message",
				PeerID:  msg.ReceivedFrom.String(),
				Topic:   msg.GetTopic(),
				Message: string(msg.Data),
			})
		}
	}()

	// RT monitor
	if len(bootInfos) > 0 {
		bootID := bootInfos[0].ID
		go func() {
			prevSize := kdht.RoutingTable().Size()
			prevBoot := kdht.RoutingTable().Find(bootID) == bootID
			rtTick := time.NewTicker(500 * time.Millisecond)
			defer rtTick.Stop()
			for {
				select {
				case <-ctx.Done():
					return
				case <-rtTick.C:
					sz := kdht.RoutingTable().Size()
					inRT := kdht.RoutingTable().Find(bootID) == bootID
					conns := len(basicHost.Network().ConnsToPeer(bootID))
					if sz != prevSize || inRT != prevBoot {
						log.Printf("RT MONITOR: size=%d (was %d), boot_in_rt=%v (was %v), conns=%d", sz, prevSize, inRT, prevBoot, conns)
						prevSize, prevBoot = sz, inRT
					}
				}
			}
		}()
	}

	// Discovery loop
	go node.discoveryLoop()
	go node.reconnectLoop()

	// Keepalive loop
	go node.keepaliveLoop()

	// Bootstrap supervisor (auto-reconnect)
	go node.bootstrapSupervisor()

	return node, nil
}

func neoDiscoveryCID() cid.Cid {
	h, err := multihash.Sum([]byte("neo-social-app-discovery-v1-2026-06-12"), multihash.SHA2_256, -1)
	if err != nil {
		panic(err)
	}
	return cid.NewCidV1(cid.Raw, h)
}

var discoveryCID = neoDiscoveryCID()

func (n *Libp2pNode) dialAndServe(pid peer.ID) {
	defer n.endDial(pid.String())
	cctx, cancel := context.WithTimeout(n.ctx, 15*time.Second)
	defer cancel()
	err := n.host.Connect(cctx, peer.AddrInfo{ID: pid})
	if err == nil {
		log.Printf("discover: direct connected to %s", pid)
		gCtx, gCancel := context.WithTimeout(n.ctx, 5*time.Second)
		s, gErr := n.host.NewStream(gCtx, pid, protocol.ID(NeoProtocolID))
		gCancel()
		if gErr == nil {
			// A DIRECT libp2p stream is bidirectional, so we MUST run a reader
			// loop on it — otherwise the initiator sends posts but never
			// RECEIVES the peer's, which is the one-directional LAN-sync bug.
			// serviceStreamFor registers it, emits peer_connected, pumps inbound
			// lines, and blocks until the stream closes.
			n.serviceStreamFor(pid.String(), s)
			return
		}
		n.sendEvent(Event{Event: "peer_connected", PeerID: pid.String()})
		return
	}
	log.Printf("discover: direct connect to %s failed: %v", pid, err)
	// Try PX directly with peer to exchange addresses (incl. relay)
	pxCtx, pxCancel := context.WithTimeout(n.ctx, 10*time.Second)
	pxStream, pxErr := n.host.NewStream(pxCtx, pid, protocol.ID("/neo/px/1.0.0"))
	pxCancel()
	if pxErr == nil {
		log.Printf("discover: PX exchange with %s succeeded, retrying connect", pid)
		pxStream.Close()
		// Retry connect after PX may have updated peerstore with relay addresses
		rctx, rcancel := context.WithTimeout(n.ctx, 15*time.Second)
		rerr := n.host.Connect(rctx, peer.AddrInfo{ID: pid})
		rcancel()
		if rerr == nil {
			log.Printf("discover: connected to %s after PX", pid)
			rctx2, rcancel2 := context.WithTimeout(n.ctx, 5*time.Second)
			s, gErr := n.host.NewStream(rctx2, pid, protocol.ID(NeoProtocolID))
			rcancel2()
			if gErr == nil {
				// Bidirectional direct stream — service with a reader loop so
				// sync works both ways (see direct-connect path above).
				n.serviceStreamFor(pid.String(), s)
				return
			}
			n.sendEvent(Event{Event: "peer_connected", PeerID: pid.String()})
			return
		}
		log.Printf("discover: connect after PX to %s failed: %v", pid, rerr)
	} else {
		log.Printf("discover: PX with %s failed: %v", pid, pxErr)
	}
	// Fall back to proxy through custom bootstrap.
	// BOTH peers open their OWN outbound send-tunnel (no tiebreaker):
	// each direction is a separate forward-only relayed stream, because
	// the reverse direction of a relayed stream does not deliver reads.
	// I SEND on the tunnel I open (registered in activeStreams); I
	// RECEIVE the peer's posts on the peer's own outbound tunnel, which
	// arrives here as an inbound stream serviced read-only.
	n.customBootMu.Lock()
	proxyID := peer.ID("")
	if len(n.customBootIDs) > 0 {
		proxyID = n.customBootIDs[0]
	}
	n.customBootMu.Unlock()
	if proxyID == "" {
		return
	}
	pCtx, pCancel := context.WithTimeout(n.ctx, 30*time.Second)
	defer pCancel()
	s, pErr := n.host.NewStream(pCtx, proxyID, protocol.ID("/neo/proxy/1.0.0"))
	if pErr != nil {
		log.Printf("proxy: open stream: %v", pErr)
		return
	}
	// Include our own id as src so the destination knows who is connecting
	// and can emit peer_connected + reply over the same tunnel. The relay
	// forwards this header verbatim to the destination.
	req := map[string]string{"target": pid.String(), "src": n.host.ID().String()}
	hdr, _ := json.Marshal(req)
	if _, err := s.Write(append(hdr, '\n')); err != nil {
		log.Printf("proxy: write header: %v", err)
		s.Close()
		return
	}
	log.Printf("proxy: opened outbound send-tunnel to %s via bootstrap", pid)
	// Register as the OUTBOUND send-tunnel to pid; we only SEND on it. The
	// peer's posts arrive on the peer's OWN outbound tunnel (an inbound stream
	// serviced read-only). The reverse-read here yields nothing but usefully
	// detects close so the discovery loop re-dials. Blocks until close.
	n.serviceStreamFor(pid.String(), s)
}

// reconnectLoop closes the cross-network "30s gap": when a proxy send-tunnel
// drops, the main discoveryLoop only re-dials on its next 30s DHT tick, so the
// peer appears to flap offline for up to half a minute. This loop ticks every
// few seconds over peers we already know (learned via PX / relayed
// announcements) and immediately re-dials any that have no active stream — using
// the SAME dialAndServe path as discovery, so steady-state behavior is
// unchanged. tryBeginDial dedupes against in-flight dials and live tunnels, so a
// healthy peer is never re-dialed; only a genuinely dropped one is.
func (n *Libp2pNode) reconnectLoop() {
	ticker := time.NewTicker(7 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-n.ctx.Done():
			return
		case <-ticker.C:
			// Snapshot known peers + their cached addrs from the PX table.
			n.pxMu.Lock()
			known := make(map[peer.ID][]string, len(n.peerAddrs))
			for pid, addrs := range n.peerAddrs {
				known[pid] = addrs
			}
			n.pxMu.Unlock()

			for pid, addrs := range known {
				if pid == n.host.ID() {
					continue
				}
				// Never treat a bootstrap/relay as a regular peer to proxy-dial.
				isBoot := false
				n.customBootMu.Lock()
				for _, bp := range n.customBootIDs {
					if bp == pid {
						isBoot = true
						break
					}
				}
				n.customBootMu.Unlock()
				if !isBoot {
					for _, bp := range n.bootPeerIDs {
						if bp == pid {
							isBoot = true
							break
						}
					}
				}
				if isBoot {
					continue
				}
				// Only act on peers with no live send-tunnel / dial in flight.
				if !n.tryBeginDial(pid.String()) {
					continue
				}
				// Seed the peerstore with cached addrs so the direct-connect leg
				// of dialAndServe has something to try before falling back to the
				// bootstrap proxy.
				for _, a := range addrs {
					if maddr, err := ma.NewMultiaddr(a); err == nil {
						n.host.Peerstore().AddAddr(pid, maddr, peerstore.TempAddrTTL)
					}
				}
				log.Printf("reconnect: re-dialing dropped peer %s", pid)
				go n.dialAndServe(pid)
			}
		}
	}
}

func (n *Libp2pNode) discoveryLoop() {
	// Announce our presence via DHT provider records
	go func() {
		for i := 0; i < 5; i++ {
			log.Printf("provide: announcing via DHT (attempt %d)", i+1)
			if err := n.dht.Provide(n.ctx, discoveryCID, true); err != nil {
				log.Printf("provide: %v", err)
			} else {
				log.Printf("provide: announced successfully")
				break
			}
			time.Sleep(30 * time.Second)
		}
		for {
			log.Printf("provide: announcing via DHT")
			if err := n.dht.Provide(n.ctx, discoveryCID, true); err != nil {
				log.Printf("provide: %v", err)
			} else {
				log.Printf("provide: announced successfully")
			}
			time.Sleep(10 * time.Minute)
		}
	}()

	// Periodically re-announce via PX to catch peers that connected after us
	go func() {
		for {
			time.Sleep(30 * time.Second)
			n.customBootMu.Lock()
			ids := make([]peer.ID, len(n.customBootIDs))
			copy(ids, n.customBootIDs)
			n.customBootMu.Unlock()
			for _, pid := range ids {
				if n.host.Network().Connectedness(pid) != network.Connected {
					continue
				}
				annCtx, annCancel := context.WithTimeout(n.ctx, 10*time.Second)
				s, err := n.host.NewStream(annCtx, pid, protocol.ID("/neo/px/1.0.0"))
				if err != nil {
					annCancel()
					continue
				}
				var addrs []string
				for _, a := range n.host.Addrs() {
					addrs = append(addrs, a.String())
				}
				relayIP := getBootstrapRelayIP()
				relayAddr := fmt.Sprintf("/ip4/%s/tcp/4001/p2p-circuit/p2p/%s", relayIP, n.host.ID().String())
				addrs = append(addrs, relayAddr)
				ann := map[string]interface{}{
					"peer_id": n.host.ID().String(),
					"addrs":   addrs,
				}
				if err := json.NewEncoder(s).Encode(ann); err != nil {
					s.Close()
					annCancel()
					continue
				}
				s.Close()
				annCancel()
				log.Printf("px: re-announced %d addrs to custom bootstrap %s", len(addrs), pid)
			}
		}
	}()

	time.Sleep(20 * time.Second)

	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-n.ctx.Done():
			return
		case <-ticker.C:
			discCtx, discCancel := context.WithTimeout(n.ctx, 15*time.Second)
			peers, err := n.dht.FindProviders(discCtx, discoveryCID)
			discCancel()
			if err != nil {
				log.Printf("discover: findproviders err=%v", err)
				continue
			}
			log.Printf("discover: found %d providers", len(peers))
			log.Printf("discover: LOOP_START about to iterate %d peers", len(peers))
			for idx, pi := range peers {
				log.Printf("discover:   [IDX=%d] provider=%s addrs=%v self=%v", idx, pi.ID.String(), pi.Addrs, pi.ID == n.host.ID())
				if pi.ID == n.host.ID() {
					log.Printf("discover:   [IDX=%d] SKIP self", idx)
					continue
				}
				var isBootstrap bool
				for _, bp := range n.bootPeerIDs {
					if bp == pi.ID {
						isBootstrap = true
						break
					}
				}
				if isBootstrap {
					continue
				}
				// Single-flight guard: skip if already connected or a dial is in
				// flight. Prevents racing duplicate tunnels that flap the peer.
				if !n.tryBeginDial(pi.ID.String()) {
					continue
				}
				// Try direct connect first (hole-punching + circuit relay), fall back to PX then proxy
				go n.dialAndServe(pi.ID)
			}
		}
	}

}

func (n *Libp2pNode) keepaliveLoop() {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-n.ctx.Done():
			return
		case <-ticker.C:
			// Snapshot streams, then ping each. A write error means the stream
			// (or the proxy tunnel behind it) is dead — remove it and emit
			// peer_disconnected so the discovery loop re-dials. Without this a
			// stale entry makes sendMessage "succeed" into a dead tunnel, so
			// posts silently vanish (the cause of one-directional sync).
			n.lock.RLock()
			snapshot := make(map[string]network.Stream, len(n.activeStreams))
			for k, v := range n.activeStreams {
				snapshot[k] = v
			}
			n.lock.RUnlock()
			for pid, s := range snapshot {
				if _, err := s.Write([]byte("\n")); err != nil {
					log.Printf("keepalive: stream to %s dead (%v), removing", pid, err)
					n.lock.Lock()
					if n.activeStreams[pid] == s {
						delete(n.activeStreams, pid)
					}
					n.lock.Unlock()
					s.Reset()
					n.sendEvent(Event{Event: "peer_disconnected", PeerID: pid})
				}
			}
		}
	}
}

// promoteBootstrapToPeer turns a connected bootstrap peer that speaks the Neo
// protocols (i.e. another phone, not a public IPFS DHT server) into a
// first-class gossip peer: it probes /neo/px, announces our addresses, then
// opens a /neo/gossip stream and emits peer_connected. Without this a
// QR-connected phone stays invisible to broadcastPost (connectedPeers stays
// empty and "0 peers connected" is shown), so posts only had the slow,
// unreliable DHT-discovery path to sync over.
func (n *Libp2pNode) promoteBootstrapToPeer(pid peer.ID) {
	if pid == n.host.ID() {
		return
	}
	// Already promoted?
	n.lock.RLock()
	_, hasStream := n.activeStreams[pid.String()]
	n.lock.RUnlock()
	if hasStream {
		return
	}

	// Probe /neo/px — only Neo nodes speak it; public IPFS peers will error out.
	pxCtx, pxCancel := context.WithTimeout(n.ctx, 10*time.Second)
	pxStream, err := n.host.NewStream(pxCtx, pid, protocol.ID("/neo/px/1.0.0"))
	pxCancel()
	if err != nil {
		log.Printf("promote: %s is not a Neo node (px: %v)", pid, err)
		return
	}
	n.customBootMu.Lock()
	known := false
	for _, id := range n.customBootIDs {
		if id == pid {
			known = true
			break
		}
	}
	if !known {
		n.customBootIDs = append(n.customBootIDs, pid)
	}
	n.customBootMu.Unlock()

	var addrs []string
	for _, a := range n.host.Addrs() {
		addrs = append(addrs, a.String())
	}
	relayAddr := fmt.Sprintf("/ip4/%s/tcp/4001/p2p-circuit/p2p/%s", getBootstrapRelayIP(), n.host.ID().String())
	addrs = append(addrs, relayAddr)
	_ = json.NewEncoder(pxStream).Encode(map[string]interface{}{
		"peer_id": n.host.ID().String(),
		"addrs":   addrs,
	})
	pxStream.Close()

	// Open the gossip stream — this is what makes it a real Neo peer.
	gCtx, gCancel := context.WithTimeout(n.ctx, 8*time.Second)
	gs, gErr := n.host.NewStream(gCtx, pid, protocol.ID(NeoProtocolID))
	gCancel()
	if gErr != nil {
		log.Printf("promote: open gossip stream to %s: %v", pid, gErr)
		return
	}
	log.Printf("promote: %s promoted to Neo gossip peer", pid)
	// Service the outbound stream exactly like an inbound one: this emits
	// peer_connected, keeps it in activeStreams, and reads inbound lines so
	// sendMessage can write to it. Blocks until the stream closes.
	n.serviceStream(gs)
}

// bootstrapSupervisor monitors bootstrap connections and reconnects with exp backoff
func (n *Libp2pNode) bootstrapSupervisor() {
	backoff := time.Second
	const maxBackoff = 60 * time.Second

	for {
		select {
		case <-n.ctx.Done():
			return
		case <-time.After(backoff):
		}

		bootInfos := parseBootstrapPeers()
		if len(bootInfos) == 0 {
			backoff = minDuration(backoff*2, maxBackoff)
			continue
		}

		allConnected := true
		for _, pi := range bootInfos {
			if n.host.Network().Connectedness(pi.ID) != network.Connected {
				log.Printf("bootstrap supervisor: reconnecting to %s", pi.ID)
				ctx, cancel := context.WithTimeout(n.ctx, 15*time.Second)
				err := n.host.Connect(ctx, pi)
				cancel()
				if err != nil {
					log.Printf("bootstrap supervisor: connect %s: %v", pi.ID, err)
					allConnected = false
					continue
				}
				log.Printf("bootstrap supervisor: reconnected to %s", pi.ID)
				n.sendEvent(Event{Event: "bootstrap_reconnected", PeerID: pi.ID.String()})
			}
			// Whether freshly connected or already connected, make sure a Neo
			// bootstrap (another phone, e.g. a scanned QR) becomes a gossip peer.
			go n.promoteBootstrapToPeer(pi.ID)
		}

		if allConnected {
			backoff = 30 * time.Second // healthy: check every 30s
		} else {
			backoff = minDuration(backoff*2, maxBackoff)
		}
	}
}

func minDuration(a, b time.Duration) time.Duration {
	if a < b {
		return a
	}
	return b
}

func (n *Libp2pNode) stop() {
	n.cancel()
	n.lock.Lock()
	for _, s := range n.activeStreams {
		s.Close()
	}
	n.activeStreams = nil
	n.lock.Unlock()
	if n.topicSub != nil {
		n.topicSub.Cancel()
	}
	if n.topic != nil {
		n.topic.Close()
	}
	// pubsub has no Close; the cancelled context tears down its goroutines.
	if n.mdnsService != nil {
		n.mdnsService.Close()
	}
	n.dht.Close()
	n.host.Close()
}

// autoRelayPeerSource feeds AutoRelay with the user's configured bootstrap
// peers as relay candidates. AutoRelay calls this when it needs relays; we
// yield the current bootstrap list (which honors the QR/set_bootstrap override),
// so the phone reserves a circuit slot on the user's own bootstrap — never on
// public IPFS infrastructure.
func autoRelayPeerSource(ctx context.Context, num int) <-chan peer.AddrInfo {
	out := make(chan peer.AddrInfo, num)
	go func() {
		defer close(out)
		for _, pi := range parseBootstrapPeers() {
			if num <= 0 {
				return
			}
			select {
			case <-ctx.Done():
				return
			case out <- pi:
				num--
			}
		}
	}()
	return out
}

func parseBootstrapPeers() []peer.AddrInfo {
	var result []peer.AddrInfo
	for _, s := range bootstrapPeers {
		addr, err := ma.NewMultiaddr(s)
		if err != nil {
			continue
		}
		pi, err := peer.AddrInfoFromP2pAddr(addr)
		if err != nil {
			continue
		}
		result = append(result, *pi)
	}
	return result
}

func topicToCID(topic string) cid.Cid {
	h, err := multihash.Sum([]byte(topic), multihash.SHA2_256, -1)
	if err != nil {
		// SHA2-256 with default length should never fail
		panic(err)
	}
	return cid.NewCidV1(cid.Raw, h)
}

func getOutboundIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return ""
	}
	defer conn.Close()
	localAddr := conn.LocalAddr().(*net.UDPAddr)
	if localAddr == nil {
		return ""
	}
	ip := localAddr.IP
	if ip == nil || ip.IsLoopback() {
		return ""
	}
	return ip.String()
}

// -------- TCP command server --------

func main() {
	port := flag.Int("port", 9877, "TCP command port for IPC with Kotlin app")
	flag.Parse()

	listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", *port))
	if err != nil {
		log.Fatalf("listen on port %d: %v", *port, err)
	}
	log.Printf("neoserver v%s listening on 127.0.0.1:%d", Version, *port)

	// Accept one connection from Kotlin app
	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Printf("accept: %v", err)
			continue
		}
		go handleConnection(conn)
	}
}

func handleConnection(conn net.Conn) {
	defer conn.Close()
	log.Printf("Kotlin app connected from %s", conn.RemoteAddr())

	var node *Libp2pNode
	writer := bufio.NewWriter(conn)
	encoder := json.NewEncoder(writer)
	reader := bufio.NewReader(conn)
	var writeMu sync.Mutex

	// Helper to send events
	sendEvent := func(evt Event) {
		writeMu.Lock()
		defer writeMu.Unlock()
		if evt.Event == "peer_connected" {
			log.Printf("event: sending peer_connected for %s", evt.PeerID)
		}
		encoder.Encode(evt)
		writer.Flush()
	}

	sendEvent(Event{Event: "server_ready"})

	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			log.Printf("read command: %v", err)
			if node != nil {
				node.stop()
			}
			return
		}
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		var cmd Command
		if err := json.Unmarshal([]byte(line), &cmd); err != nil {
			sendEvent(Event{Event: "error", Error: fmt.Sprintf("bad json: %v", err)})
			continue
		}

		switch cmd.Cmd {
		case "init":
			if node != nil {
				node.stop()
				node = nil
			}
			// Parse private key
			var privBytes []byte
			if cmd.PrivKey != "" {
				var err error
				privBytes, err = base64.StdEncoding.DecodeString(cmd.PrivKey)
				if err != nil {
					sendEvent(Event{Event: "error", Error: fmt.Sprintf("bad privkey: %v", err)})
					continue
				}
			}
			ctx, cancel := context.WithCancel(context.Background())
			newNode, err := newNode(ctx, privBytes, cmd.Port)
			if err != nil {
				cancel()
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("init: %v", err)})
				continue
			}
			newNode.cancel = cancel
			newNode.eventWriter = func(evt Event) { sendEvent(evt) }
			node = newNode

			addrs := strings.Join(node.listenAddresses(), ",")
			sendEvent(Event{
				Event:  "ready",
				PeerID: node.host.ID().String(),
				Addrs:  addrs,
			})

		case "connect":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			addr, err := ma.NewMultiaddr(cmd.Multiaddr)
			if err != nil {
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("bad addr: %v", err)})
				continue
			}
			pi, err := peer.AddrInfoFromP2pAddr(addr)
			if err != nil {
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("bad addr: %v", err)})
				continue
			}
			cctx, cancel := context.WithTimeout(node.ctx, 15*time.Second)
			defer cancel()
			if err := node.host.Connect(cctx, *pi); err != nil {
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("connect: %v", err)})
				continue
			}
			sendEvent(Event{Event: "connected", PeerID: pi.ID.String()})

		case "send":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			if err := node.sendMessage(cmd.PeerID, cmd.Message); err != nil {
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("send: %v", err)})
			}

		case "broadcast":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			count := node.broadcastMessage(cmd.Message)
			sendEvent(Event{Event: "broadcast_result", Count: count})

		case "peers":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			peers := node.connectedPeers()
			sendEvent(Event{Event: "peers", Message: strings.Join(peers, ",")})

		case "listen_addrs":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			addrs := node.listenAddresses()
			sendEvent(Event{Event: "listen_addrs", Addrs: strings.Join(addrs, ",")})

		case "dht_count":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			sendEvent(Event{Event: "dht_count", Count: node.dhtCount()})

		case "peer_id":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			sendEvent(Event{Event: "peer_id", PeerID: node.host.ID().String()})

		case "connect_info":
			// Return this node's shareable identity for the "Connect to me" QR,
			// split into LAN (same-Wi-Fi) and WAN (cross-network) addresses so the
			// app can advertise the right one and tell the user whether they can
			// actually host peers across networks.
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			pidStr := node.host.ID().String()
			lan := make([]string, 0)
			wan := make([]string, 0)
			// host.Addrs() includes observed/relay addrs discovered via AutoNAT
			// and circuit reservations, not just our own listeners.
			seen := map[string]bool{}
			collect := func(s string) {
				if s == "" || seen[s] {
					return
				}
				seen[s] = true
				if strings.Contains(s, "/p2p-circuit") {
					wan = append(wan, s) // relay address: usable cross-network
					return
				}
				if strings.Contains(s, "/ip4/127.0.0.1") || strings.Contains(s, "/ip6/::1") {
					return
				}
				if isPrivateAddr(s) {
					lan = append(lan, s)
				} else {
					wan = append(wan, s)
				}
			}
			for _, a := range node.listenAddresses() {
				collect(a)
			}
			for _, a := range node.host.Addrs() {
				s := a.String()
				if !strings.Contains(s, "/p2p/") {
					s += "/p2p/" + pidStr
				}
				collect(s)
			}
			node.lock.RLock()
			status := reachabilityString(node.reachability)
			node.lock.RUnlock()
			// We can host peers across networks if we have a public or relay addr.
			canHost := len(wan) > 0 && status == "public"
			// Best single address to advertise: prefer WAN when hostable, else LAN.
			best := lan
			if canHost {
				best = append(append([]string{}, wan...), lan...)
			}
			sendEvent(Event{
				Event:    "connect_info",
				PeerID:   pidStr,
				Addrs:    strings.Join(best, ","),
				LanAddrs: strings.Join(lan, ","),
				WanAddrs: strings.Join(wan, ","),
				Status:   status,
				CanHost:  canHost,
			})

		case "stop":
			if node != nil {
				node.stop()
				node = nil
			}
			sendEvent(Event{Event: "stopped"})

		case "ping":
			sendEvent(Event{Event: "pong"})

		case "set_bootstrap":
			// Override the bootstrap peer list. An empty multiaddr reverts
			// to the built-in defaults.
			if cmd.Multiaddr == "" {
				bootstrapPeers = loadBootstrapPeers()
				log.Printf("bootstrap: override cleared, restored to %d default(s)", len(bootstrapPeers))
			} else {
				bootstrapPeers = []string{cmd.Multiaddr}
				log.Printf("bootstrap: override applied, 1 peer")
				// Connect + promote immediately rather than waiting for the next
				// supervisor tick (which may skip an already-connected peer that
				// has no gossip stream yet). This makes a scanned/linked peer
				// sync right away.
				if node != nil {
					if addr, err := ma.NewMultiaddr(cmd.Multiaddr); err == nil {
						if pi, err := peer.AddrInfoFromP2pAddr(addr); err == nil {
							go func(pi peer.AddrInfo) {
								cctx, cancel := context.WithTimeout(node.ctx, 15*time.Second)
								defer cancel()
								node.host.Peerstore().AddAddrs(pi.ID, pi.Addrs, peerstore.PermanentAddrTTL)
								if err := node.host.Connect(cctx, pi); err != nil {
									log.Printf("set_bootstrap: connect %s: %v", pi.ID, err)
									return
								}
								node.promoteBootstrapToPeer(pi.ID)
							}(*pi)
						}
					}
				}
			}

		case "publish":
			if node == nil {
				sendEvent(Event{Event: "error", Error: "not initialized"})
				continue
			}
			if node.topic == nil {
				sendEvent(Event{Event: "error", Error: "pubsub not initialized"})
				continue
			}
			if err := node.topic.Publish(node.ctx, []byte(cmd.Message)); err != nil {
				sendEvent(Event{Event: "error", Error: fmt.Sprintf("publish: %v", err)})
			} else {
				sendEvent(Event{Event: "published"})
			}

		default:
			sendEvent(Event{Event: "error", Error: fmt.Sprintf("unknown cmd: %s", cmd.Cmd)})
		}
	}
}
