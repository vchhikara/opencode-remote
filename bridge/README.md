# OpenCode Remote — Bridge

Node process that pairs the Android app with a local `opencode serve` instance over
one authenticated WebSocket (`/ws`). Run it on the machine where your project lives.

```bash
npm install
npm start        # or: node main.js <port>
```

The bridge prints a pairing QR code with the actual listening port. The startup
pairing secret is accepted once within two minutes by default, after which
new pairing requires a bridge restart. Already paired phones reconnect using
device-specific tokens. `PAIRING_MAX_DEVICES` can explicitly allow several new
devices in one window. Tokens are persisted as SHA-256 verifiers, not bearer
tokens, in a file restricted to mode 0600.

## Reaching the bridge from off your LAN (Task 7.3)

By default the bridge binds to all interfaces with a plain `ws://` socket.
It does not enforce a local-network-only firewall. Restrict it using host
firewall rules or `BRIDGE_HOST=127.0.0.1` with a private tunnel or reverse
proxy. Never forward this plaintext port directly to the public Internet. Reaching it from anywhere else — a phone on mobile data, a different
network — requires a relay/tunnel in front of it. This section documents two
concrete, testable setups; it is not a code change, and does not claim a specific
option is "done" beyond having a working documented procedure. If you later want a
purpose-built relay server instead, that's new scope with its own plan.

### Option A — Tailscale (recommended: private, no public exposure)

Tailscale creates a private WireGuard mesh between your devices; the bridge stays
unreachable from the public internet.

1. Install Tailscale on the bridge machine and sign in:
   ```bash
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up
   ```
2. Install the Tailscale Android app (Play Store) and sign in with the **same**
   account, so both devices join the same tailnet.
3. On the bridge machine, find its Tailscale IP:
   ```bash
   tailscale ip -4
   ```
4. In the Android app's pairing screen, enter that Tailscale IP as the host
   instead of the LAN IP, plus the bridge's port and auth token as usual.
5. Verify: turn off Wi-Fi on the phone (switch to mobile data) and confirm the app
   still connects — that proves the tunnel, not local Wi-Fi, is carrying the
   connection.

### Option B — Cloudflare Tunnel (reachable via a public hostname)

Use this if you want a stable public hostname instead of a private mesh (e.g. to
share access, or your Android device can't run Tailscale).

1. Install `cloudflared` on the bridge machine and authenticate against a
   Cloudflare account/zone you control:
   ```bash
   cloudflared tunnel login
   ```
2. Create a named tunnel and route it to a hostname:
   ```bash
   cloudflared tunnel create opencode-remote-bridge
   cloudflared tunnel route dns opencode-remote-bridge bridge.yourdomain.com
   ```
3. Run the tunnel, pointing it at the bridge's local port (the bridge's WebSocket
   handshake is plain HTTP/`ws://` locally — Cloudflare terminates TLS for you):
   ```bash
   cloudflared tunnel run --url ws://127.0.0.1:<bridge-port> opencode-remote-bridge
   ```
4. In the Android app's pairing screen, enter `bridge.yourdomain.com` as
   the host, set port `443` and **Transport** to `wss`. The TLS proxy must
   accept `/ws`. The bridge speaks `ws` on the private side. Never use the
   `ws` mode to the public hostname.
5. Verify from any network outside your LAN (e.g. mobile data with Wi-Fi off) that
   the app connects through the public hostname.

### Notes

- Either option relies on the bridge's own token-based auth (`BRIDGE_TOKEN` /
  the CONNECT handshake) as the actual access control — the tunnel only solves
  *reachability*, not *authorization*. Don't skip pairing with a real token.
- A public Cloudflare Tunnel hostname is reachable by anyone who has the URL and
  the auth token; prefer Tailscale (Option A) if you don't need to share access
  outside your own devices.
