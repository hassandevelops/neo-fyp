// Package anet replaces github.com/wlynxg/anet for Go 1.22+ cross-compilation.
// The original uses internal net.zoneCache removed in Go 1.23+.
// We delegate to the standard net package instead.
package anet

import (
	"net"
)

func InterfaceAddrs() ([]net.Addr, error) {
	return net.InterfaceAddrs()
}

func InterfaceAddrsByInterface(ifi *net.Interface) ([]net.Addr, error) {
	return ifi.Addrs()
}

func Interfaces() ([]net.Interface, error) {
	return net.Interfaces()
}

func SetAndroidVersion(version uint) {
}
