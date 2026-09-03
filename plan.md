# Plan — OpenCode Remote Android Client (Rebuild)

Executes [spec.md](spec.md) in order. Each phase ends in something that
builds and runs — never move to the next phase with the current one broken.
Gate markers: **[H-BUILD]** needs a real Gradle+Android SDK toolchain,
**[H-DEVICE]** needs a real phone + bridge running on a real LAN. Both are
available in this environment now (verified this session:
`./gradlew :app:assembleDebug` succeeds, JDK 17 + Android SDK installed).

## Phase 0 — Toolchain lock-in

Before any app code, pin every version by resolving it for real, not from
memory.

1. Record the actual installed Android SDK platforms/build-tools
   (`~/Android/Sdk/platforms`, `~/Android/Sdk/build-tools`) and pick
   `compileSdk`/`targetSdk` from what's actually installed.
2. Pick a Kotlin version, then verify AGP + KSP + Compose Compiler plugin
   versions that are mutually compatible with it (check each plugin's own
   compatibility table — do not assume the newest of each is compatible with
   the newest of the others; this exact mismatch cost significant time on
   the previous build).
3. Decide Compose Unstyled vs. plain Material 3 (spec §7) **now**, before any
   screen is written against one or the other.
4. `[H-BUILD]` Scaffold an empty single-Activity Compose project with just
   the chosen `compileSdk`/Kotlin/AGP/KSP combination and confirm
   `./gradlew :app:assembleDebug` succeeds before writing a single screen.
5. Set up `gradle/libs.versions.toml` from the start — no inline version
   strings in `build.gradle.kts`. Run a catalog structural check (reuse
   `tools/verify_catalog.py` from the current repo, or equivalent) as part
   of every dependency change from here on.

**Exit criteria:** empty app installs and launches on a device/emulator.
Commit.

## Phase 1 — Networking core

1. `RemoteSessionManager`: owns the single `WebSocketSession`, connection
   lifecycle, reconnect/backoff, and the `{eventType, payload}` envelope
   encode/decode (reuse `WebSocketFrame`/DTOs from the old app's
   `network/protocol` package if they still match `bridge/main.js` — verify
   each field against the bridge source, don't assume).
2. Implement `CONNECT` handshake exactly per
   [docs/integration-context.md](docs/integration-context.md): send
   `CONNECT` first, wait for `CONNECTED`, treat anything else (including no
   response) as a failed pairing attempt with a surfaced error, not a silent
   hang.
3. `[H-DEVICE]` Run `bridge/main.js` for real, connect from a debug-only
   test harness (a temporary screen or instrumented test is fine) using the
   token it prints, confirm `CONNECTED` then `WORKSPACE_LIST` arrive.
4. Unit tests: envelope round-trip, DTO shapes, reconnect-after-close logic
   with a fake WebSocket. Reuse/adapt
   `WebSocketFrameTest.kt` if still accurate.

**Exit criteria:** a debug build can pair with a real running bridge and log
the received `WORKSPACE_LIST`. Commit.

## Phase 2 — Pairing & workspace selection UI

1. Pairing screen: QR scan (real ML Kit barcode decode — no
   `postDelayed` fake), manual IP+token entry, mDNS-discovered bridge list
   if available.
2. Persist the last-used credential via `EncryptedSharedPreferences`
   (Keystore-backed) — never plaintext.
3. Workspace list screen wired to `FETCH_WORKSPACES`/`WORKSPACE_LIST`;
   selecting one sends `OPEN_WORKSPACE`.
4. Disconnected/reconnecting/error states are real, designed UI states, not
   afterthoughts — build them alongside the happy path, not after.

**Exit criteria:** `[H-DEVICE]` fresh install → scan QR → land on workspace
list with real data → open a workspace. Commit.

## Phase 3 — File explorer + chat (the core loop)

1. File tree screen: `FETCH_FILE_TREE`/`FILE_TREE`, lazy-expand directories,
   `FETCH_FILE`/`FILE_CONTENT` for read-only viewing with syntax
   highlighting.
2. Chat screen: send `PROMPT`, render streamed `CHAT_MESSAGE`s, show
   `AGENT_STATE` inline (thinking/running/idle).
3. This is the single most important loop in the app — budget real testing
   time here: send a prompt that causes the bridge-spawned `opencode`
   process to actually edit a file, confirm the message stream and resulting
   `FILE_DIFF` arrive correctly.

**Exit criteria:** `[H-DEVICE]` a real prompt round-trips through the actual
`opencode` CLI and the response is visible in the app. Commit.

## Phase 4 — Diff review

1. `FILE_DIFF` rendering: red/green line diff, file-level Accept/Reject only
   (`ACCEPT_DIFF`/`REJECT_DIFF`) — no hunk-level controls, since the bridge
   has no hunk-level apply. Don't build UI for a capability that doesn't
   exist server-side.
2. Confirm rejecting a diff actually leaves the file unchanged and accepting
   it actually writes it — verify by reading the file on disk after each
   action, not just trusting the UI updated.

**Exit criteria:** `[H-DEVICE]` full loop: prompt → diff appears → accept →
file on disk matches; reject → file on disk unchanged. Commit.

## Phase 5 — Terminal, tasks, git

1. Terminal screen: `TERMINAL` command send, `TERMINAL_OUTPUT` render. No
   resize UI (documented bridge no-op — don't build a control that does
   nothing).
2. Activity/tasks screen: `AGENT_STATE`, `TASK_UPDATED`/`TASK_REMOVED` bound
   to real `StateFlow`s — this is the one thing the previous app got right
   the first time (`ActivityScreen.kt` after its own fix); keep the same
   shape (real `collectAsStateWithLifecycle`, explicit idle/empty states).
3. Git screen: `GIT_STATUS` display, commit/pull/push/fetch actions only
   (matches the bridge's actual subcommand allowlist — verify it against
   `bridge/main.js` at build time, not against this document, in case it's
   changed since).

**Exit criteria:** `[H-DEVICE]` run a shell command and see its output; make
a commit from the phone and confirm it lands with `git log` on the laptop.
Commit.

## Phase 6 — Resilience & polish

1. Kill the bridge process mid-session; confirm the app shows disconnected,
   doesn't crash, and reconnects automatically when the bridge restarts.
2. Backgrounding/foregrounding the app during an active connection doesn't
   lose the session or duplicate `CONNECT` frames.
3. Every screen in the nav graph reachable with no dead-end or placeholder
   content.
4. `.github/workflows/ci.yml`-equivalent: bridge tests (unchanged, already
   passing) + `:app:testDebugUnitTest` + `:app:assembleDebug` all green in
   CI, not just locally.

**Exit criteria:** [intent.md](intent.md) "Definition of done" is fully met.
Tag the commit.

## What this plan deliberately excludes

Per [intent.md](intent.md) non-goals: device management, system logs,
preview/WebView, conversation history persistence, push notifications,
Play Store release engineering. Do not add these mid-plan without first
updating [spec.md](spec.md) — scope drift is exactly what put the previous
app in the state that triggered this rebuild.
