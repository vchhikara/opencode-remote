# Code Review — OpenCode Android Client

- **Date:** 2026-08-25 · **HEAD:** 1a0f7f3 · **Method:** static analysis only (no build possible on this machine; see finding H8)
- **Provenance:** produced from two independent review passes (subagent sessions + lead verification). Every Critical/High item was re-confirmed against source before publication.

## Executive summary

**Verdict: NOT production-ready.** The UI layer is competent Compose work with clean UDF plumbing, but the integration core is broken or simulated: the app cannot connect at all on modern Android (cleartext default-blocked), the wire protocol does not match the only real server, and several features are outright fabricated in the client (mock AI replies, fake QR scan, static activity/log screens). Three of these violate the project's own roadmap principle §9 ("Never generate fake…").

**Top 5 issues**
1. Cleartext traffic blocked by default on API 28+ → zero connectivity on modern devices (C1)
2. Wire protocol incompatible end-to-end with bridge server (H1, detailed in `reports/04-audit/SECURITY_AUDIT.md`)
3. Fabricated AI responses and diffs inside the network layer (C2)
4. QR pairing is a 3-second timer that injects dummy credentials (C3)
5. Build broken as committed — version catalog absent (H8)

## Findings table

| # | Severity | Axis | Location | Summary |
|---|---|---|---|---|
| C1 | Critical | Correctness | AndroidManifest.xml (absence) | No `usesCleartextTraffic`/`networkSecurityConfig`; hardcoded `http://`·`ws://` targets fail on API 28+ |
| C2 | Critical | Architecture | RemoteSessionManager.kt:221-255 | `sendCommand("PROMPT")` fabricates mock AI reply + hardcoded diff locally |
| C3 | Critical | Correctness | AuthScreen.kt:262-263 | Fake QR scan: `postDelayed(3000)` injects `"ip=10.0.2.2;key=dummy_api_key_123"` |
| H1 | High | Architecture | RSM.kt vs bridge/main.js | Client speaks JSON envelopes; server parses raw `CMD:` strings — total mismatch |
| H2 | High | Correctness | RSM.kt:183-191 + ProjectExplorerScreen.kt:113 | `mapFileNode` never sets path; nested files fetched by bare name → wrong file/failure |
| H3 | High | Correctness | RSM.kt:38-39,177-178 | Single global `selectedFileContent`, never cleared per request → stale content shown under new file name |
| H4 | High | Correctness | MainActivity nav + AuthScreen LaunchedEffect | Transient ERROR status ejects user to Auth; AuthScreen force-disconnects on entry → kills auto-reconnect loop |
| H5 | High | Correctness | MainViewModel uiEvents SharedFlow | replay=0/no-buffer: navigation events emitted before collector starts are silently dropped (cold-start race) |
| H6 | High | Performance | CodeViewer.kt:70-153 | Whole file rendered in non-lazy `Column(verticalScroll)` → freezes on large files |
| H7 | High | Correctness | TerminalScreen.kt:64-81 | Resize fired twice per layout pass w/ different padding math, during composition phase, no debounce, hardcoded char metrics |
| H8 | High | Build | repo root | `libs.*` aliases referenced everywhere; `gradle/libs.versions.toml` absent from repo → Gradle cannot configure |
| M1 | Medium | Performance | RSM.kt:128,134,138,228,244 | `_terminalLines`/`_chatMessages`/`_fileDiffs` grow unbounded across reconnects/sessions |
| M2 | Medium | Correctness | DataStoreManager.kt vs AuthManager.kt | Two parallel DataStore files (`"settings"` vs `"opencode_settings"`); `saveApiKey` never called → paired key lost on restart |
| M3 | Medium | UX | ProjectChatScreen | `listState` declared but no auto-scroll-to-bottom effect on new messages |
| M4 | Medium | Dead code | UploadManager.kt | Never called anywhere; `readBytes()` leaks stream; filename interpolated into ContentDisposition without escaping |
| M5 | Medium | Architecture | ActivityScreen, LogsScreen, SettingsScreen, PreviewScreen | Fully static/fake content; PreviewScreen loads third-party `https://lovable.dev` (leftover template artifact) |
| L1 | Low | Lifecycle | All screens | `collectAsState()` instead of `collectAsStateWithLifecycle()` |
| L2 | Low | Readability | MainActivity | String-based nav routes; README's "type-safe routing" claim false |

## Detailed findings

