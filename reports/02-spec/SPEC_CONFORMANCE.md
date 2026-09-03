# Spec Conformance Audit — claims vs code reality

- **Date:** 2026-08-25 · **HEAD:** 1a0f7f3 · **Method:** static verification of every completion checkbox against source
- **Statuses:** VERIFIED (evidence found) / PARTIAL (exists but incomplete or different from claim) / FALSE (no implementation or contradicted by evidence) / UNVERIFIABLE

## roadmap.md — Engineering Roadmap & Sprints

### Sprint 1 — Core Architecture & Connectivity
| Claim | Status | Evidence / Notes |
|---|---|---|
| Configure Navigation Compose | VERIFIED | MainActivity.kt: NavHost + string routes |
| Configure Material Theme | VERIFIED | ui/theme/Theme.kt wired in MainActivity |
| Resolve DI strategy (Hilt removed) | VERIFIED | no hilt references anywhere; manual construction |
| Configure Ktor | VERIFIED | KtorClient.kt: OkHttp engine, JSON negotiation, WS ping 15s |
| Configure DataStore | PARTIAL | DataStoreManager.kt exists but plain-text; second parallel store in AuthManager.kt |
| Build REST client | PARTIAL | client exists; REST barely used (upload manager dead code) |
| Build WebSocket wrapper | VERIFIED | RSM startConnectionLoop |
| RemoteSessionManager reconnect + heartbeat | PARTIAL | backoff loop real (RSM.kt:72-118, 1s→30s doubling); "heartbeat" is only the Ktor `pingInterval` plugin config, not the claimed background coroutine loop |
| Remove all local mock logic | FALSE | C2/C3/M5 in CODE_REVIEW.md — mock AI, fake QR, static screens all present |

### Sprint 2 — Authentication & Workspace Management
| Claim | Status | Evidence / Notes |
|---|---|---|
| QR Code Scanner UI | PARTIAL | Camera preview yes; decode FAKE — timer injects dummy payload (AuthScreen.kt:262-263); no ML Kit/ZXing dep |
| API key exchange / secure token storage | PARTIAL | key held in memory only (`KtorClient.updateApiKey`); `AuthManager` + `saveApiKey` are dead code → nothing persisted, nothing exchanged with server |
| Device management (Remember/Rename/Remove) | FALSE | DevicesScreen is an mDNS discovery browser; `PairingAction.RemoveDevice` is a no-op (MainViewModel.kt:91); no device persistence |
| Fetch/display workspaces | VERIFIED | WorkspacesScreen + WORKSPACE_LIST frame handling |
| Open/switch active project | VERIFIED | OPEN_WORKSPACE command path |
| Project search | VERIFIED | client-side filter in WorkspacesScreen |

### Sprint 3 — Remote Filesystem & Reading
| Claim | Status | Evidence / Notes |
|---|---|---|
| Directory tree lazy loading | PARTIAL | whole tree arrives as one frame; expansion is client-side over complete tree — not per-directory network laziness |
| Expand/collapse/search | PARTIAL | expand/collapse yes; **no search field anywhere in explorer** |
| Fetch remote file contents | PARTIAL | works for root-level names only — nested files broken (H2) |
| Syntax highlighting | PARTIAL | hand-rolled tokenizer in CodeViewer.kt:35-141; keywords/strings/comments/numbers only, no library |
| Scroll restoration + opened-file caching | FALSE | verticalScroll state resets; single-slot content flow only |
| Line numbers, word wrap, copy | UNVERIFIABLE | not positively located during review; treat as unproven until checked on device |

### Sprint 4 — AI Chat & Execution Telemetry
| Claim | Status | Evidence / Notes |
|---|---|---|
| Chat connected to WS streaming tokens | FALSE at integration | sends PROMPT frame, then fabricates mock reply locally (RSM.kt:221-255); no token-level streaming concept exists inbound (CHAT_MESSAGE arrives complete) |
| Markdown + code block rendering | PARTIAL | MarkdownViewer handles fenced code blocks + plain text; no headings/lists/bold/links |
| Voice input | VERIFIED | RecognizerIntent ACTION_RECOGNIZE_SPEECH launcher, ProjectChatScreen.kt:49-60,329-334 |
| Activity Monitor hooked to real states | FALSE | ActivityScreen is 100% hardcoded ("Writing Header.tsx", fake timestamps) |
| Conversation history sync | FALSE | no history fetch/sync code located |

### Sprint 5 — Diffs & Terminal
| Claim | Status | Evidence / Notes |
|---|---|---|
| Diff Review UI accept/reject | PARTIAL | UI + command plumbing exist; bridge has no ACCEPT_HUNK handler → integration FALSE |
| Full patch context + copy | VERIFIED | DiffReviewScreen |
| Terminal connected to live PTY stream | FALSE at integration | protocol mismatch means TERMINAL frames never parse server-side; bridge's own handler shells through `cmd /c` (Windows-only) |
| ANSI colors/history/scrollback | PARTIAL | AnsiParser used (TerminalScreen.kt:90) but SGR-only; scrollback unbounded list |
| Interactive inputs (Ctrl+C/Z/D, Tab) | PARTIAL | control-key chips send codes; no IME-action composition coverage verified |
| PTY resizing sync | PARTIAL | resize frames sent but double-fire bug (H7) and no server-side PTY resize handler |

