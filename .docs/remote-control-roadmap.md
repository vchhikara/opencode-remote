# Remote Control Roadmap

What's needed to take OpenCode Remote from "send a prompt, wait, see the result" to
actual remote control of a running agent. Grounded in what's confirmed to exist in
`opencode serve`'s API (probed directly via `/doc`, not assumed) and what the bridge/
Android app currently do.

Status as of this writing: chat/files/git/terminal all work against a live bridge,
backed by one persistent `opencode` session per workspace (see `bridge/main.js`). This
document is everything past that point.

---

## 1. Live streaming (highest impact, biggest gap)

**Current state:** every prompt is one blocking HTTP call
(`POST /session/{id}/message`) — the app shows a static "Thinking..." for the entire
duration, then the full reply and full diff land at once. No visibility into what the
agent is actually doing while it works.

**What exists to build on:** `opencode serve` exposes `GET /event` (global SSE stream)
and `/api/session/{sessionID}/event` (per-session). Event types include message part
updates (`EventMessagePartUpdated`, `EventMessagePartDelta`), tool call state
transitions, and session status changes — this is the same feed the TUI/web UI use.

**Work:**
- [ ] Bridge: open one SSE connection to `/event` when a session exists; parse events
      and re-broadcast as new WebSocket frame types (`STREAM_TEXT_DELTA`,
      `STREAM_TOOL_CALL`, `STREAM_TOOL_RESULT`) instead of only pushing at the end.
- [ ] Bridge: keep the final `CHAT_MESSAGE`/`FILE_DIFF` push for clients that don't
      care about streaming (backward compatible).
- [ ] Android: `RemoteSessionManager` gains a flow for in-progress deltas; `ChatScreen`
      renders a streaming bubble instead of only fully-formed messages.
- [ ] Android: show which tool is currently running (e.g. "Editing `main.js`...",
      "Running `npm test`...") using the tool-call events, not just "Thinking...".

---

## 2. Mid-task control: interrupt, steer, approve

**Current state:** `KILL_TASK` calls `/session/{id}/abort` (added in this session) —
that's the one piece of mid-task control that exists. Everything else is fire-and-wait.

**What exists to build on:** `/session/{sessionID}/abort` (confirmed working),
`/session/{sessionID}/permission/{permissionID}` and `/permission/{requestID}/reply`
(approve/deny a tool call the agent wants to make), `/question/{requestID}/reply` and
`/reject` (answer a clarifying question the agent asks mid-task).

**Work:**
- [ ] Bridge: relay `PermissionRequested`/`QuestionAsked` SSE events as new frame
      types (`PERMISSION_REQUEST`, `QUESTION_REQUEST`) instead of the server's current
      auto-approve-everything mode.
- [ ] Bridge: wire `PERMISSION_REPLY`/`QUESTION_REPLY` inbound frames to the matching
      `/reply`/`/reject` endpoints.
- [ ] Android: a modal/inline card — "Agent wants to run `rm -rf node_modules` — allow
      once / always / deny" — appears when a `PERMISSION_REQUEST` frame arrives, blocks
      nothing else in the UI while waiting.
- [ ] Android: confirm `KILL_TASK`'s abort actually shows up as a distinguishable
      "cancelled" message in chat, not a silent nothing.
- [ ] Stretch: send a follow-up message *while* the agent is still working on the
      previous one (steering mid-task), if the server API supports queuing.

---

## 3. Session visibility and management

**Current state:** one implicit session per workspace, created lazily, never listed,
never named by the user, lost if the bridge restarts.

**What exists to build on:** `GET /session` (list), `POST /session/{id}/fork`,
`POST /session` with `parentID` (branch a session), `/session/{sessionID}/children`,
`opencode session` CLI equivalent.

**Work:**
- [ ] Bridge: expose `LIST_SESSIONS`/`SWITCH_SESSION`/`NEW_SESSION` frame types.
- [ ] Android: a session picker (replace or extend the current single-workspace
      Workspaces screen) — see past sessions with titles/timestamps, resume any of
      them, start a fresh one without losing the old one.
- [ ] Bridge: persist the current session id across bridge restarts (currently
      `ocSessionId` is in-memory only — a bridge crash silently starts a new session).
- [ ] Stretch: session forking from the app (branch a conversation to try an
      alternative without losing the original).

---

## 4. Real terminal, not one-shot command exec

**Current state:** `spawn(sh, ['-c', command])` per command, buffered output at
process close, no PTY, `TERMINAL_RESIZE` is a documented no-op. Confirmed in
`bridge/main.js`'s `TERMINAL` case.

