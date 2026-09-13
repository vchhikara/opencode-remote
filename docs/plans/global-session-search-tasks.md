# Implementation plan: Global cross-workspace session search

Design reference: [`adr/0002-global-cross-workspace-session-search.md`](../../adr/0002-global-cross-workspace-session-search.md) — locked, implement against it as written; any deviation found necessary during implementation should update the ADR, not silently diverge from it.

Each phase should end in a committable, working state. Do not start a phase's subtasks out of order — later phases depend on earlier ones being real and tested, not stubbed.

---

## Phase 1 — Bridge: read-only DB access layer

**Goal:** a tested, isolated module that can answer "what sessions exist globally" without touching any live `opencode serve` process.

- [ ] 1.1 Add `bridge/sessionStore.js` (or similar) opening `~/.local/share/opencode/opencode.db` via `node:sqlite`'s `DatabaseSync` in `{ readOnly: true }` mode, lazily on first use, cached as a module-level singleton.
- [ ] 1.2 Implement `listAllSessions({ limit, cursor })` running the join query from the ADR, returning `{ sessions, nextCursor }`. Decide and document the concrete cursor encoding (e.g. `time_updated` + `id` of the last row — plain `OFFSET` pagination is wrong here since new sessions can be created between pages).
- [ ] 1.3 Compute `reachable` per row (`fs.existsSync` + `isDirectory()`) inside this module, not in the WS handler.
- [ ] 1.4 Handle the missing/locked/corrupt DB case: catch the `DatabaseSync` open/query error and surface a plain `Error` with a clear message — do not let it throw raw across the WS boundary.
- [ ] 1.5 Unit tests (`bridge/test/`, following the existing `envelope.test.js` style): empty DB, multiple projects, pagination boundary (exact `limit` count, then one more), a row whose `worktree` doesn't exist on disk, DB-file-missing case.
- [ ] 1.6 Manual check: run against the real `~/.local/share/opencode/opencode.db` on this machine and confirm the output matches the earlier live `sqlite3` query's rows.

**Exit criteria:** `node --test` passes; module works standalone with no WS/bridge process needed to exercise it.

---

## Phase 2 — Bridge: wire protocol

**Goal:** `FETCH_ALL_SESSIONS` and `OPEN_SESSION_GLOBAL` are live, authenticated, and tested end-to-end at the WS layer.

- [ ] 2.1 Add `case 'FETCH_ALL_SESSIONS'` to the WS switch in `bridge/main.js`, calling Phase 1's `listAllSessions`, replying `ALL_SESSIONS_LIST`. Payload parsing must accept a missing/empty payload (defaults) without throwing.
- [ ] 2.2 Add `case 'OPEN_SESSION_GLOBAL'`, implementing the 6-step handling from the ADR: `WORKSPACE_ROOT` boundary check → existence check → `activeWorkspace` swap + `workspaces[]` registration → conditional `startOpenCodeServer()` restart → `ocSessionId` set + `persistSessionId()` → `SESSION_OPENED` reply → `WORKSPACE_LIST` broadcast. Extract a shared `resolveWithinWorkspaceRoot(dir)` helper (boundary check + existence/directory check) and point `OPEN_WORKSPACE` and `ADD_WORKSPACE` at it too, so this feature doesn't add a third inline copy of the same check.
- [ ] 2.3 Error paths: unauthenticated socket (existing top-level check already covers this — confirm, don't re-implement), out-of-root worktree, unreachable worktree, unknown session id (the `opencode serve` restart + a 404 from `SWITCH_SESSION`'s existing `/session/:id` check should already surface this — verify).
- [ ] 2.4 Extend `bridge/test/envelope.test.js` (or a new file) with frame-shape tests for both new eventTypes — request/response envelope shape, matching the existing test's style, covering at minimum: happy path for each, one error path for each.
- [ ] 2.5 Update `bridge/API.md` documenting both new frames (request/response shape, error cases) — this file already documents the existing protocol, so this is filling in a gap, not creating new doc structure.

**Exit criteria:** `node --test` passes in `bridge/`; manual WS client (or `wscat`) round-trip against a running bridge instance confirms both frames work against the real `opencode.db`.

---

## Phase 3 — Android: data layer

