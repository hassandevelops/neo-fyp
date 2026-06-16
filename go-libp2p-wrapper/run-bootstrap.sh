#!/usr/bin/env bash
#
# run-bootstrap.sh — one command to run a Neo bootstrap (the relay that lets
# two phones sync). Builds the binary, optionally opens a public tunnel, starts
# the bootstrap, and prints the QR-code URL to scan on the phones.
#
# Usage:
#   ./run-bootstrap.sh            # cross-network: opens a public tunnel (bore.pub)
#                                 #   -> phones on ANY network can connect
#   ./run-bootstrap.sh --lan      # same-Wi-Fi only: no tunnel, advertises LAN IP
#                                 #   -> phones must be on the same router
#   ./run-bootstrap.sh --build    # force-rebuild the binary first
#
# Ctrl+C stops the bootstrap AND the tunnel cleanly.
#
set -euo pipefail
cd "$(dirname "$0")"

LAN_ONLY=0
FORCE_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --lan)   LAN_ONLY=1 ;;
    --build) FORCE_BUILD=1 ;;
    *) echo "unknown arg: $arg (use --lan and/or --build)"; exit 1 ;;
  esac
done

LIBP2P_PORT=4001
HTTP_PORT=4002

# --- 1. Build the binary if needed ---------------------------------------
if [[ ! -x ./neo-bootstrap || $FORCE_BUILD -eq 1 ]]; then
  echo "[build] compiling neo-bootstrap…"
  CGO_ENABLED=0 go build -o neo-bootstrap ./cmd/bootstrap
fi

# --- 2. Free the libp2p port (kill any stale bootstrap) ------------------
if command -v ss >/dev/null 2>&1; then
  for pid in $(ss -ltnp 2>/dev/null | grep ":${LIBP2P_PORT} " | grep -oE 'pid=[0-9]+' | grep -oE '[0-9]+' | sort -u); do
    echo "[cleanup] stopping stale process on :${LIBP2P_PORT} (pid $pid)"
    kill "$pid" 2>/dev/null || true
  done
  sleep 1
fi

BORE_PID=""
cleanup() {
  [[ -n "$BORE_PID" ]] && kill "$BORE_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

PUBLIC_ENV=()
if [[ $LAN_ONLY -eq 1 ]]; then
  LAN_IP=$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oE 'src [0-9.]+' | awk '{print $2}' | head -1)
  echo "[mode] LAN-only. Phones must be on the same Wi-Fi as this machine (${LAN_IP:-unknown})."
else
  # --- 3. Open a public TCP tunnel via bore.pub ---------------------------
  BORE_BIN="$(command -v bore || true)"
  [[ -z "$BORE_BIN" && -x "$HOME/.cargo/bin/bore" ]] && BORE_BIN="$HOME/.cargo/bin/bore"
  if [[ -z "$BORE_BIN" ]]; then
    echo "[error] 'bore' not found. Install it with:  cargo install bore-cli"
    echo "        (or run with --lan for a same-Wi-Fi demo that needs no tunnel)"
    exit 1
  fi
  echo "[tunnel] opening public tunnel via bore.pub…"
  BORE_LOG=$(mktemp)
  "$BORE_BIN" local "$LIBP2P_PORT" --to bore.pub > "$BORE_LOG" 2>&1 &
  BORE_PID=$!
  # wait for the assigned remote port
  REMOTE_PORT=""
  for _ in $(seq 1 20); do
    REMOTE_PORT=$(grep -oE "listening at bore.pub:[0-9]+" "$BORE_LOG" | grep -oE "[0-9]+$" | head -1 || true)
    [[ -n "$REMOTE_PORT" ]] && break
    sleep 0.5
  done
  if [[ -z "$REMOTE_PORT" ]]; then
    echo "[error] tunnel did not come up. bore log:"; cat "$BORE_LOG"; exit 1
  fi
  BORE_IP=$(getent ahostsv4 bore.pub | awk '{print $1}' | sort -u | head -1)
  echo "[tunnel] public endpoint: ${BORE_IP}:${REMOTE_PORT}  (via bore.pub)"
  PUBLIC_ENV=(NEO_PUBLIC_ADDR="/ip4/${BORE_IP}/tcp/${REMOTE_PORT}")
fi

# --- 4. Start the bootstrap (foreground) ---------------------------------
echo "[run] starting bootstrap…  scan the QR at: http://$(ip -4 route get 1.1.1.1 2>/dev/null | grep -oE 'src [0-9.]+' | awk '{print $2}' | head -1):${HTTP_PORT}/"
echo
env "${PUBLIC_ENV[@]}" BOOTSTRAP_PORT="$LIBP2P_PORT" BOOTSTRAP_HTTP_PORT="$HTTP_PORT" ./neo-bootstrap
