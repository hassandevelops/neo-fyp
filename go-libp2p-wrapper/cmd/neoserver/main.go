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
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/libp2p/go-libp2p"
	"github.com/libp2p/go-libp2p/core/crypto"
	"github.com/libp2p/go-libp2p/core/host"
	"github.com/libp2p/go-libp2p/core/network"
	"github.com/libp2p/go-libp2p/core/peer"
	"github.com/libp2p/go-libp2p/core/protocol"
	"github.com/libp2p/go-libp2p/p2p/discovery/mdns"
	"github.com/libp2p/go-libp2p/p2p/host/routed"
	"github.com/libp2p/go-libp2p/p2p/protocol/circuitv2/relay"

	"github.com/ipfs/go-cid"
	dht "github.com/libp2p/go-libp2p-kad-dht"
	ds "github.com/ipfs/go-datastore"
	dsync "github.com/ipfs/go-datastore/sync"
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
	Event   string `json:"event"`
	PeerID  string `json:"peerID,omitempty"`
	Message string `json:"message,omitempty"`
	Addrs   string `json:"addrs,omitempty"`
	Count   int    `json:"count"`
	Error   string `json:"error,omitempty"`
}

// -------- Bootstrap peers --------
// Configurable via:
//   1. File: "bootstrap.conf" - one multiaddr per line
//   2. Env: NEO_BOOTSTRAP - comma-separated multiaddrs
//   3. Fallback: hardcoded default
var defaultBootstrapPeers = []string{
	"/ip4/192.168.100.180/tcp/4001/p2p/12D3KooWRr67qtQrb3aHNkjQM2fABs1QbJwLwzmNzAkWp1W8MFoh",
}

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
	if len(bootstrapPeers) == 0 {
		return "127.0.0.1"
	}
	addr, err := ma.NewMultiaddr(bootstrapPeers[0])
	if err != nil {
		return "127.0.0.1"
	}
	host, _ := ma.SplitFirst(addr)
	if host == nil {
		return "127.0.0.1"
	}
	_ = host // suppress unused warning
	return host.Value()
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
	bootPeerIDs   []peer.ID
	mdnsService   mdns.Service
}

