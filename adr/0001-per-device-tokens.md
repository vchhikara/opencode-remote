# 0001: Per-device tokens instead of one shared long-lived token

## Status
Accepted (implemented, Phase 8)

## Context

Early on, the bridge authenticated every connection against a single `BRIDGE_TOKEN` value, checked forever. That has two problems for a tool that's meant to stay running and get paired with multiple phones/devices over time:

- **No revocation.** If a device's token leaked (lost phone, shoulder-surfed QR code, log capture), the only fix was rotating `BRIDGE_TOKEN` and re-pairing *every* device, not just the compromised one.
- **Unbounded blast radius.** One token authenticates every device forever; there's no way to tell which device did what (all requests looked the same to the bridge), and no way to cut off just one.

## Decision

Split the token into two roles:

1. **Pairing secret** (`BRIDGE_TOKEN`, env-configurable, defaults to a random 32-byte hex string) — printed as a QR code at bridge startup. It is a **one-time-use bootstrap credential**, not a standing credential.
2. **Per-device token** — issued by the bridge the first time a device presents the pairing secret in a `CONNECT` frame (`issueDeviceToken()` in `bridge/main.js`), persisted to `DEVICE_STORE_PATH` (default `~/.opencode-remote-devices.json`) keyed by the token, with `{ deviceId, deviceName, issuedAt }`. The bridge returns it as `CONNECTED.payload.issuedToken`; the client persists it and uses it on every subsequent `CONNECT` instead of the pairing secret.

The `CONNECT` handler checks, in order: (a) is this token already a known device token — if so, authenticate as that device; (b) does it match the pairing secret (via `timingSafeTokenEquals`, constant-time comparison) — if so, mint a new device token and register it; (c) otherwise, close the socket (code `4001`).

Every authenticated socket carries `ws.deviceId`/`ws.deviceName`, which flows into the audit log (`appendAuditLog`) so every permission decision, terminal command, and git command is attributable to a specific device.

## Alternatives considered

- **Keep the single shared `BRIDGE_TOKEN` forever.** Rejected: no revocation story — a leaked token means rotating and re-pairing everything, and there's no per-device attribution in the audit log.
- **Full OAuth-style flow** (authorization server, refresh tokens, scopes, expiry). Rejected as overkill: this is a personal-use, single-operator tool run over a LAN or a private tunnel, not a multi-tenant service. The complexity of a real OAuth flow (client registration, token endpoints, refresh rotation) buys nothing here that a simple pairing-secret-once → per-device-token model doesn't already solve, and it would meaningfully slow down pairing a new phone.

## Consequences

**Enabled:**
- `REVOKE_TOKEN` (Task 8.2.1): deletes one device's token and force-closes its open sockets, without affecting other paired devices or the pairing secret.
- `LIST_DEVICES` (Task 8.2.2): lets the Android settings screen show who's paired (`deviceId`, `deviceName`, `issuedAt`), without ever exposing raw tokens.
- Audit log attribution (Task 8.4): every `permission_decision`, `question_reply`, `terminal_command`, and `git_command` entry carries the acting device's `deviceId`/`deviceName`.

**Not solved by this decision:**
- **No token expiry or rotation policy.** Issued device tokens are valid indefinitely until explicitly revoked — there's no TTL, no forced re-authentication, no rotation-on-use.
- **No mTLS / transport-level device identity.** Device identity is entirely a bearer-token concept at the application layer; the WebSocket transport itself has no client-certificate verification. A token that leaks off-device (e.g. via a compromised phone) is fully usable by whoever holds it until it's revoked.
- **No rate limiting on `CONNECT` attempts** — the pairing secret is checked with a constant-time comparison to prevent timing attacks, but repeated bad-token connection attempts aren't throttled or locked out.
- `DEVICE_STORE_PATH` is a plaintext JSON file on the bridge host; anyone with filesystem access to that file can read (though not use, since the bridge itself never round-trips them) all device metadata, and anyone able to write it can forge entries.
