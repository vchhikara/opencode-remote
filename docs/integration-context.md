# OpenCode Companion App Integration Context

## Overview
A native Android companion app for OpenCode. It pairs with a Node.js WebSocket
bridge (`bridge/main.js`) that runs on the same machine as OpenCode and speaks
directly to the local filesystem, git, and a shell — there is no separate
Express server (the old `opencode-remote/` server was removed).

## Status
Client and bridge speak the same canonical `{eventType, payload}` JSON
envelope end-to-end. The features below are wired to real bridge state; a few
are explicitly **not implemented** rather than faked (see the list at the
bottom).

## Transport & auth
- **Default port:** `8080` (bridge listens for both HTTP and the `/ws`
  WebSocket upgrade on this port).
- **Cleartext:** the bridge is plain `ws://`/`http://`, restricted at the
  Android network-security-config layer to LAN/loopback ranges. Do not expose
  it beyond a trusted local network.
- **Auth handshake:** the first frame the client sends after the socket opens
  must be `CONNECT` with `{ deviceName, token }`. The bridge compares `token`
  to its `AUTH_TOKEN` using a constant-time comparison; on mismatch it closes
  the socket with code `4001` and sends nothing further. Any other frame sent
  before a successful `CONNECT` is likewise rejected with `4001`. On success
  the bridge replies `CONNECTED { sessionId, deviceName }` followed by
  `WORKSPACE_LIST`; the client only proceeds (e.g. calls `FETCH_WORKSPACES`)
  after receiving `CONNECTED`.

## Wire format
Every frame in both directions is `{ "eventType": string, "payload"?: any }`.

### Client -> bridge
- `CONNECT` — `{ deviceName, token }`. Must be the first frame (see above).
- `FETCH_WORKSPACES` — list available workspaces.
- `OPEN_WORKSPACE` — payload is an absolute path; the bridge only accepts
  paths inside the confined workspace root and returns `ERROR` otherwise.
- `FETCH_FILE_TREE` — request the directory tree of the active workspace.
- `FETCH_FILE` — payload is a path; path-traversal and absolute escapes
  outside the workspace are rejected.
- `PROMPT` — payload is the user's message to the AI agent.
- `ACCEPT_DIFF` / `REJECT_DIFF` — payload is a file name; applies at the
  whole-file level.
- `ACCEPT_HUNK` / `REJECT_HUNK` — **not supported.** The bridge has no
  per-hunk apply (the diff is one accumulated blob); it logs and ignores
  these, and the Android UI disables the hunk-level controls accordingly
  (file-level accept/reject still works).
- `TERMINAL` — payload is a shell command string, run via `spawn` (not
  `exec`) inside the active workspace.
- `TERMINAL_RESIZE` — payload is `{ cols, rows }`. **Documented no-op:** the
  bridge runs line-oriented commands, not an interactive PTY, so this is
  accepted and ignored.
- `KILL_TASK` — payload is a task id; sends the process a kill signal.
- `GIT` — payload is a command string, e.g. `pull`, `push`, `commit -m "msg"`,
  `stash`, `checkout -b <branch>`. The bridge only allows a fixed subcommand
  allowlist and tokenizes arguments itself (no shell interpolation).

### Bridge -> client
- `CONNECTED { sessionId, deviceName }` — handshake accepted.
- `ERROR { message }` — sent for any rejected/failed operation above.
- `WORKSPACE_LIST`, `WORKSPACE_OPENED`, `FILE_TREE`, `FILE_CONTENT`,
  `GIT_STATUS`, `AGENT_STATE`, `CHAT_MESSAGE`, `FILE_DIFF`,
  `TERMINAL_OUTPUT`, `TASK_UPDATED`, `TASK_REMOVED` — state pushes matching
  the client's `RemoteSessionManager` `StateFlow`s.

## Not implemented (by design, not by omission)
These are explicitly surfaced to the user as unavailable rather than faked
with static or simulated data:
- **Device management** (remember/rename/remove a paired device) — there is
  no persisted device list beyond the last-used IP.
- **Advanced git** (stash, cherry-pick) — no UI entry point; `commit`,
  `pull`, `push`, `fetch` work.
- **System logs** — the bridge does not stream a log feed.
- **Conversation history sync** — chat state is in-memory only and resets
  each session; nothing is persisted or restored.
- **Push notifications** — no FCM (or equivalent) wiring exists.
- **mDNS/NSD discovery** — see the H-DEVICE gate; if the bridge's
  advertisement can't be verified end-to-end, pairing is manual IP or QR
  only, and the auto-discover UI is disabled rather than left showing a
  browse list that never resolves.