### C1 — Cleartext blocked on modern devices
Manifest declares neither `android:usesCleartextTraffic="true"` nor a network security config (verified by grep over `app/src/main/AndroidManifest.xml`). Since targetSdk 28+, cleartext HTTP/WS is denied by default; `connect()` hardcodes `http://$ip:8080` / `ws://$ip:8080` (RSM.kt:67-68). On any device ≥ API 28 every connection attempt throws before a byte is sent.
**Fix:** add `network_security_config.xml` permitting cleartext to private LAN ranges only (or user-toggled), plus document why.

### C2 — Mock AI generation inside network layer
`RemoteSessionManager.sendCommand` intercepts `PROMPT`, then after local delays sets agent states "Thinking…"/"Generating code…", appends a hardcoded Kotlin-snippet reply and a fabricated diff for `MainActivity.kt` (lines 221-255, verified). This collides head-on with real daemon frames when H1 is fixed, violates roadmap §9/§18, and poisons state (`_fileDiffs.value = listOf(...)` overwrites server diffs).
**Fix:** delete the block; optimistic user-message append stays, AI content arrives only via frames.

### C3 — Fake QR pairing
`QRScannerPreview` runs a CameraX preview whose analyzer does nothing, then `previewView.postDelayed({ onQrCodeScanned("ip=10.0.2.2;key=dummy_api_key_123") }, 3000)` (AuthScreen.kt:262-263, verified). Pairing is theater; it also overwrites any real in-memory key with `dummy_api_key_123`.
**Fix:** integrate an actual decoder (ML Kit barcode scanning) and remove the timer path entirely.

### H1 — Protocol mismatch end-to-end
Client sends `{"eventType":"PROMPT","payload":"…"}` (WebSocketFrame.kt:7-9; RSM.kt:207-213); bridge matches `cmd.startsWith('PROMPT:')` (main.js:270). Inbound, bridge emits `{type:'AgentState',data:…}` (main.js:77 et al.) while client requires `eventType` field with no default (WebSocketFrame.kt:8) → every inbound frame throws SerializationException (RSM.kt:94-98 swallows it). Full matrix in the audit report. Nothing works until one side moves.

### H2/H3 — File browsing correctness
`mapFileNode` synthesizes ids (`"root_0_1"`) and leaves `path=""`; explorer dispatches `OpenFile(node.name)` — bare name for nested files. Combined with the single global content flow (H3), the code viewer shows stale or wrong content with no loading/error state.

### H4/H5 — Navigation lifecycle bugs
Two independent races: (a) status flaps ERROR→RECONNECTING on each failed attempt while a collector navigates to `"auth"` on ERROR, and AuthScreen itself calls Disconnect on entry — a transient Wi-Fi blip permanently ejects the user and cancels reconnection; (b) `MutableSharedFlow` with default buffer drops emissions made before subscription, so cold-start auto-connect navigation can vanish.

### H6/H7 — Rendering performance
CodeViewer composes every line of a file eagerly (violates roadmap §8 "fast scrolling"). TerminalScreen triggers resize side-effects twice per layout pass with different metrics and no debounce — conflicting TERMINAL_RESIZE frames per rotation/keyboard event.

### H8 — Broken build as committed
All plugin/dependency refs go through `libs.*`, but no `gradle/libs.versions.toml` exists anywhere in the repo (verified: find + git ls-files). INFERENCE: catalog lived outside VCS on the original Windows machine. Restore it (or inline versions) before anything else can be verified by compilation.

## Architecture conformance vs roadmap

| Principle | Status |
|---|---|
| §12 single session manager | Partially — one manager exists, but as un-injectable singleton `object` (RSM.kt:17) |
| §13 repository layer | Absent — ViewModels call RemoteSessionManager directly |
| §9 no fake events | Violated — C2, C3, M5 |
| §14 typed command bus | Present in shape (AppAction), good |
| Thin client | Good intent, broken by C2/M5 fabrication |

## Test coverage assessment
One real unit test file (`MainViewModelTest.kt`) + template examples. Zero tests for reconnect logic, frame routing, diff parsing, ANSI parser. Roborazzi plugin declared; zero screenshot tests exist. The singleton blocks mocking — interface extraction (per prior archived review) remains the prerequisite.

## Prioritized actions

- **P0:** H8 (restore version catalog) → C1 (network security config) → H1 (pick one protocol; migrate bridge to the JSON envelope — client is already typed) 
- **P1:** C2, C3, M5 (delete all fabrication), H2+H3 (thread paths end-to-end, per-request content state), H4 (stop navigating on ERROR; drop auth-screen auto-disconnect), H7 (debounced BoxWithConstraints resize)
- **P2:** H5, H6 (LazyColumn line rendering), M1-M4, L1-L2, extract `IRemoteSession` interface + constructor injection to unlock tests