**What exists to build on:** `opencode serve` exposes `/pty`, `/pty/{ptyID}`,
`/pty/{ptyID}/connect`, `/pty/{ptyID}/connect-token`, `/pty/shells` — a real PTY API,
almost certainly what the TUI's embedded terminal uses.

**Work:**
- [ ] Bridge: replace the raw `spawn` terminal handler with the `/pty` API — real
      PTY, real resize support, real streaming output instead of buffer-at-close.
- [ ] Android: `TerminalScreen` becomes a real streaming terminal (consider a
      lightweight ANSI-aware renderer) instead of a scrollback list of finished-command
      output.
- [ ] Android: wire `TERMINAL_RESIZE` to something real once the bridge PTY supports
      it (currently it's a no-op on both ends).

---

## 5. Fine-grained diff review

**Current state:** one pending diff at a time (`_pendingDiff` StateFlow), fetched
after generation completes via `GET /session/{id}/diff`, accept/reject is
whole-file-or-nothing. `ACCEPT_HUNK`/`REJECT_HUNK` are explicitly rejected by the
bridge as unsupported.

**Work:**
- [ ] Android: `pendingDiff` becomes a list, not a single value — show every changed
      file from `GET /session/{id}/diff`, not just the last one received.
- [ ] Bridge + Android: per-hunk accept/reject, if/when the underlying `opencode`
      session API supports partial application (needs checking against a current
      `opencode` version — this may require an upstream capability that doesn't exist
      yet, not just bridge plumbing).
- [ ] Android: live diff preview as edits stream in (depends on §1 streaming work).

---

## 6. Multi-workspace / multi-project

**Current state:** bridge hardcodes one `activeWorkspace`; `FETCH_WORKSPACES` always
returns a single-item list built from it. Real multi-project support doesn't exist
server-side.

**Work:**
- [ ] Bridge: track multiple workspace roots, expose a real list.
- [ ] Bridge: one `opencode serve` + session per workspace, or scope sessions by
      workspace within one server (needs checking against the server's `/project`
      API, which does exist in the OpenAPI surface).
- [ ] Android: `WorkspaceListScreen` becomes meaningful instead of a single-item list.

---

## 7. Reachability, notifications, backgrounding

**Current state:** phone must be on the same LAN as the bridge (mDNS + manual
IP:port), app must be foregrounded to see anything happen, no notification when a
task finishes or needs approval while backgrounded.

**Work:**
- [ ] Push notifications (FCM or similar) for: task completed, permission requested,
      error occurred — at minimum while the app is backgrounded but the WebSocket is
      still alive; ideally even after the OS kills the background connection.
- [ ] A foreground service or reconnect-on-resume strategy so a backgrounded app
      doesn't silently lose the WebSocket and miss everything until manually reopened.
- [ ] Remote reachability beyond LAN: a relay/tunnel option (e.g. Tailscale,
      Cloudflare Tunnel, or a small relay server) for controlling a machine that isn't
      on the same network as the phone — currently completely out of scope, LAN-only.

---

## 8. Security hardening for "actual remote control"

**Current state:** one static token per bridge process (regenerated on restart, no
rotation), no per-device identity, `--auto` grants the agent broad permission
auto-approval server-side (partially superseded by §2's real permission flow once
built), terminal/git allow arbitrary-ish commands within an allowlist.

**Work:**
- [ ] Per-device pairing/token issuance instead of one shared token for anyone who
      scans the QR.
- [ ] Token revocation from the app (kick a device without restarting the bridge).
- [ ] Once §2 lands, actually turn off blanket auto-approval and route real
      permission decisions to the phone — this is a security improvement, not just a
      feature.
- [ ] Audit log of what commands/edits were approved remotely, visible in-app.

---

## Suggested sequencing

1. **§1 Streaming** — unlocks the *feel* of remote control; nothing else matters much
   without this.
2. **§2 Interrupt/approve** — the other half of "control" as opposed to "operate."
3. **§3 Session visibility** — cheap relative to its value once §1/§2 exist, and makes
   the app trustworthy for real work (nothing silently lost).
4. **§4 Real terminal** — high value, isolated, doesn't block on the others.
5. **§5 Fine-grained diff** — depends partly on §1 for the live-preview piece.
6. **§6 Multi-workspace**, **§7 Reachability/notifications**, **§8 Security** — scale
   and hardening once the core loop (1–5) is solid.
