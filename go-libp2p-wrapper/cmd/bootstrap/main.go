package main

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"os/signal"
	"sync"
	"syscall"
	"time"

	ds "github.com/ipfs/go-datastore"
	dsync "github.com/ipfs/go-datastore/sync"
	"github.com/libp2p/go-libp2p"
	dht "github.com/libp2p/go-libp2p-kad-dht"
	"github.com/libp2p/go-libp2p/core/crypto"
	"github.com/libp2p/go-libp2p/core/event"
	"github.com/libp2p/go-libp2p/core/network"
	"github.com/libp2p/go-libp2p/core/peer"
	"github.com/libp2p/go-libp2p/core/protocol"
	"github.com/skip2/go-qrcode"
	"github.com/libp2p/go-libp2p/p2p/host/eventbus"
	"github.com/libp2p/go-libp2p/p2p/protocol/circuitv2/relay"
)

const (
	PeerExchangeProtocol = protocol.ID("/neo/px/1.0.0")
	ProxyProtocol        = protocol.ID("/neo/proxy/1.0.0")
)

type PeerAnnounce struct {
	PeerID string   `json:"peer_id"`
	Addrs  []string `json:"addrs"`
}

func main() {
	keyFile := "bootstrap.key"
	port := 4001
	if p := os.Getenv("BOOTSTRAP_PORT"); p != "" {
		fmt.Sscanf(p, "%d", &port)
	}

	var privKey crypto.PrivKey
	if data, err := os.ReadFile(keyFile); err == nil && len(data) > 0 {
		privKey, err = crypto.UnmarshalPrivateKey(data)
		if err != nil {
			privKey = nil
		}
	}
	if privKey == nil {
		var err error
		privKey, _, err = crypto.GenerateEd25519Key(rand.Reader)
		if err != nil {
			log.Fatalf("generate key: %v", err)
		}
		if data, err := crypto.MarshalPrivateKey(privKey); err == nil {
			os.WriteFile(keyFile, data, 0600)
		}
	}

	listenAddr := fmt.Sprintf("/ip4/0.0.0.0/tcp/%d", port)

	host, err := libp2p.New(
		libp2p.ListenAddrStrings(listenAddr),
		libp2p.Identity(privKey),
		libp2p.DefaultTransports,
		libp2p.DefaultMuxers,
		libp2p.DefaultSecurity,
		libp2p.EnableRelay(),
		libp2p.EnableHolePunching(),
		libp2p.NATPortMap(),
	)
	if err != nil {
		log.Fatalf("new host: %v", err)
	}
	defer host.Close()

	// Notifiee: when a peer connects, record its remote address in the
	// peerstore. This is critical when the peer is behind NAT and the only
	// reachable address is the one it connected FROM (not its own listen
	// addresses which are loopback / VPN tunnel).
	host.Network().Notify(&network.NotifyBundle{
		ConnectedF: func(_ network.Network, c network.Conn) {
			remotePID := c.RemotePeer()
			remoteAddr := c.RemoteMultiaddr()
			// Add the remote address to the peerstore so future dials can use it
			host.Peerstore().AddAddr(remotePID, remoteAddr, 24*time.Hour)
			log.Printf("notifiee: peer=%s connected from %s (added to peerstore)", remotePID, remoteAddr)
		},
		DisconnectedF: func(_ network.Network, c network.Conn) {
			log.Printf("notifiee: peer=%s disconnected from %s", c.RemotePeer(), c.RemoteMultiaddr())
		},
	})

	ctx := context.Background()

	dstore := dsync.MutexWrap(ds.NewMapDatastore())
	kdht, err := dht.New(ctx, host,
		dht.Datastore(dstore),
		dht.Mode(dht.ModeServer),
		dht.DisableAutoRefresh(),
	)
	if err != nil {
		log.Fatalf("new dht: %v", err)
	}
	defer kdht.Close()

	// Start relay service for p2p-circuit relay
	_, err = relay.New(host)
	if err != nil {
		log.Fatalf("relay service: %v", err)
	}
	log.Printf("relay service started")

	// Detect LAN IP for relay addresses (must be before PX handler)
	lanIP := getOutboundIP()
	log.Printf("detected LAN IP: %s", lanIP)

	// Publicly reachable relay base, e.g. "/ip4/159.223.110.159/tcp/52804" or
	// "/dns4/bore.pub/tcp/52804". Set this when the bootstrap is behind a tunnel
	// or NAT so the relay addresses handed to peers are actually dialable. When
	// unset, falls back to the detected LAN IP on the listen port (LAN-only).
	publicRelayBase := os.Getenv("NEO_PUBLIC_ADDR")
	if publicRelayBase != "" {
		log.Printf("using public relay base: %s", publicRelayBase)
	}

	// Peer exchange: relay announcements between connected peers
	var (
		pxMu     sync.Mutex
		peerAddrs = make(map[peer.ID][]string)
	)

	host.SetStreamHandler(PeerExchangeProtocol, func(s network.Stream) {
		defer s.Close()
		var ann PeerAnnounce
		if err := json.NewDecoder(s).Decode(&ann); err != nil {
			log.Printf("px: decode error: %v", err)
			return
		}
		pid, err := peer.Decode(ann.PeerID)
		if err != nil {
			log.Printf("px: invalid peer id: %v", err)
			return
		}
		pxMu.Lock()
		peerAddrs[pid] = ann.Addrs
		// Broadcast to other connected peers
		for otherPid := range peerAddrs {
			if otherPid == pid {
				continue
			}
			go func(target peer.ID) {
				ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
				defer cancel()
				s, err := host.NewStream(ctx, target, PeerExchangeProtocol)
				if err != nil {
					log.Printf("px: relay to %s: %v", target, err)
					return
				}
				defer s.Close()
				// Add relay address for the target to reach the announcing peer
				relayAddrs := make([]string, len(ann.Addrs))
				copy(relayAddrs, ann.Addrs)
				// Build a circuit-relay address that points at how peers actually
				// reach THIS bootstrap. Prefer the configured public base (tunnel/
				// public IP); fall back to LAN IP on the listen port.
				relayBase := publicRelayBase
				if relayBase == "" {
					relayIP := lanIP
					if relayIP == "" {
						relayIP = "127.0.0.1"
					}
					relayBase = fmt.Sprintf("/ip4/%s/tcp/%d", relayIP, port)
				}
				// Format: <relay-base>/p2p/<BOOTSTRAP_ID>/p2p-circuit/p2p/<DEST_PEER_ID>
				// base → how to reach the relay (this bootstrap); then the relay's
				// own peer id; then the circuit hop to the announcing peer.
				relayAddr := fmt.Sprintf("%s/p2p/%s/p2p-circuit/p2p/%s", relayBase, host.ID().String(), ann.PeerID)
				relayAddrs = append(relayAddrs, relayAddr)
				relayAnn := PeerAnnounce{PeerID: ann.PeerID, Addrs: relayAddrs}
				if err := json.NewEncoder(s).Encode(relayAnn); err != nil {
					log.Printf("px: relay encode: %v", err)
					return
				}
				log.Printf("px: relayed %s to %s with relay addr", pid, target)
			}(otherPid)
		}
		pxMu.Unlock()
	})

	// Stream proxy: relay data between connected peers.
	// IMPORTANT: do not close s on function return — that would kill the channel
	// mid-conversation. Instead, wait for both io.Copy directions to finish
	// before closing the streams.
	host.SetStreamHandler(ProxyProtocol, func(s network.Stream) {
		log.Printf("proxy: handler entered, remote=%s", s.Conn().RemotePeer())
		// Read the header as a single newline-terminated line (the initiator
		// sends it via json.Encode, which appends '\n'). We must FORWARD this
		// header verbatim to the target so the destination phone knows the
		// original src and treats the tunnel as a gossip session — not consume
		// it. Any gossip bytes that arrived with the header stay in `buffered`.
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
		targetStr, ok := msg["target"].(string)
		if !ok {
			log.Printf("proxy: missing target")
			s.Close()
			return
		}
		target, err := peer.Decode(targetStr)
		if err != nil {
			log.Printf("proxy: invalid target: %v", err)
			s.Close()
			return
		}

		// Open a stream to the target peer
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		log.Printf("proxy: dialing target=%s with addrs=%v", target, host.Peerstore().Addrs(target))
		targetStream, err := host.NewStream(ctx, target, ProxyProtocol)
		cancel()
		if err != nil {
			log.Printf("proxy: dial target %s: %v", target, err)
			s.Close()
			return
		}
		// Forward the header line to the target FIRST, then relay the rest.
		if _, werr := targetStream.Write([]byte(strings.TrimSpace(headerLine) + "\n")); werr != nil {
			log.Printf("proxy: forward header: %v", werr)
			s.Close()
			targetStream.Close()
			return
		}
		log.Printf("proxy: connected to target %s, relaying (header forwarded)", target)

		// Copy data bidirectionally. Use a wait group so we only close the
		// streams once BOTH directions have finished. Read the src->target
		// direction from `buffered` so any bytes pulled in with the header
		// are not lost.
		srcID := s.Conn().RemotePeer()
		var wg sync.WaitGroup
		wg.Add(2)
		go func() {
			defer wg.Done()
			nb, _ := io.Copy(targetStream, buffered)
			log.Printf("proxy: %s->%s copied %d bytes (src->target)", srcID, target, nb)
		}()
		go func() {
			defer wg.Done()
			nb, _ := io.Copy(s, targetStream)
			log.Printf("proxy: %s->%s copied %d bytes (target->src)", target, srcID, nb)
		}()
		wg.Wait()
		s.Close()
		targetStream.Close()
	})

	// Listen for connection events and add peers to the routing table
	// This bypasses the DHT's lookup check which fails for small networks
	sub, err := host.EventBus().Subscribe(&event.EvtPeerConnectednessChanged{}, eventbus.BufSize(256))
	if err != nil {
		log.Printf("subscribe: %v", err)
	} else {
		go func() {
			defer sub.Close()
			for {
				e, more := <-sub.Out()
				if !more {
					return
				}
				evt := e.(event.EvtPeerConnectednessChanged)
				if evt.Connectedness == network.Connected {
					// Wait for identification to complete
					time.Sleep(2 * time.Second)
					pid := evt.Peer
					found := kdht.RoutingTable().Find(pid) == pid
					log.Printf("connection event: peer=%s connected, rt_find=%v, rt_size=%d", pid, found, kdht.RoutingTable().Size())
					if !found {
						added, err := kdht.RoutingTable().TryAddPeer(pid, true, true)
						log.Printf("TryAddPeer: added=%v err=%v rt_size=%d", added, err, kdht.RoutingTable().Size())
					}
				}
			}
		}()
	}

	go func() {
		for {
			time.Sleep(30 * time.Second)
			log.Printf("RT status: size=%d, peers=%v", kdht.RoutingTable().Size(), kdht.RoutingTable().ListPeers())
		}
	}()

	pid := host.ID().String()
	// lanIP already computed above for relay addresses

	// Pick the public-facing multiaddr to advertise in the QR code.
	// Priority: NEO_PUBLIC_ADDR (an internet-reachable base like a tunnel or
	// public IP) so phones on OTHER networks can connect; then LAN IP for
	// same-Wi-Fi; then a non-loopback host address.
	var publicMultiaddr string
	if publicRelayBase != "" {
		publicMultiaddr = publicRelayBase + "/p2p/" + pid
	} else if lanIP != "" {
		publicMultiaddr = fmt.Sprintf("/ip4/%s/tcp/%d/p2p/%s", lanIP, port, pid)
	} else {
		for _, a := range host.Addrs() {
			s := a.String()
			if !strings.Contains(s, "127.0.0.1") &&
				!strings.Contains(s, "::1") &&
				!strings.Contains(s, "0.0.0.0") &&
				!strings.Contains(s, "::") {
				publicMultiaddr = s + "/p2p/" + pid
				break
			}
		}
	}
	if publicMultiaddr == "" {
		publicMultiaddr = fmt.Sprintf("/ip4/127.0.0.1/tcp/%d/p2p/%s", port, pid)
	}
	deepLink := "neo://bootstrap?maddr=" + url.QueryEscape(publicMultiaddr)

	fmt.Printf("\n=== BOOTSTRAP NODE ===\n")
	fmt.Printf("PeerID: %s\n", pid)
	for _, a := range host.Addrs() {
		fmt.Printf("Addr: %s/p2p/%s\n", a.String(), pid)
	}
	fmt.Printf("Key file: %s\n", keyFile)
	fmt.Printf("Listening on port %d\n", port)
	fmt.Printf("========================\n\n")

	fmt.Printf("Public multiaddr (paste into Neo Settings → Network → Scan QR):\n")
	fmt.Printf("  %s\n\n", publicMultiaddr)
	fmt.Printf("Deep link:\n  %s\n\n", deepLink)

	// HTTP server for QR code serving
	httpPort := 4002
	if p := os.Getenv("BOOTSTRAP_HTTP_PORT"); p != "" {
		fmt.Sscanf(p, "%d", &httpPort)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		fmt.Fprintf(w, qrHTML, publicMultiaddr, deepLink)
	})
	mux.HandleFunc("/qr.png", func(w http.ResponseWriter, r *http.Request) {
		png, err := qrcode.Encode(deepLink, qrcode.Medium, 512)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "image/png")
		w.Write(png)
	})
	go func() {
		addr := fmt.Sprintf("0.0.0.0:%d", httpPort)
		log.Printf("HTTP QR server listening on %s (try http://%s:%d/)", addr, lanIP, httpPort)
		if err := http.ListenAndServe(addr, mux); err != nil {
			log.Printf("HTTP server exited: %v", err)
		}
	}()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	log.Println("shutting down")
}