// sendEvent writes a JSON event to the Kotlin app via TCP
func (n *Libp2pNode) sendEvent(evt Event) {
	if n.eventWriter != nil {
		n.eventWriter(evt)
	}
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
	peerID := s.Conn().RemotePeer().String()
	defer s.Close()
	defer func() {
		n.lock.Lock()
		delete(n.activeStreams, peerID)
		n.lock.Unlock()
		n.sendEvent(Event{Event: "peer_disconnected", PeerID: peerID})
	}()

	n.lock.Lock()
	if existing, exists := n.activeStreams[peerID]; exists && existing != s {
		// New connection from same peer - close old one
		existing.Close()
	}
	n.activeStreams[peerID] = s
	n.lock.Unlock()

	n.sendEvent(Event{Event: "peer_connected", PeerID: peerID})

	reader := bufio.NewReader(s)
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err != io.EOF {
				log.Printf("proxy read error from %s: %v", peerID, err)
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
	peerID := s.Conn().RemotePeer().String()
	defer s.Close()
	defer func() {
		n.lock.Lock()
		delete(n.activeStreams, peerID)
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
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err != io.EOF {
				log.Printf("read error from %s: %v", peerID, err)
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
		if s, ok := a.(string); ok {
			addrs = append(addrs, s)
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
	var filtered []ma.Multiaddr
	for _, a := range addrs {
		ma, err := ma.NewMultiaddr(a)
		if err != nil {
			continue
		}
		s := ma.String()
		if strings.Contains(s, "/ip4/127.0.0.1") || strings.Contains(s, "/ip6/::1") {
			continue
		}
		filtered = append(filtered, ma)
	}
	if len(filtered) == 0 {
		log.Printf("px: no non-loopback addrs for %s", pid)
		return
	}
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
		libp2p.EnableHolePunching(),
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
		dht.DisableAutoRefresh(),
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
		bootPeerIDs:   make([]peer.ID, 0),
	}

	// Register stream handlers BEFORE connecting to bootstrap
	routedHost.SetStreamHandler(protocol.ID(NeoProtocolID), node.handleStream)
	routedHost.SetStreamHandler(protocol.ID("/neo/px/1.0.0"), node.handlePeerExchange)
	routedHost.SetStreamHandler(protocol.ID("/neo/proxy/1.0.0"), node.handleProxyStream)

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
				// Store bootstrap peer ID for later use (proxy connections)
				node.bootPeerIDs = append(node.bootPeerIDs, pi.ID)
				// Add to DHT routing table so Bootstrap can use them
				added, err := kdht.RoutingTable().TryAddPeer(pi.ID, true, true)
				log.Printf("TryAddPeer: added=%v, err=%v, rt_size=%d", added, err, kdht.RoutingTable().Size())
				// Announce our addresses to bootstrap for peer exchange
				go func() {
					annCtx, annCancel := context.WithTimeout(ctx, 10*time.Second)
					defer annCancel()
					s, err := routedHost.NewStream(annCtx, pi.ID, protocol.ID("/neo/px/1.0.0"))
					if err != nil {
						log.Printf("px: open stream: %v", err)
						return
					}
					defer s.Close()
					var addrs []string
					for _, a := range basicHost.Addrs() {
						addrs = append(addrs, a.String())
					}
					// Add OUR relay address for others to reach us via bootstrap
					relayIP := getBootstrapRelayIP()
					relayAddr := fmt.Sprintf("/ip4/%s/tcp/4001/p2p-circuit/p2p/%s", relayIP, basicHost.ID().String())
					addrs = append(addrs, relayAddr)
					ann := map[string]interface{}{
						"peer_id": basicHost.ID().String(),
						"addrs":   addrs,
					}
					if err := json.NewEncoder(s).Encode(ann); err != nil {
						log.Printf("px: encode: %v", err)
						return
					}
					log.Printf("px: announced %d addrs to bootstrap (including relay)", len(addrs))
				}()
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
						if !inRT {
							buf := make([]byte, 64*1024)
							n := runtime.Stack(buf, true)
							log.Printf("RT MONITOR: stack after removal:\n%s", buf[:n])
						}
					}
				}
			}
		}()
	}

	// Discovery loop
	go node.discoveryLoop()

	// Keepalive loop
	go node.keepaliveLoop()

	// Bootstrap supervisor (auto-reconnect)
	go node.bootstrapSupervisor()

	return node, nil
}

func neoDiscoveryCID() cid.Cid {
	h, err := multihash.Sum([]byte("neo-discovery-v1"), multihash.SHA2_256, -1)
	if err != nil {
		panic(err)
	}
	return cid.NewCidV1(cid.Raw, h)
}