**Goal:** `RemoteSessionManager` can fetch and open global sessions; the Files-bug lesson (verify decode shape against the real bridge payload, don't assume) is explicitly re-applied here, not just remembered.

- [ ] 3.1 Add `GlobalSessionDto` (`id`, `title`, `worktree`, `updatedAt`, `reachable`) and `AllSessionsListDto`/pagination-cursor DTOs matching Phase 2's actual wire shape exactly — read the real payload from a running bridge (or the Phase 2 tests) before writing the decode, don't guess the shape from the ADR prose alone.
- [ ] 3.2 Add `_allSessions: MutableStateFlow<List<GlobalSessionDto>>` (+ loading/error state, following whatever pattern `_fileTree`/`_sessions` already use) and `fetchAllSessions(cursor: String? = null)` to `RemoteSessionManager`.
- [ ] 3.3 Add `openGlobalSession(id: String, worktree: String)` sending `OPEN_SESSION_GLOBAL`, and a `"SESSION_OPENED"` case in the frame-dispatch `when` that updates `_activeWorkspace` and clears stale per-workspace state the same way the Files-fix commit (`91f1dba`) already made `WORKSPACE_OPENED` do — this is the same stale-cache class of bug, don't reintroduce it here by forgetting to clear `_fileTree`/session-scoped state on this new path too.
- [ ] 3.4 Unit tests in `SessionManagerTest.kt`: decode `ALL_SESSIONS_LIST` correctly (including an unreachable row), decode `SESSION_OPENED` and confirm it clears stale state, and — mirroring `testFileTreeEvent`'s original mistake — make sure the test payload is built to match the *real* bridge shape from Phase 2, not a convenient guess.

**Exit criteria:** `./gradlew testDebugUnitTest` passes; new tests fail if run against the old (pre-Phase-2) frame shape, proving they're actually checking something.

---

## Phase 4 — Android: UI

**Goal:** a user can see and open a session from any workspace.

- [ ] 4.1 ~~Decide screen placement~~ **Resolved:** new top-level screen, not a tab on Sessions. `SessionsScreen.kt` is currently a plain `Column` → `ScreenHeader` → `LazyColumn`, no `TabRow`/segmented-control pattern exists there to extend, and the global list carries an extra `reachable`/disabled-row concept the existing `SessionRow` doesn't need to grow to support. Add a second `HeaderAction("All Workspaces")` to the existing `ScreenHeader` (which already supports a `HeaderAction` slot) to navigate into the new screen, reusing `ScreenHeader`/`LazyColumn`/`EmptyNote` exactly as `SessionsScreen.kt` already does.
- [ ] 4.2 Build the list UI (new `GlobalSessionsScreen.kt`, mirroring `SessionsScreen.kt`'s structure): title + worktree basename subtitle, sorted by `updatedAt` desc, unreachable rows visibly disabled with the full path shown (not hidden, not just greyed with no explanation).
- [ ] 4.3 Wire pull-to-refresh / pagination trigger to `fetchAllSessions(cursor)`.
- [ ] 4.4 Wire row tap (reachable rows only) to `openGlobalSession`, with a loading state while the bridge restarts `opencode serve` — this is not instant, the UI must not look frozen or double-fire on a second tap.
- [ ] 4.5 On `SESSION_OPENED`, navigate into the existing chat/session view, matching current session-switch navigation exactly.
- [ ] 4.6 Error-state UI for `FETCH_ALL_SESSIONS`/`OPEN_SESSION_GLOBAL` `ERROR` replies (DB unreachable, worktree gone, out-of-root) — user-readable, not a raw error string dump, consistent with how other screens in this app surface bridge `ERROR` frames.

**Exit criteria:** manual on-device test — open a session from a workspace other than the currently active one, confirm it lands correctly in the chat view with the right history; open a session whose worktree no longer exists and confirm it's disabled, not crashing.

---

## Phase 5 — Integration verification & release

- [ ] 5.1 Full manual pass on a real device against the real bridge + real `opencode.db`: global list loads, an unreachable session is shown disabled, opening a session from a different workspace correctly restarts `opencode serve` and switches history, opening a session already in the active workspace is a fast no-op.
- [ ] 5.2 Re-run full bridge (`node --test`) and Android (`./gradlew test`) suites together, confirm no regressions in existing Files/Sessions/Workspace tests.
- [ ] 5.3 Update `android-application/CHANGELOG.md` with a new MINOR version row (new capability, not a bugfix — per the versioning scheme in that file) and bump `versionCode`/`versionName` in `app/build.gradle.kts` accordingly.
- [ ] 5.4 Update `bridge/README.md` / `bridge/API.md` if Phase 2's doc update didn't already cover the end-user-facing description of the feature.

**Exit criteria:** both test suites green, CHANGELOG entry present, version bumped, manual on-device verification evidence captured (screenshot or description) before calling this done.

---

## Notes for whoever executes this plan

- Do not skip Phase 1's standalone unit tests to "save time" by going straight to the WS layer — the Files bug happened specifically because a test was built against an assumed shape instead of the real one; Phase 1 in isolation is what prevents that here.
- Phase 2.2's shared-helper extraction is the only sanctioned scope addition in this plan — everything else is strictly additive, no other refactors while implementing this feature.