func containsAny(s string, subs []string) bool {
	for _, sub := range subs {
		if contains(s, sub) {
			return true
		}
	}
	return false
}

func contains(s, sub string) bool {
	return len(s) >= len(sub) && (s == sub || (len(s) > len(sub) && (s[:len(sub)] == sub || s[len(s)-len(sub):] == sub || indexOf(s, sub) >= 0)))
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}

const qrHTML = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Neo Bootstrap QR</title>
<style>
body { font-family: -apple-system, sans-serif; background: #0a0a0a; color: #e0e0e0;
       max-width: 640px; margin: 40px auto; padding: 24px; text-align: center; }
h1 { color: #ccff00; }
.qr { background: white; padding: 16px; display: inline-block; margin: 16px 0; }
.addr { word-break: break-all; background: #1a1a1a; padding: 12px; border-radius: 8px;
        font-family: monospace; font-size: 12px; margin: 12px 0; }
</style></head>
<body>
<h1>Neo Bootstrap Node</h1>
<p>Scan this QR code with the Neo app to set this node as your custom bootstrap.</p>
<div class="qr"><img src="/qr.png" alt="QR code" width="320" height="320"></div>
<p>Multiaddr:</p>
<div class="addr">%s</div>
<p>Deep link:</p>
<div class="addr">%s</div>
</body></html>`

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
	return localAddr.IP.String()
}