var discoveryCID = neoDiscoveryCID()

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
			bootInfos := parseBootstrapPeers()
			for _, pi := range bootInfos {
				annCtx, annCancel := context.WithTimeout(n.ctx, 10*time.Second)
				s, err := n.host.NewStream(annCtx, pi.ID, protocol.ID("/neo/px/1.0.0"))
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
				log.Printf("px: re-announced %d addrs to bootstrap", len(addrs))
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
				n.lock.RLock()
				_, already := n.activeStreams[pi.ID.String()]
				n.lock.RUnlock()
				if already {
					continue
				}
				// Connect via bootstrap proxy
				go func(pid peer.ID) {
					cctx, cancel := context.WithTimeout(n.ctx, 30*time.Second)
					defer cancel()
					s, err := n.host.NewStream(cctx, n.bootPeerIDs[0], protocol.ID("/neo/proxy/1.0.0"))
					if err != nil {
						log.Printf("proxy: open stream: %v", err)
						return
					}
					req := map[string]string{"target": pid.String()}
					if err := json.NewEncoder(s).Encode(req); err != nil {
						log.Printf("proxy: encode: %v", err)
						s.Close()
						return
					}
					n.lock.Lock()
					n.activeStreams[pid.String()] = s
					n.lock.Unlock()
					log.Printf("proxy: sending peer_connected event for %s", pid.String())
					n.sendEvent(Event{Event: "peer_connected", PeerID: pid.String()})
					log.Printf("proxy: connected to %s via bootstrap", pid)
					// Keep stream open - close it when context is done
					go func() {
						<-n.ctx.Done()
						n.lock.Lock()
						delete(n.activeStreams, pid.String())
						n.lock.Unlock()
						s.Close()
					}()
				}(pi.ID)
			}
		}
	}

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
			bootInfos := parseBootstrapPeers()
			for _, pi := range bootInfos {
				annCtx, annCancel := context.WithTimeout(n.ctx, 10*time.Second)
				s, err := n.host.NewStream(annCtx, pi.ID, protocol.ID("/neo/px/1.0.0"))
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
				log.Printf("px: re-announced %d addrs to bootstrap", len(addrs))
			}
		}
	}()

	time.Sleep(20 * time.Second)

	discoverTicker := time.NewTicker(30 * time.Second)
	defer discoverTicker.Stop()
	for {
		select {
		case <-n.ctx.Done():
			return
		case <-discoverTicker.C:
			discCtx, discCancel := context.WithTimeout(n.ctx, 15*time.Second)
			peers, err := n.dht.FindProviders(discCtx, discoveryCID)
			discCancel()
			if err != nil {
				log.Printf("discover: findproviders err=%v", err)
				continue
			}
			log.Printf("discover: found %d providers", len(peers))
			log.Printf("discover: LOOP2_START about to iterate %d peers", len(peers))
			for idx, pi := range peers {
				log.Printf("discover:   [L2_IDX=%d] provider=%s addrs=%v", idx, pi.ID.String(), pi.Addrs)
				if pi.ID == n.host.ID() {
					log.Printf("discover:   [L2_IDX=%d] SKIP self", idx)
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
				n.lock.RLock()
				_, already := n.activeStreams[pi.ID.String()]
				n.lock.RUnlock()
				if already {
					continue
				}
				// Connect via bootstrap proxy
				go func(pid peer.ID) {
					cctx, cancel := context.WithTimeout(n.ctx, 30*time.Second)
					defer cancel()
					s, err := n.host.NewStream(cctx, n.bootPeerIDs[0], protocol.ID("/neo/proxy/1.0.0"))
					if err != nil {
						log.Printf("proxy: open stream: %v", err)
						return
					}
					req := map[string]string{"target": pid.String()}
					if err := json.NewEncoder(s).Encode(req); err != nil {
						log.Printf("proxy: encode: %v", err)
						s.Close()
						return
					}
					n.lock.Lock()
					n.activeStreams[pid.String()] = s
					n.lock.Unlock()
					log.Printf("proxy: sending peer_connected event for %s", pid.String())
					n.sendEvent(Event{Event: "peer_connected", PeerID: pid.String()})
					log.Printf("proxy: connected to %s via bootstrap", pid)
					// Keep stream open - close it when context is done
					go func() {
						<-n.ctx.Done()
						n.lock.Lock()
						delete(n.activeStreams, pid.String())
						n.lock.Unlock()
						s.Close()
					}()
				}(pi.ID)
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
			n.lock.RLock()
			for _, s := range n.activeStreams {
				s.Write([]byte("\n"))
			}
			n.lock.RUnlock()
		}
	}
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
				} else {
					log.Printf("bootstrap supervisor: reconnected to %s", pi.ID)
					n.sendEvent(Event{Event: "bootstrap_reconnected", PeerID: pi.ID.String()})
				}
			}
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
	if n.mdnsService != nil {
		n.mdnsService.Close()
	}
	n.dht.Close()
	n.host.Close()
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

		case "stop":
			if node != nil {
				node.stop()
				node = nil
			}
			sendEvent(Event{Event: "stopped"})

		case "ping":
			sendEvent(Event{Event: "pong"})

		default:
			sendEvent(Event{Event: "error", Error: fmt.Sprintf("unknown cmd: %s", cmd.Cmd)})
		}
	}
}
