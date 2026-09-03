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
| 1 | 1.1.1 Bridge SSE client (log only) | Not started | | | |
| 1 | 1.1.2 Bridge relay STREAM_* frames | Not started | | | |
| 1 | 1.1.3 Confirm final CHAT_MESSAGE/FILE_DIFF unchanged | Not started | | | |
| 1 | 1.2.1 Android streaming state in RemoteSessionManager | Not started | | | |
| 1 | 1.2.2 Android ChatScreen streaming bubble + tool status | Not started | | | |
| 1 | Phase 1 merge gate (baseline rerun) | Not started | | | |
| 2 | 2.1.1 Bridge relay PERMISSION_REQUEST/QUESTION_REQUEST | Not started | | | |
| 2 | 2.1.2 Bridge handle PERMISSION_REPLY/QUESTION_REPLY | Not started | | | |
| 2 | 2.1.3 Check/gate existing auto-approve | Not started | | | |
| 2 | 2.2.1 Android permission/question DTOs + flows | Not started | | | |
| 2 | 2.2.2 Android permission/question UI card | Not started | | | |
| 2 | 2.2.3 KILL_TASK produces visible "Cancelled" message | Not started | | | |
| 2 | 2.3.1 [VERIFY LIVE] mid-task steering / message queuing | Not started | | | |
| 2 | Phase 2 merge gate (baseline rerun) | Not started | | | |
| 3 | 3.1.1 Bridge LIST_SESSIONS | Not started | | | |
| 3 | 3.1.2 Bridge SWITCH_SESSION | Not started | | | |
| 3 | 3.1.3 Bridge NEW_SESSION | Not started | | | |
| 3 | 3.2.1 Bridge persist ocSessionId across restarts | Not started | | | |
| 3 | 3.3.1 Android session DTOs/flows | Not started | | | |
| 3 | 3.3.2 Android session picker screen | Not started | | | |
| 3 | 3.4.1 Stretch: session forking | Not started | | | |
| 3 | Phase 3 merge gate (baseline rerun) | Not started | | | |
| 4 | 4.1.1 Bridge PTY-backed TERMINAL | Not started | | | |
| 4 | 4.1.2 Bridge real TERMINAL_RESIZE | Not started | | | |
| 4 | 4.2.1 Android streaming terminal output (append not replace) | Not started | | | |
| 4 | 4.2.2 Android wire TERMINAL_RESIZE to real size changes | Not started | | | |
| 4 | Phase 4 merge gate (baseline rerun) | Not started | | | |
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
