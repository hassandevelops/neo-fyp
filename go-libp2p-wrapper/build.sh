#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "=== Building Go libp2p binary for Android ARM64 ==="
export PATH=$HOME/go1.22/bin:$PATH
go mod tidy
CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -o neoserver_arm64 ./cmd/neoserver
echo "Binary size: $(ls -lh neoserver_arm64 | awk '{print $5}')"

echo "=== Copying to assets ==="
cp neoserver_arm64 ../app/src/main/assets/neoserver
echo "Done. Binary ready in app/src/main/assets/neoserver"