### Sprint 6 — Source Control & Background Tasks
| Claim | Status | Evidence / Notes |
|---|---|---|
| Running tasks monitor | PARTIAL | TASK_UPDATED/TASK_REMOVED handled; depends on working transport |
| Task controls Stop/Restart/View Output | PARTIAL | Stop only; no restart or output view |
| Git dashboard status/branch/pull/push/fetch | PARTIAL | GIT: plumbing both sides, format mismatched; UI buttons present |
| Commit/Stash/Cherry-pick actions | FALSE | no UI entry points; backend would accept raw strings only |
| Push notifications | FALSE | zero NotificationManager/Firebase-messaging usage |

### Sprint 7 — Hardening, Polish & Launch
| Claim | Status | Evidence / Notes |
|---|---|---|
| Optimization | PARTIAL | OkHttp pooling default; file-tree LazyColumn anti-pattern still present (H6) |
| UX Polish (haptics/animations/a11y/foldables) | PARTIAL | split layout via BoxWithConstraints>600dp; no WindowSizeClass API; a11y unverified |
| Unit tests (ViewModels, Repositories, Session Manager) | OVERSTATED→FALSE | only MainViewModelTest.kt; no repository/session-manager tests |
| UI & Screenshot tests (Roborazzi) | FALSE | plugin declared, zero screenshot tests written |
| ProGuard/R8 | FALSE | `isMinifyEnabled = false` (app/build.gradle.kts release block) |
| CI/CD + Crash Reporting | FALSE | no .github/workflows; no crashlytics wiring despite Firebase deps |
| Release to Internal Testing / Play Store | UNVERIFIABLE | signing config exists (env-based keystore); no artifacts/tracks evidence in repo |

## tasks.md — Integration Phases

| Task | Claimed | Actual | Notes |
|---|---|---|---|
| 1.1 serialization plugin/deps | [x] | VERIFIED | kotlin.serialization plugin applied |
| 1.2 schema entities | [x] | VERIFIED | Schemas.kt complete set |
| 1.3 WebSocket envelope | [x] | VERIFIED client-side | WebSocketFrame.kt |
| 1.4 Ktor lifecycle refactor | [x] | PARTIAL | client routes decoded frames; **goal "transition to JSON protocol" NOT met end-to-end** — bridge still raw-string |
| 2.1 connection state machine | [x] | VERIFIED | ConnectionState sealed set; SEARCHING/PAIRING unused |
| 2.2 NSD mDNS `_opencode._tcp` | [x] | PARTIAL→FALSE end-to-end | NsdHelper exists and is used, but NO server ever advertises `_opencode._tcp` → discovery can never succeed |
| 2.3 heartbeats | [x] | PARTIAL | Ktor pingInterval config ≠ described coroutine loops |
| 2.4 exponential backoff | [x] | VERIFIED | RSM.kt:75,114-115 |
| 3.1 PIN/QR pairing UI | [x] | PARTIAL | UI shell real, scan fake (C3) |
| 3.2 secure handshake JWT/API key | [x] | PARTIAL | AuthManager dead code; bridge issues Math.random token never enforced |
| 3.3 EncryptedDataStore | [x] | **FALSE** (reverted this cleanup) | plain-text preferencesDataStore; allowBackup=true compounds it |
| 4.1 ANSI parser | [x] | VERIFIED | used by TerminalScreen |
| 4.2 keyboard interceptor | [x] | PARTIAL | control chips only |
| 4.3 PTY resize sync | [x] | PARTIAL | sent, but buggy double-fire; server side absent |
| 5.1 Speech Recognizer | [x] | VERIFIED | via RecognizerIntent |
| 5.2 document picker | [x] | PARTIAL | picker appends cosmetic "[Attached: name]" text — no upload occurs |
| 5.3 multipart chunk upload manager | [x] | PARTIAL/FALSE | UploadManager unused dead code; no chunking; no progress reporting |
| 6.1 adaptive split view | [x] | PARTIAL | BoxWithConstraints heuristic, not WindowSizeClass; functionally splits |
| 6.2 hunk-level acceptance | [x] | PARTIAL | full client UI; no server handler |
| 6.3 advanced git actions | [x] | FALSE | no branching/stash/cherry-pick UI |

## Summary

- **roadmap sprints:** ~14 VERIFIED-equivalent, ~15 PARTIAL, ~10 FALSE/OVERSTATED
- **tasks phases:** 8 VERIFIED, 9 PARTIAL, 3 FALSE, rest partial-by-integration
- **Pattern:** client-side scaffolding was marked done while (a) the server half was never built/migrated and (b) several marquee features are simulated locally. The most damaging FALSE items: end-to-end protocol migration (1.4), NSD discovery (2.2), encrypted storage (3.3), notifications, CI/CD, R8.

**Recommendations**
1. Re-mark every PARTIAL/FALSE row above before using these docs as sprint truth (docs corrected where already proven: Sprint 1, Task 3.3).
2. Adopt a rule: a checkbox may only be ticked when the *end-to-end* flow demonstrably works against the real bridge — add an integration smoke test as the gate.
