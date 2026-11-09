# Security

## What the bridge exposes

`bridge/main.js` is not a sandbox. An authenticated client can, by design:

- Execute an allowlisted set of `git` subcommands (`status, add, commit, push, pull, fetch, checkout, branch, stash, cherry-pick, diff, log, rev-parse`) via the `GIT` event.
- Run arbitrary shell commands in a real PTY via the `TERMINAL` event.
- Read any file inside the active workspace via `FETCH_FILE`.
- Drive `opencode serve` itself, sending prompts, approving or denying permission requests, answering questions, which can in turn read, write, and execute within the workspace on the agent's behalf.

This is the point of the project (remote control of a coding agent on your machine), but it means anyone who can authenticate to the bridge has meaningful access to that machine. Treat the bridge like you'd treat SSH access to your laptop, not like a read-only status page.

## Authentication model

Full design rationale is in [ADR 0001](adr/0001-per-device-tokens.md); the short version:

- The bridge starts with a **pairing secret** (`BRIDGE_TOKEN`, random 32-byte hex by default), printed as a QR code. The secret expires after 120 seconds or after one successful pairing by default. Both limits are configurable with `PAIRING_WINDOW_MS` and `PAIRING_MAX_DEVICES`. A restart opens a new window, so avoid reusing an explicit static `BRIDGE_TOKEN`.
- The first device to present it in `CONNECT` gets a **per-device token**, persisted server-side (`DEVICE_STORE_PATH`) and used for all future connections instead of the pairing secret.
- Devices can be individually revoked (`REVOKE_TOKEN`), deleting that device's token and force-closing its live socket, without affecting other paired devices or requiring the pairing secret to be rotated.
- Every authenticated action (permission decisions, terminal commands, git commands) is attributed to a `deviceId`/`deviceName` and appended to an audit log (`AUDIT_LOG_PATH`, JSON-lines, retrievable via `FETCH_AUDIT_LOG`).

### What this model does *not* provide (from ADR 0001's "Not solved by this decision")

- **No token expiry or rotation.** A per-device token is valid indefinitely until explicitly revoked.
- **No transport-level device identity.** There's no mTLS or client-certificate verification; device identity is a bearer token at the application layer. A token that leaks off-device is fully usable by whoever holds it until revoked.
- **No rate limiting on `CONNECT` attempts.** The pairing secret is compared in constant time (`timingSafeTokenEquals`) to resist timing attacks, but repeated bad-token connection attempts aren't throttled or locked out.
- **`DEVICE_STORE_PATH` is a private JSON file** on the bridge host. It stores SHA-256 token verifiers and device metadata, not bearer tokens. Existing raw-token-key stores are migrated automatically at startup. File mode 0600 is enforced; a privileged local user or malicious process with write access can still tamper with the authorization store. A SHA-256 verifier does not protect against a stolen bearer token from a phone or network trace.

Pairing is single-use by default; there is still no rate limiting for bad-token connection attempts beyond the WebSocket resource and authentication-time limits. The socket is restricted to `/ws`, has a 16 MiB inbound message cap, a 10-second pre-authentication timeout, a 64-client limit and ping/pong cleanup.

This is a deliberate, documented trade-off (see ADR 0001's "Alternatives considered") for a **personal-use, single-operator tool** run over a LAN or a private tunnel, not a multi-tenant service. It is not designed to withstand a hostile network or a hostile co-tenant on the bridge host.

## Recommended deployment

- The default `BRIDGE_HOST=0.0.0.0` listens on all interfaces. Bind to `127.0.0.1` when behind a local proxy, or restrict inbound traffic using a host firewall. Never expose the bridge directly to the public internet.
- Run the bridge only on networks you trust (your home LAN), or reach it from outside your LAN via a private tunnel (Tailscale or Cloudflare Tunnel) rather than by exposing the WebSocket port directly to the public internet. See [`bridge/README.md`](bridge/README.md) for setup of both.
- Treat a leaked pairing-secret QR code the same as a leaked password: rotate `BRIDGE_TOKEN` (restart the bridge with a new value or let it generate a fresh random one) before pairing another device.
- If a phone is lost or compromised, revoke its device token immediately from the app's settings screen (or via `REVOKE_TOKEN`), rather than waiting to rotate the shared pairing secret.
- Review `AUDIT_LOG_PATH` periodically if you're running the bridge unattended for extended periods. It's the only record of what commands and approvals happened while you weren't watching.
- Do not commit `DEVICE_STORE_PATH` or `AUDIT_LOG_PATH` contents to version control; both can contain information about your local environment and usage patterns. (The default paths are outside the repo tree, but a custom `WORKSPACE_ROOT`-relative `AUDIT_LOG_PATH` could land inside it, check your `.gitignore` if you change this.)

## Reporting a vulnerability

This is a personal-use project without a dedicated security contact or disclosure program. If you find a vulnerability, please open a GitHub issue describing it, or reach the maintainer directly if the issue involves details you'd rather not post publicly (e.g. a working exploit against the default configuration).
