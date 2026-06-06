package main

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"os"
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
				relayIP := lanIP
				if relayIP == "" {
					relayIP = "127.0.0.1"
				}
				relayAddr := fmt.Sprintf("/ip4/%s/tcp/4001/p2p-circuit/p2p/%s", relayIP, pid.String())
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
		var msg map[string]interface{}
		if err := json.NewDecoder(s).Decode(&msg); err != nil {
			log.Printf("proxy: decode error: %v", err)
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
		log.Printf("proxy: connected to target %s, starting relay", target)

		// Copy data bidirectionally. Use a wait group so we only close the
		// streams once BOTH directions have finished.
		var wg sync.WaitGroup
		wg.Add(2)
		go func() {
			defer wg.Done()
			io.Copy(targetStream, s)
		}()
		go func() {
			defer wg.Done()
			io.Copy(s, targetStream)
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

	fmt.Printf("\n=== BOOTSTRAP NODE ===\n")
	fmt.Printf("PeerID: %s\n", pid)
	for _, a := range host.Addrs() {
		fmt.Printf("Addr: %s/p2p/%s\n", a.String(), pid)
	}
	fmt.Printf("Key file: %s\n", keyFile)
	fmt.Printf("Listening on port %d\n", port)
	fmt.Printf("========================\n\n")

	fmt.Printf("Add this to the bootstrap peers list in the Go binary:\n")
	if lanIP != "" {
		fmt.Printf("  \"/ip4/%s/tcp/%d/p2p/%s\",\n", lanIP, port, pid)
	}
	for _, a := range host.Addrs() {
		s := a.String()
		if s != fmt.Sprintf("/ip4/%s/tcp/%d", lanIP, port) && s != fmt.Sprintf("/ip4/0.0.0.0/tcp/%d", port) {
			fmt.Printf("  \"%s/p2p/%s\",\n", s, pid)
		}
	}

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	log.Println("shutting down")
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
	return localAddr.IP.String()
}