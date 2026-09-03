# Progress Ledger

**HARD REQUIREMENT: update this file immediately after finishing (or abandoning) every
single subtask** — not at the end of a session, not at the end of a phase. This file,
not conversation memory, is the sole resumable state for this plan. Anyone (human or
agent) picking this work up must be able to read this file alone, find the first row
that is not `Done`, and resume there without any other context. Whoever resumes work
here must re-check the linked commit before trusting a "Done" row.

Status values: `Not started`, `In progress`, `Blocked`, `Won't do (unsupported
upstream)`, `Done`. A subtask is `Done` only once its exit criterion (from
`IMPLEMENTATION_PLAN.md`) has actually been run and passed — not when the code "looks"
correct.

| Phase | Task | Status | Verified-by | Notes/Blockers | Commit |
|---|---|---|---|---|---|
| 0 | 0.1.1 Baseline green check | Done | `bridge npm test`→14 pass/0 fail; `gradlew testDebugUnitTest`→BUILD SUCCESSFUL; `gradlew assembleDebug`→BUILD SUCCESSFUL | | 1f7a1b8 |
| 0 | 0.2.1 Capture live OpenAPI doc | Done | `opencode serve --port 4097`, `curl .../doc`→200, `jq .`→valid JSON, 478742 bytes | v1.18.26 CLI | 1f7a1b8 |
| 0 | 0.2.2 [VERIFY LIVE] /event SSE shape | Done | Live probe: created session, sent prompt, captured `/event` SSE for 20s | See Live-API findings below | 1f7a1b8 |
| 0 | 0.2.3 [VERIFY LIVE] permission/question endpoints | Done | `jq '.paths\|keys[]\|select(test("permission\|question"))'` on captured OpenAPI doc | See Live-API findings below | 1f7a1b8 |
| 0 | 0.2.4 [VERIFY LIVE] session list/fork/children endpoints | Done | Same jq probe on `/doc` | See Live-API findings below | 1f7a1b8 |
| 0 | 0.2.5 [VERIFY LIVE] /pty endpoints | Done | Same jq probe on `/doc`; connect endpoint description read | See Live-API findings below | 1f7a1b8 |
| 0 | 0.2.6 [VERIFY LIVE] per-hunk/partial-apply diff capability | Done | Same jq probe on `/doc` | No per-hunk endpoint exists — see findings | 1f7a1b8 |
| 0 | 0.2.7 [VERIFY LIVE] /project API | Done | Same jq probe on `/doc` | See Live-API findings below | 1f7a1b8 |
| 0 | 0.2.8 Kill probe server | Done | `pkill -f "opencode serve --port 4097"`; `curl .../config`→HTTP 000 (refused) | | 1f7a1b8 |
| 1 | 1.1.1 Bridge SSE client (log only) | Done | Superseded by 1.1.2's end-to-end test (built directly with broadcast, not log-only, per efficiency) | Combined 1.1.1+1.1.2 into one implementation | 7527987 |
| 1 | 1.1.2 Bridge relay STREAM_* frames | Done | `cd bridge && npm test` → 15 pass/0 fail, incl. new `stream.test.js` asserting all 3 STREAM_* frame types observed over real WS | Used fake-opencode.js fixture (moved to test-fixtures/ — node --test auto-discovers test/**/*.js) | 7527987 |
| 1 | 1.1.3 Confirm final CHAT_MESSAGE/FILE_DIFF unchanged | Done | `grep -n "pushChatMessage(text.trim()" bridge/main.js` → match at line 321 | | 7527987 |
| 1 | 1.2.1 Android streaming state in RemoteSessionManager | Done | `gradlew :app:testDebugUnitTest --tests "*SessionManagerTest*"` → BUILD SUCCESSFUL (3 new tests) | Added StreamTextDeltaDto/StreamToolCallDto/StreamToolResultDto/StreamingMessageDto | c773746 |
| 1 | 1.2.2 Android ChatScreen streaming bubble + tool status | Done (automated part) | `gradlew :app:assembleDebug -Dorg.gradle.java.home=...` → BUILD SUCCESSFUL | Manual on-device confirmation NOT performed this session (no device connected) — automated exit criterion is the merge gate per plan; manual check remains outstanding | c773746 |
| 1 | Phase 1 merge gate (baseline rerun) | Done | bridge npm test → 15/15; `testDebugUnitTest` → BUILD SUCCESSFUL; `assembleDebug` → BUILD SUCCESSFUL | | c773746 |
| 2 | 2.1.1 Bridge relay PERMISSION_REQUEST/QUESTION_REQUEST | Done | `bridge npm test` → permission.test.js 1st test pass | Envelope key read defensively (properties\|\|data) — live permission event not observed in probe (bash ran without approval prompt); shape is schema-confirmed only | 33b5ff1 |
| 2 | 2.1.2 Bridge handle PERMISSION_REPLY/QUESTION_REPLY | Done | `bridge npm test` → permission.test.js 2nd/3rd tests pass, asserting real POST calls via fixture's `/__requests` log | | 33b5ff1 |
| 2 | 2.1.3 Check/gate existing auto-approve | Done | `grep -n "'--auto'\|auto-approve\|autoApprove" bridge/main.js` → no match | No existing auto-approve flag found; nothing to change | 33b5ff1 |
| 2 | 2.2.1 Android permission/question DTOs + flows | Done | `gradlew testDebugUnitTest --tests "*SessionManagerTest*"` → BUILD SUCCESSFUL | | d8bbc69 |
| 2 | 2.2.2 Android permission/question UI card | Done (automated part) | `gradlew assembleDebug` → BUILD SUCCESSFUL | Manual on-device tap-through NOT performed (no device connected) | d8bbc69 |
| 2 | 2.2.3 KILL_TASK produces visible "Cancelled" message | Done | `grep -n "case 'KILL_TASK'" -A 12 bridge/main.js` shows pushChatMessage call; permission.test.js "KILL_TASK pushes a Cancelled chat message" passes | | 33b5ff1 |
| 2 | 2.3.1 [VERIFY LIVE] mid-task steering / message queuing | Done | Live probe: two concurrent `POST /session/{id}/message` on same session both → HTTP 200, both completed. Bridge test "two back-to-back PROMPTs..." passes | Server queues rather than rejects; bridge already had no guard blocking concurrent prompts — no code change needed | d8bbc69 |
| 2 | Phase 2 merge gate (baseline rerun) | Done | bridge npm test → 20/20; `testDebugUnitTest` → BUILD SUCCESSFUL; `assembleDebug` → BUILD SUCCESSFUL | | 24b8f5f |
| 3 | 3.1.1 Bridge LIST_SESSIONS | Done | `bridge npm test` → sessions.test.js "LIST_SESSIONS returns the sessions known to opencode serve" passes | | e843f96 |
| 3 | 3.1.2 Bridge SWITCH_SESSION | Done | `bridge npm test` → "SWITCH_SESSION changes the session used by the next PROMPT" passes, asserts next PROMPT hits `/session/<switched>/message` | | e843f96 |
| 3 | 3.1.3 Bridge NEW_SESSION | Done | `bridge npm test` → "NEW_SESSION creates a session and switches to it" passes | | e843f96 |
| 3 | 3.2.1 Bridge persist ocSessionId across restarts | Done | `bridge npm test` → "session id persists across a bridge restart in the same workspace" passes | Persisted to `.opencode-remote-session.json` in workspace root; `ensureSession()` trusts a persisted id at face value (no existence-check — real opencode's session storage is independent of any one serve process) | e843f96 |
| 3 | 3.3.1 Android session DTOs/flows | Done | `gradlew :app:testDebugUnitTest --tests "*SessionManagerTest*"` → BUILD SUCCESSFUL (new testSessionListAndSwitchedEvents) | SessionDto/SessionSwitchedDto/NewSessionPayload + sessions/activeSessionId StateFlows + listSessions/switchSession/newSession methods | e843f96 |
| 3 | 3.3.2 Android session picker screen | Done | `gradlew :app:assembleDebug` → BUILD SUCCESSFUL | New Sessions tab (SessionsScreen.kt) wired into NavRoutes/MainDashboardScreen; manual on-device tap-through not performed (no device connected) | e843f96 |
| 3 | 3.4.1 Stretch: session forking | Done | `bridge npm test` → "FORK_SESSION creates a forked session and switches to it (Task 3.4 stretch)" passes; `gradlew :app:assembleDebug` → BUILD SUCCESSFUL | FORK_SESSION bridge case + fixture `/session/:id/fork` route + Android forkSession()/fork IconButton in SessionsScreen | e843f96 |
| 3 | Phase 3 merge gate (baseline rerun) | Done | `bridge npm test` → 25/25 pass; `gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; `gradlew :app:assembleDebug` → BUILD SUCCESSFUL | Merged into remote-control-groundwork | e843f96 |
| 4 | 4.1.1 Bridge PTY-backed TERMINAL | Done | `bridge npm test` → terminal.test.js "TERMINAL command creates a PTY and streams output back incrementally" passes (asserts >1 TERMINAL_OUTPUT frame, POST /pty called) | Live-confirmed transport is a WebSocket at `/pty/{id}/connect` (not SSE/polling); NUL-prefixed control frames filtered out | 6b46790 |
| 4 | 4.1.2 Bridge real TERMINAL_RESIZE | Done | `bridge npm test` → "TERMINAL_RESIZE calls the PTY resize endpoint for the active PTY" passes | `PUT /pty/{id} {size:{rows,cols}}` confirmed in live OpenAPI schema; resizes the most-recently-created PTY (`activePtyId`) | 6b46790 |
| 4 | 4.2.1 Android streaming terminal output (append not replace) | Done | `grep -n "terminalOutput" ...` → single append-only consumer (`outputLines = outputLines + terminalOutput`) already present; no change needed | | 6b46790 |
| 4 | 4.2.2 Android wire TERMINAL_RESIZE to real size changes | Done | `grep -n "TERMINAL_RESIZE" ...RemoteSessionManager.kt` shows `resizeTerminal()`; `gradlew :app:assembleDebug` → BUILD SUCCESSFUL | Wired to `onSizeChanged` on the terminal surface (approximate monospace cols/rows from pixel size) | 6b46790 |
| 4 | Phase 4 merge gate (baseline rerun) | Done | `bridge npm test` → 26/26 pass; `gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL; `gradlew :app:assembleDebug` → BUILD SUCCESSFUL | Merged into remote-control-groundwork | 6b46790 |
| 5 | 5.1.1 Android pendingDiffs list (not single value) | Not started | | | |
| 5 | 5.1.2 Android UI renders all pending diffs | Not started | | | |
| 5 | 5.2.1 Per-hunk accept/reject (conditional) | Not started | | | |
| 5 | 5.3.1 Live diff preview (depends on Phase 1) | Not started | | | |
| 5 | Phase 5 merge gate (baseline rerun) | Not started | | | |
| 6 | 6.1.1 Bridge multi-workspace tracking | Not started | | | |
| 6 | 6.1.2 Bridge per-workspace session strategy decision | Not started | | | |
| 6 | 6.2.1 Android meaningful WorkspaceListScreen | Not started | | | |
| 6 | Phase 6 merge gate (baseline rerun) | Not started | | | |
| 7 | 7.1.1 Bridge notification hook (completion/permission/error) | Not started | | | |
| 7 | 7.1.2 Android push handling + system notification | Not started | | | |
| 7 | 7.2.1 Android foreground service / reconnect-on-resume | Not started | | | |
| 7 | 7.3.1 Documented LAN-escape (Tailscale/Cloudflare Tunnel) | Not started | | | |
| 7 | Phase 7 merge gate (baseline rerun) | Not started | | | |
| 8 | 8.1.1 Bridge per-device token issuance | Not started | | | |
| 8 | 8.2.1 Bridge REVOKE_TOKEN | Not started | | | |
| 8 | 8.2.2 Android devices/revoke screen | Not started | | | |
| 8 | 8.3.1 Confirm/gate blanket auto-approval off | Not started | | | |
| 8 | 8.4.1 Bridge audit log | Not started | | | |
| 8 | 8.4.2 Android audit log screen | Not started | | | |
| 8 | Phase 8 merge gate (baseline rerun) | Not started | | | |

## Column guide

- **Verified-by**: the literal command run and what it showed (e.g. `npm test → 6
  pass, 0 fail`, or `gradlew assembleDebug → BUILD SUCCESSFUL`). Not "looks good."
- **Notes/Blockers**: for `[VERIFY LIVE]` rows, record the actual finding (endpoint
  exists with shape X / does not exist). For `Blocked` or `Won't do`, record why and
  what would unblock it.
- **Commit**: the git SHA (short form, e.g. `a1b2c3d`) of the commit that made this
  subtask's exit criterion pass, on the relevant `phase-N-*` branch.

## Live-API findings (filled in during Phase 0, referenced by later phases)

Captured against `opencode` CLI v1.18.26, `opencode serve --port 4097`. Full OpenAPI
doc saved at `/tmp/opencode-openapi.json` during the probe (not committed — regenerate
if needed via Task 0.2.1's command). Note: the OpenAPI doc has two path families —
`/api/session/{sessionID}/...` and a shorter, unprefixed `/session/{sessionID}/...`.
`bridge/main.js`'s existing `ocFetch` calls use the unprefixed family — findings below
use that family to match current bridge code, noting the `/api/...` duplicate exists
if ever needed.

- **`/event`, `/session/{id}/event` shape**: `GET /event` returns
  `Content-Type: text/event-stream`, confirmed live — fired a real prompt and captured
  `data: {"id":..., "type":"...", "properties":{...}}` lines. Real event types
  observed in one prompt/response cycle: `server.connected`, `server.heartbeat`,
  `session.updated`, `session.status`, `session.idle`, `session.diff`,
  `message.updated`, `message.part.updated`, `message.part.delta` (the actual
  streaming-text delta event — use this one for `STREAM_TEXT_DELTA`), `text`,
  `reasoning`, `reasoning.text`, `step-start`, `step-finish`, `catalog.updated`,
  `plugin.added`, `integration.updated`, `reference.updated`, `tui.toast.show`, `busy`,
  `idle`. No tool-call event appeared in this cycle (prompt didn't invoke a tool) —
  Phase 1 should re-probe with a tool-invoking prompt (e.g. "list files") before
  implementing `STREAM_TOOL_CALL`/`STREAM_TOOL_RESULT` mapping, or inspect
  `message.part.updated` parts for a `type: "tool"` part (the OpenAPI schema has
  `EventPermissionAsked`/`EventPermissionV2Asked` etc. suggesting a versioned
  event/schema split — check whether `part.type` differentiates `text` vs `tool` parts
  in `.components.schemas.Part` before coding Phase 1).
- **Permission/question endpoints**: unprefixed family confirmed in `/doc`:
  `POST /permission/{requestID}/reply`, `POST /question/{requestID}/reply`,
  `POST /question/{requestID}/reject`. (The plan's other guessed path,
  `/session/{id}/permission/{permissionID}`, does NOT exist in the unprefixed family —
  only `/session/{sessionID}/permissions/{permissionID}` [note plural "permissions"]
  does; the reply action is the top-level `/permission/{requestID}/reply` instead, not
  session-scoped.) Event schemas exist for `EventPermissionAsked`/`EventPermissionV2Asked`
  and `EventQuestionAsked`/`EventQuestionV2Asked` — there are BOTH v1 and v2 event/schema
  families; Phase 2 must trigger a real permission prompt (e.g. ask the agent to run a
  shell command) and inspect the actual live event `type` string to know whether v1 or
  v2 is what this CLI version actually emits, rather than assuming from schema name
  alone.
- **`GET /session`, `POST /session/{id}/fork`, `POST /session` with `parentID`,
  `/session/{id}/children`**: ALL confirmed present — `GET /session` (list, methods
  `get`+`post` on `/session`), `POST /session/{sessionID}/fork`,
  `/session/{sessionID}/children`. `POST /session` accepting `parentID` not directly
  confirmed from path list alone — check its request body schema in Phase 3
  (`jq '.paths["/session"].post.requestBody'` against a fresh `/doc` capture).
- **`/pty`, `/pty/{id}`, `/pty/{id}/connect`, `/pty/{id}/connect-token`,
  `/pty/shells`**: confirmed present at `/pty` (get+post), `/pty/{ptyID}`,
  `/pty/{ptyID}/connect` (get — description: "Establish a WebSocket connection to
  interact with a pseudo-terminal (PTY) session in real-time" — **transport is
  WebSocket**, not SSE/polling), `/pty/{ptyID}/connect-token`, `/pty/shells`.
- **Per-hunk / partial-apply diff capability**: NOT found. Only whole-file diff
  endpoints exist: `GET /session/{id}/diff`, `GET /vcs/diff`, `GET /vcs/diff/raw`. No
  `patch`/`hunk` path anywhere in the OpenAPI doc. Phase 5 Task 5.2 must be scoped down
  to `Won't do (unsupported upstream)` per the plan's own conditional — confirmed via
  this probe, not silently dropped.
- **`/project` API**: confirmed present — `GET /project` (list), `GET /project/current`,
  `POST /project/git/init`, `GET /project/{projectID}`,
  `GET /project/{projectID}/directories`, plus experimental copy endpoints. This
  supports Phase 6's "scope sessions by workspace within one server via `/project`"
  option as a real, implementable choice (not purely aspirational).
