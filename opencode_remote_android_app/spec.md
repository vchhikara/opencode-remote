# Spec — OpenCode Remote Android Client (Rebuild)

Companion to [intent.md](intent.md). This is the contract the build must
satisfy. Anything not written here is out of scope by default — add to this
file before building it, don't build first and document after.

## 1. System boundary

```
Android Client  <—— ws://<lan-ip>:8080/ws ——>  Bridge (bridge/main.js)  ——spawn——>  opencode CLI
```

The bridge is unchanged, already implemented, and already tested. This spec
treats it as fixed infrastructure. Its full wire protocol is documented in
[docs/integration-context.md](docs/integration-context.md) — **that document
is the source of truth for the protocol; this spec references it rather than
re-deriving it, so the two never drift apart.**

Bridge behavior worth restating because it directly shapes client design:

- Auth is a `CONNECT{deviceName, token}` handshake, required before any other
  frame; the socket closes with code `4001` on failure or if any other frame
  arrives first.
- All state pushes are unsolicited server→client frames matching a fixed set
  of `eventType`s — the client is a passive listener plus a command sender,
  never a poller.
- Diff accept/reject is whole-file only (no hunk-level apply).
- `TERMINAL` runs one command at a time via `spawn`, not an interactive PTY —
  no resize semantics, no ANSI passthrough beyond what the process itself
  emits.
- Workspace root is confined server-side; path-escape attempts are rejected
  with `ERROR`, not silently clamped.

## 2. Target platform

- Kotlin, Jetpack Compose, Material 3.
- `minSdk 24`, `targetSdk`/`compileSdk` matched to whatever stable API level
  the toolchain on the build machine actually has installed — verify with
  `sdkmanager --list` before picking a number, don't guess.
- MVVM: `Screen (Compose) → ViewModel → Repository → RemoteSessionManager →
  Ktor WebSocket`. No screen constructs or holds a `WebSocketSession`
  directly.

## 3. Screens (nav graph)

Each screen below is **in scope only as described**. If a screen needs data
the bridge doesn't send, it shows an explicit unavailable state — it does
not invent the data.

| Screen | Purpose | Backed by |
|---|---|---|
| **Pairing** | Scan QR / enter IP+token manually / accept an mDNS-discovered bridge | `CONNECT`, local `EncryptedSharedPreferences` for the last-used credential |
| **Workspace list** | Pick which workspace to open | `FETCH_WORKSPACES` → `WORKSPACE_LIST` |
| **Chat** | Send prompts, view streamed agent responses | `PROMPT` → `CHAT_MESSAGE`, `AGENT_STATE` |
| **File explorer** | Browse the workspace file tree, open a file read-only | `FETCH_FILE_TREE` → `FILE_TREE`, `FETCH_FILE` → `FILE_CONTENT` |
| **Diff review** | Accept/reject a pending AI-generated file change | `FILE_DIFF`, `ACCEPT_DIFF`/`REJECT_DIFF` — **file-level only**; no per-hunk UI |
| **Activity monitor** | Show current agent state and running tasks | `AGENT_STATE`, `TASK_UPDATED`/`TASK_REMOVED` |
| **Terminal** | Run one shell command, see its output | `TERMINAL` → `TERMINAL_OUTPUT`; no resize control (bridge ignores it) |
| **Git** | Status + commit/pull/push/fetch | `GIT_STATUS`, `GIT` (commit/pull/push/fetch only — see §5) |
| **Settings** | Disconnect, forget saved credential, connection info | Local only |

Screens explicitly **not** in this rebuild (bridge has no data for them):
device management (remember/rename/remove), system logs, live preview/WebView,
conversation history persistence, push notifications. If any of these become
in-scope later, that starts with a bridge protocol change, then a spec
update — not a client-side workaround.

## 4. Data models

Client-side DTOs mirror the bridge's JSON payloads exactly — field names and
shapes come from `bridge/main.js`, not from imagining a "nicer" API. Existing
DTOs that were already correct and covered by
[`WebSocketFrameTest.kt`](app/src/test/java/com/example/network/protocol/WebSocketFrameTest.kt)
(`FileNodeDto`, `GitStatusDto`, `WorkspaceDto`, `ChatMessageDto`,
the `{eventType, payload}` envelope) may be reused as-is; re-verify each
against the current `bridge/main.js` before reuse, don't assume prior
verification still holds.

## 5. Command allowlist (client-enforced UI, not a new server rule)

The bridge already enforces its own git subcommand allowlist and rejects
anything else with `ERROR` — the client does not need to re-implement that
check, but the UI should only expose actions the bridge is known to accept
(commit, pull, push, fetch) so users aren't invited to try commands that
will visibly fail. Read the current allowlist out of `bridge/main.js` at
build time rather than hardcoding a copy that can drift.

## 6. Non-functional requirements

- **Reconnect:** on unexpected socket close, retry with backoff; re-send
  `CONNECT` with the stored credential before resuming any other command.
  Never silently drop into a state where the UI looks connected but isn't.
- **Security:** the pairing token is stored via `EncryptedSharedPreferences`
  (Keystore-backed), never in plaintext DataStore or logs.
- **Network:** cleartext `ws://` only to LAN/loopback ranges, enforced via
  Android network security config — this is a real constraint carried over
  from the bridge's own trust model (see
  [docs/integration-context.md](docs/integration-context.md) §Transport),
  not optional hardening to skip for convenience.
- **No mock data ships to `main`.** Sample/seed data is permitted only
  behind a `debug`-only build variant flag, never reachable in a release
  build, and never the default render path if a network call hasn't
  returned yet (that path is a loading or empty state, not fake content).

## 7. Explicit unknowns to resolve before/during build (not guessed here)

- Exact `compileSdk`/AGP/Kotlin/KSP version combination — the previous
  attempt burned significant time on transitive-dependency version
  mismatches (Compose Unstyled requiring newer Compose Foundation than the
  project's Kotlin compiler could read, `firebase-ai` needing a BOM bump,
  etc.). Pin every third-party version against what's *actually resolvable*
  on the build machine before writing code against it, not from memory or
  documentation that may describe a newer unreleased API.
- Whether `com.composables:composeunstyled-*` (used for the previous app's
  unstyled Button/TextField/Switch primitives) is worth keeping at all, or
  whether plain Material 3 components are simpler and sufficient — decide
  this at build time (see [plan.md](plan.md) Phase 0), don't default to
  reusing a dependency that already caused most of the previous build pain.
