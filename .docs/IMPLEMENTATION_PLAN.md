# Implementation Plan — Remote Control Roadmap

> Note: the Android client directory has since been renamed from
> `opencode_remote_android_app/` to `android-application/`. References below
> use the old name as it was at the time this plan was written — left
> unchanged as an accurate record, not stale.

Ground truth: `remote-control-roadmap.md` (8 sections), `bridge/main.js` (current
bridge), `opencode_remote_android_app/` (Android client). This plan turns those 8
sections into deterministic, checkable, resumable work.

## How to use this plan

- Work top to bottom through Phases 0–8 (Phase 0 is setup; Phases 1–8 map 1:1 to
  roadmap sections 1–8, in the roadmap's own "Suggested sequencing" order).
- Every Subtask has an **Exit criterion**: a literal command to run and what to grep
  for or confirm in its output. Do not mark a subtask done on the basis of "it looks
  right" — run the command.
- After finishing (or abandoning) every single subtask, update `PROGRESS.md`. That
  file, not this one and not conversation history, is the resumable state. If you are
  picking this plan up fresh, read `PROGRESS.md` first, find the first row that is not
  `Done`, and resume there.
- Anything tagged `[VERIFY LIVE]` is an assumption about the `opencode serve` HTTP/SSE
  API taken from the roadmap doc's prose, not from a request/response pair captured in
  this repo's history. Before writing code that depends on it, run the verification
  step given for that tag and record the real response shape in `PROGRESS.md`'s Notes
  column. If the live probe contradicts the assumption, stop and re-plan that task
  instead of coding against the wrong shape.

## Global error-recovery / rollback rules (apply to every phase)

1. **Before starting a phase**: confirm the working tree is clean (`git status
   --porcelain` empty, or only expected untracked scratch dirs like `.opencode/`),
   then create a phase branch off the current branch:
   `git checkout -b phase-N-<slug>`. Do not start coding on `remote-control-groundwork`
   directly.
2. **Commit after every subtask that changes files**, not just at phase end. Small
   commits are the undo granularity. Commit message references the subtask id, e.g.
   `git commit -m "P1.2 bridge: relay SSE deltas as STREAM_TEXT_DELTA"`.
3. **If a subtask's exit criterion fails**: do not proceed to the next subtask. Either
   fix forward (re-run the exit criterion until it passes) or roll back that subtask's
   commit with `git reset --hard HEAD~1` (if uncommitted, `git checkout -- .` /
   `git clean -fd` for new files) and re-attempt. Never leave the tree in a state where
   the last commit fails its own exit criterion.
4. **If a whole phase must be abandoned** (e.g. a `[VERIFY LIVE]` assumption turns out
   false and the phase's scope needs to shrink): `git log` to find the last good commit
   on the phase branch, decide whether to `git reset --hard <sha>` and re-scope, or
   keep partial work and mark the remaining subtasks `Blocked` in `PROGRESS.md` with
   the reason. Never force-push over a phase branch another session might be reading.
5. **Merging a finished phase**: only after every subtask in it is `Done` and the
   phase-level verification (listed per phase below) passes on the phase branch, merge
   to `remote-control-groundwork` with `git checkout remote-control-groundwork && git
   merge --no-ff phase-N-<slug>`, then delete the phase branch.
6. **Regression guard**: before merging any phase, re-run the full baseline test
   suite (bridge + Android unit tests, §"Baseline verification commands" below) even
   if that phase touched only one side — the bridge and Android app share a wire
   contract, and a bridge-side rename can silently break Android DTOs (see
   `CONTEXT.md`'s "Wire contract discipline" section).

## Baseline verification commands (run before Phase 0 and after every phase merge)

```
cd bridge && npm test                       # node --test, see bridge/package.json
cd opencode_remote_android_app && ./gradlew :app:testDebugUnitTest
cd opencode_remote_android_app && ./gradlew :app:assembleDebug -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
```
Exit criterion for "baseline is green": all three commands exit 0. `npm test` output
must show `# fail 0` (from `node --test`'s summary); `testDebugUnitTest` and
`assembleDebug` must both print `BUILD SUCCESSFUL`.

---

## Phase 0 — Setup and live-API verification

Nothing in Phases 1–8 should be built on an unverified assumption about
`opencode serve`. Phase 0 collects every `[VERIFY LIVE]` probe from the whole plan
into one pass so later phases only need to read the result, not repeat the probe.

### Task 0.1 — Confirm baseline is green
- **0.1.1** Run the three baseline commands above on a clean `remote-control-groundwork`
  checkout.
  - Exit criterion: all three exit 0 as described above. If any fails, stop — fix the
    baseline before starting roadmap work; this is out of scope for the roadmap phases
    themselves but blocks them.

### Task 0.2 — Stand up a local `opencode serve` and capture its real API surface
- **0.2.1** With the bridge machine's `opencode` CLI on PATH, run
  `opencode serve --port 4097 --hostname 127.0.0.1 &` in the working directory used for
  testing, then `curl -s http://127.0.0.1:4097/doc | tee /tmp/opencode-openapi.json`
  (or the scratchpad dir).
  - Exit criterion: the command exits 0 and `/tmp/opencode-openapi.json` is non-empty,
    parseable JSON (`jq . /tmp/opencode-openapi.json >/dev/null` exits 0). This is the
    authoritative source for every `[VERIFY LIVE]` tag below — prefer it over guessing
    from endpoint names.
- **0.2.2** `[VERIFY LIVE]` Confirm `GET /event` and `GET /session/{id}/event` exist and
  stream SSE (`Content-Type: text/event-stream`).
  - Probe: create a session (`POST /session`), then
    `curl -N -s http://127.0.0.1:4097/event | head -c 500` while sending a prompt to
    that session from another shell (`curl -X POST
    http://127.0.0.1:4097/session/<id>/message -d '{"parts":[{"type":"text","text":"say hi"}]}'`).
  - Exit criterion: the `/event` curl prints at least one `data: {...}` line containing
    a `type` field before the prompt curl returns. Record the exact event `type`
    strings seen (e.g. `message.part.updated`) in `PROGRESS.md` Notes for Phase 1 —
    Task 1.1. If no event arrives within 15s, record that finding instead and treat §1
    as blocked pending upstream investigation, not silently unimplementable.
- **0.2.3** `[VERIFY LIVE]` Confirm `/session/{id}/permission/{permissionID}` (or
  `/permission/{requestID}/reply`) and `/question/{requestID}/reply` /
  `/question/{requestID}/reject` exist in the OpenAPI doc from 0.2.1.
  - Exit criterion: `jq '.paths | keys | .[] | select(test("permission|question"))'
    /tmp/opencode-openapi.json` prints at least one path for each of "permission" and
    "question". Record the exact paths and HTTP methods found in `PROGRESS.md` for
    Phase 2.
- **0.2.4** `[VERIFY LIVE]` Confirm `GET /session`, `POST /session/{id}/fork`,
  `POST /session` accepts `parentID`, `GET /session/{id}/children`.
  - Exit criterion: `jq '.paths | keys | .[] | select(test("session"))'
    /tmp/opencode-openapi.json` lists these paths (or their nearest real equivalents).
    Record findings for Phase 3.
- **0.2.5** `[VERIFY LIVE]` Confirm `/pty`, `/pty/{ptyID}`, `/pty/{ptyID}/connect`,
  `/pty/{ptyID}/connect-token`, `/pty/shells`.
  - Exit criterion: `jq '.paths | keys | .[] | select(test("pty"))'
    /tmp/opencode-openapi.json` lists these. Record findings (especially the
    `/connect` transport — WebSocket vs SSE vs raw TCP — read from the OpenAPI doc's
    description/tags) for Phase 4.
- **0.2.6** `[VERIFY LIVE]` Confirm whether the diff/patch API
  (`GET /session/{id}/diff`, used already in `bridge/main.js`'s `runPrompt`) supports
  any per-hunk or partial-apply operation.
  - Exit criterion: `jq '.paths | keys | .[] | select(test("diff|patch|hunk"))'
    /tmp/opencode-openapi.json`. If nothing beyond whole-file diff exists, record that
    explicitly — Phase 5's per-hunk subtask is scoped down accordingly, not silently
    dropped.
- **0.2.7** `[VERIFY LIVE]` Confirm the `/project` API mentioned in roadmap §6.
  - Exit criterion: `jq '.paths | keys | .[] | select(test("project"))'
    /tmp/opencode-openapi.json`. Record findings for Phase 6.
- **0.2.8** Kill the probe server (`kill %1` or the recorded PID) so it doesn't
  collide with the bridge's own `OC_SERVE_PORT` (4096) later.
  - Exit criterion: `curl -s -o /dev/null -w '%{http_code}'
    http://127.0.0.1:4097/config` returns something other than `200` (connection
    refused), confirming the probe process is down.

**Phase 0 exit criterion**: `PROGRESS.md` Phase 0 row is `Done`, and every
`[VERIFY LIVE]` item in Phases 1–8 below has a corresponding recorded finding in
`PROGRESS.md` Notes (endpoint exists with shape X, or endpoint does not exist / differs
— in which case the dependent task's scope is noted as reduced, not skipped silently).

---

## Phase 1 — Live streaming (roadmap §1)

Branch: `phase-1-streaming`. Depends on Phase 0 findings for 0.2.2.

### Task 1.1 — Bridge: SSE relay
- **1.1.1** Add an SSE client in `bridge/main.js` that opens
  `GET {ocBaseUrl}/event` once `ocServeProc` is ready (reuse `ocReadyPromise`), parses
  `data:` lines as JSON, and logs each parsed event type to stdout for now (no
  broadcast yet).
  - Exit criterion: start the bridge (`node bridge/main.js`), send a `PROMPT` frame
    from a test client (or `wscat`), and see `[sse] <type>` lines appear in the
    bridge's stdout during generation. Concretely:
    `node bridge/main.js & sleep 1 && node -e "<connect+CONNECT+PROMPT via ws>"` and
    `grep -q '\[sse\]' <captured bridge stdout>`.
- **1.1.2** Map the event types recorded in Phase 0 / 0.2.2 to new outbound frame
  types: `STREAM_TEXT_DELTA` (payload: `{sessionId, text}`), `STREAM_TOOL_CALL`
  (payload: `{sessionId, tool, input}`), `STREAM_TOOL_RESULT` (payload: `{sessionId,
  tool, output}`). Broadcast each via the existing `broadcast()` helper instead of
  just logging.
  - Exit criterion: a new bridge test `bridge/test/stream.test.js` that mocks the
    `/event` SSE endpoint (spin up a tiny local HTTP server emitting canned
    `data:` lines) and asserts `broadcast` is called with the three new eventTypes for
    the corresponding canned inputs. `cd bridge && npm test` exits 0 and output
    includes `stream.test.js` with no failing subtests.
- **1.1.3** Keep the final `CHAT_MESSAGE`/`FILE_DIFF` push in `runPrompt` unchanged
  (already true — do not remove it) so non-streaming clients keep working.
  - Exit criterion: `grep -n "pushChatMessage(text.trim()" bridge/main.js` still
    matches after 1.1.1–1.1.2 land — the final-push code path is untouched, only
    additive.

### Task 1.2 — Android: streaming state
- **1.2.1** `RemoteSessionManager` gains a `MutableStateFlow`/`SharedFlow` for
  in-progress deltas (e.g. `_streamingMessage: MutableStateFlow<StreamingMessageDto?>`)
  and routes `STREAM_TEXT_DELTA`/`STREAM_TOOL_CALL`/`STREAM_TOOL_RESULT` frames into it
  in `handleIncomingMessage`, mirroring the existing `when` branches' style.
  - Exit criterion: new/updated test in
    `opencode_remote_android_app/app/src/test/java/com/opencode/remote/session/SessionManagerTest.kt`
    that feeds a `STREAM_TEXT_DELTA` frame through `handleIncomingMessage` and asserts
    the new flow emits the expected value. `./gradlew :app:testDebugUnitTest
    --tests "*SessionManagerTest*"` reports `BUILD SUCCESSFUL`.
- **1.2.2** `ChatScreen` renders a streaming bubble sourced from the new flow instead
  of only fully-formed `chatMessages` entries, and shows which tool is running (from
  `STREAM_TOOL_CALL` payload) in place of the static "Thinking..." text.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds, and a manual
    check: run the bridge + app against a real `opencode serve`, send a prompt, and
    visually confirm the chat screen shows incremental text/tool-name updates before
    the final message lands (record as a note in `PROGRESS.md`, since this half is not
    machine-checkable — the automated exit criterion above is the merge gate; this
    manual check is confirmatory only).

**Phase 1 exit criterion**: baseline commands (bridge tests, Android unit tests,
Android assembleDebug) all green on `phase-1-streaming`, plus 1.1.1–1.2.2's individual
exit criteria all passed. Merge per global rollback rules.

---

## Phase 2 — Mid-task control: interrupt, steer, approve (roadmap §2)

Branch: `phase-2-control`. Depends on Phase 0 / 0.2.3 findings, and reuses Phase 1's
SSE relay (`ocReadyPromise`/event loop) if Phase 1 has already merged — if not, this
phase must add its own minimal SSE subscription following the same pattern as 1.1.1.

### Task 2.1 — Bridge: permission/question relay
- **2.1.1** Extend the SSE handler (from Phase 1, or a new minimal one if Phase 2
  precedes Phase 1 on this branch) to recognize `PermissionRequested`/`QuestionAsked`
  event types (exact names per Phase 0 findings) and broadcast `PERMISSION_REQUEST`
  `{permissionId, tool, input, sessionId}` / `QUESTION_REQUEST` `{questionId, text,
  sessionId}` frames.
  - Exit criterion: `bridge/test/permission.test.js` (new) mocks an SSE event of the
    recorded type and asserts the correct outbound frame is broadcast. `npm test`
    green.
- **2.1.2** Handle inbound `PERMISSION_REPLY` `{permissionId, decision}` and
  `QUESTION_REPLY` `{questionId, answer}` / a reject variant, calling the endpoints
  found in Phase 0 / 0.2.3 (`ocFetch` with the recorded path and method).
  - Exit criterion: `bridge/test/permission.test.js` extended to assert that a
    `PERMISSION_REPLY` inbound frame results in an `ocFetch` call (mock `global.fetch`)
    to the exact path/method recorded in `PROGRESS.md` for 0.2.3. `npm test` green.
- **2.1.3** Turn off (or make configurable) any existing blanket auto-approval only if
  the current bridge start-up passes `--auto` or similar to `opencode serve` — check
  first.
  - Exit criterion: `grep -n "'\-\-auto'\|auto-approve\|autoApprove" bridge/main.js` —
    if this exists, gate it behind an explicit flag defaulting to off after this task;
    if it does not exist in the current file (confirm via the grep), record "no
    existing auto-approve flag found; nothing to change" in `PROGRESS.md` and mark this
    subtask Done with that note — do not invent scope not present in the roadmap or
    code.

### Task 2.2 — Android: permission/question UI + abort confirmation
- **2.2.1** New DTOs `PermissionRequestDto`, `QuestionRequestDto` in `DtoModels.kt`
  following the existing `Bridge*`/UI-facing split pattern; `RemoteSessionManager`
  routes `PERMISSION_REQUEST`/`QUESTION_REQUEST` into a new
  `StateFlow<PermissionRequestDto?>` / `StateFlow<QuestionRequestDto?>`, and exposes
  `replyPermission(id, decision)` / `replyQuestion(id, answer)` sending
  `PERMISSION_REPLY`/`QUESTION_REPLY`.
  - Exit criterion: `SessionManagerTest` gains cases feeding
    `PERMISSION_REQUEST`/`QUESTION_REQUEST` frames and asserting the flows update, plus
    a case asserting `replyPermission` sends the right frame. `./gradlew
    :app:testDebugUnitTest --tests "*SessionManagerTest*"` green.
- **2.2.2** A modal/inline card in the chat UI subscribes to the new flows and renders
  "Agent wants to run `X` — allow once / always / deny", non-blocking to the rest of
  the UI (i.e. not a full-screen blocking dialog that prevents scrolling other tabs).
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; manual check
    against a live bridge that the card appears and a tap sends a reply frame
    (confirm via bridge stdout `< PERMISSION_REPLY` log line). Record manual result in
    `PROGRESS.md`.
- **2.2.3** Confirm `KILL_TASK`'s abort surfaces as a distinguishable "cancelled"
  chat message, not silent nothing.
  - Exit criterion: check `bridge/main.js`'s `KILL_TASK` case — it currently deletes
    the task and pushes `TASK_REMOVED` only, no chat message. Add a
    `pushChatMessage('Cancelled', false, 'Cancelled', false)` call (or equivalent) in
    that branch. `grep -n "case 'KILL_TASK'" -A 12 bridge/main.js` shows a
    `pushChatMessage` call inside the branch after this change, and a new/updated
    bridge test asserts it. `npm test` green.

### Task 2.3 — Stretch: mid-task steering
- **2.3.1** `[VERIFY LIVE]` Determine whether `POST /session/{id}/message` can be
  called again while a prior call for the same session is still in flight (queuing vs
  rejection vs corruption).
  - Exit criterion: manual probe against a running `opencode serve` — fire two
    `POST /session/{id}/message` calls back-to-back without awaiting the first, record
    the observed behavior (HTTP status of the second call, whether both eventually
    complete) in `PROGRESS.md`. If queuing works, implement bridge support for a
    second `PROMPT` while `tasks[taskId]` is still `Running`; if not, mark this
    subtask `Won't do (unsupported upstream)` in `PROGRESS.md` — do not fake queuing
    client-side.

**Phase 2 exit criterion**: baseline commands green on `phase-2-control`; all
non-stretch subtask exit criteria passed; 2.3.1 resolved either as implemented-and-
tested or explicitly recorded as unsupported.

---

## Phase 3 — Session visibility and management (roadmap §3)

Branch: `phase-3-sessions`. Depends on Phase 0 / 0.2.4 findings.

### Task 3.1 — Bridge: session listing and switching
- **3.1.1** Add `LIST_SESSIONS` handler calling `GET /session` and broadcasting/
  replying with the list (id, title, timestamps per the real response shape from
  0.2.4).
  - Exit criterion: `bridge/test/sessions.test.js` (new) mocks `GET /session` and
    asserts a `LIST_SESSIONS` inbound frame triggers an outbound frame with the mocked
    list. `npm test` green.
- **3.1.2** Add `SWITCH_SESSION {sessionId}` handler that sets `ocSessionId` to the
  given id (after confirming it exists via the list, or a `GET /session/{id}` call)
  instead of always using the lazily-created one from `ensureSession()`.
  - Exit criterion: test asserting `SWITCH_SESSION` changes the module-level
    `ocSessionId` used by the next `runPrompt` call (exported/testable via the
    existing test harness pattern in `bridge/test/helpers.js`). `npm test` green.
- **3.1.3** Add `NEW_SESSION {title?}` handler calling `POST /session` with an
  optional title and switching to it.
  - Exit criterion: test asserting `NEW_SESSION` results in a `POST /session` mock
    call and an outbound `SESSION_OPENED`-style frame (or reuse `WORKSPACE_OPENED`-
    style naming convention — pick and document one). `npm test` green.

### Task 3.2 — Bridge: persist current session id across restarts
- **3.2.1** Write `ocSessionId` to a small JSON file (e.g.
  `path.join(activeWorkspace, '.opencode-remote-session.json')` or a bridge-local
  state dir — pick one and document it in a code comment) whenever it changes, and
  read it back on `startOpenCodeServer()` instead of always starting `null`.
  - Exit criterion: unit test that starts the bridge module functions with a temp
    dir, sets a session id, simulates a restart (re-require or re-init the relevant
    function), and asserts the persisted id is read back — following the existing
    test style in `bridge/test/`. `npm test` green. Additionally, a manual check:
    `node bridge/main.js`, send a `PROMPT`, note the printed/logged session id, kill
    and restart the bridge, confirm the same session id is reused (not a fresh one) —
    record in `PROGRESS.md`.

### Task 3.3 — Android: session picker
- **3.3.1** New DTOs (`SessionDto` etc.) and `RemoteSessionManager` methods
  `listSessions()`, `switchSession(id)`, `newSession(title?)` following the existing
  `sendRaw`/`sendString` + `StateFlow` pattern; a `StateFlow<List<SessionDto>>`
  exposed for the UI.
  - Exit criterion: `SessionManagerTest` cases for the new frames/flows pass;
    `./gradlew :app:testDebugUnitTest --tests "*SessionManagerTest*"` green.
- **3.3.2** A session picker screen (new route under `NavRoutes`, per the existing
  "single source of truth" convention documented in `CONTEXT.md`) replacing or
  extending the current single-workspace Workspaces screen, listing sessions with
  resume/new actions.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; `grep -n
    "Tab\." opencode_remote_android_app/app/src/main/java/com/opencode/remote/ui/navigation/NavRoutes.kt`
    shows the new route registered in the single source of truth, not a parallel list.

### Task 3.4 — Stretch: session forking
- **3.4.1** Bridge `FORK_SESSION {sessionId}` calling `POST /session/{id}/fork` (per
  0.2.4 findings); Android action to trigger it.
  - Exit criterion: bridge test mocking the fork endpoint call; if 0.2.4 found no
    fork endpoint, mark `Won't do (unsupported upstream)` in `PROGRESS.md` instead.

**Phase 3 exit criterion**: baseline green on `phase-3-sessions`; 3.1–3.3 subtasks all
passed; 3.4 resolved (implemented or explicitly deferred).

---

## Phase 4 — Real terminal via PTY (roadmap §4)

Branch: `phase-4-terminal`. Depends on Phase 0 / 0.2.5 findings. This is the most
likely phase to need re-scoping if the `/pty` transport isn't a simple HTTP+SSE
pattern — read the 0.2.5 findings before starting.

### Task 4.1 — Bridge: PTY-backed terminal
- **4.1.1** Replace the `TERMINAL` case's `spawn(sh, ['-c', command])` with a call to
  `POST /pty` (or the exact creation endpoint from 0.2.5) to create a PTY, then
  connect per the documented transport (WebSocket proxy, SSE, or polling — whichever
  0.2.5 found) and stream chunks as `TERMINAL_OUTPUT` frames incrementally instead of
  buffering until `close`.
  - Exit criterion: `bridge/test/terminal.test.js` (existing file — extend it) has a
    new test mocking the `/pty` creation + connect endpoints and asserting
    `TERMINAL_OUTPUT` frames are broadcast incrementally (i.e. `broadcast` called more
    than once per command, not once at the end) for a canned multi-chunk response.
    `npm test` green, and specifically `node --test bridge/test/terminal.test.js`
    shows the new test passing.
- **4.1.2** Implement real `TERMINAL_RESIZE {ptyId, cols, rows}` calling whatever
  resize mechanism the PTY API exposes (check 0.2.5's OpenAPI description for a
  resize field/endpoint).
  - Exit criterion: extended `terminal.test.js` case asserting `TERMINAL_RESIZE`
    triggers the corresponding upstream call. `npm test` green. If 0.2.5 found no
    resize capability, record that and leave `TERMINAL_RESIZE` as a documented no-op
    with a comment explaining why (unsupported upstream), rather than silently doing
    nothing without explanation (current state, per roadmap, already a no-op but
    undocumented as such in a comment).

### Task 4.2 — Android: streaming terminal
- **4.2.1** `RemoteSessionManager`/`TerminalScreen` already consumes
  `terminalOutput: SharedFlow<String>` — verify no DTO changes are needed for
  incremental output (bare string frames, same shape) and that the UI appends rather
  than replaces on each emission.
  - Exit criterion: `grep -n "terminalOutput" opencode_remote_android_app/app/src/main/java/com/opencode/remote -r`
    confirms a single append-only consumer; if the current `TerminalScreen`
    implementation clears/replaces instead of appending per emission, fix it.
    `./gradlew :app:testDebugUnitTest` and `assembleDebug` both green.
- **4.2.2** Wire `TERMINAL_RESIZE` to fire on actual terminal view size changes
  (rather than being unused client-side).
  - Exit criterion: `grep -n "TERMINAL_RESIZE"
    opencode_remote_android_app/app/src/main/java/com/opencode/remote -r` shows a real
    call site tied to a layout/size-change callback, not just a dead helper function.
    `assembleDebug` green.

**Phase 4 exit criterion**: baseline green on `phase-4-terminal`; 4.1–4.2 subtasks
passed or explicitly reduced in scope per 0.2.5 findings, recorded in `PROGRESS.md`.

---

## Phase 5 — Fine-grained diff review (roadmap §5)

Branch: `phase-5-diff`. Depends on Phase 0 / 0.2.6 findings and, for the live-preview
piece, on Phase 1 having landed.

### Task 5.1 — Android: multiple pending diffs
- **5.1.1** Change `RemoteSessionManager._pendingDiff: MutableStateFlow<FileDiffDto?>`
  to `_pendingDiffs: MutableStateFlow<List<FileDiffDto>>`, appending/replacing by
  `filePath` on each `FILE_DIFF` frame instead of overwriting the single value; update
  `acceptDiff`/`rejectDiff` to remove by `filePath` from the list.
  - Exit criterion: `SessionManagerTest` updated/added cases: two `FILE_DIFF` frames
    for different files both appear in `pendingDiffs.value`; `acceptDiff` on one
    removes only that entry. `./gradlew :app:testDebugUnitTest --tests
    "*SessionManagerTest*"` green.
- **5.1.2** Update whatever screen currently renders `pendingDiff` (single value) to
  render the list — every changed file from the bridge's `GET /session/{id}/diff`
  fetch, not just the last one received.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; `grep -rn
    "pendingDiff\b" opencode_remote_android_app/app/src/main` shows no remaining
    single-value usages (all migrated to the list, or intentionally kept as a
    derived "first/selected diff" accessor — document which).

### Task 5.2 — Per-hunk accept/reject (conditional on 0.2.6)
- **5.2.1** If 0.2.6 found a partial-apply capability: bridge implements real
  `ACCEPT_HUNK`/`REJECT_HUNK` handling (replacing the current
  "not supported... ignored" branch) calling the discovered endpoint; Android sends
  per-hunk actions from a hunk-level UI.
  - Exit criterion: bridge test replacing the current implicit "no-op" assumption
    with a real assertion that `ACCEPT_HUNK`/`REJECT_HUNK` call the discovered
    endpoint. `npm test` green.
  - If 0.2.6 found no partial-apply capability: **do not implement**. Update the
    comment at `bridge/main.js`'s `ACCEPT_HUNK`/`REJECT_HUNK` case to cite the Phase 0
    finding (e.g. "confirmed via /doc probe on <date>: no partial-apply endpoint
    exists") instead of the current speculative comment, and mark this subtask
    `Won't do (unsupported upstream)` in `PROGRESS.md`. This matches the roadmap's own
    caveat that this "may require an upstream capability that doesn't exist yet."

### Task 5.3 — Live diff preview (depends on Phase 1)
- **5.3.1** If Phase 1's `STREAM_TOOL_RESULT` events carry file-edit payloads
  (confirm from Phase 0/1 findings), push incremental `FILE_DIFF`-shaped updates
  during generation, not only after `runPrompt` finishes.
  - Exit criterion: bridge test asserting a `STREAM_TOOL_RESULT` event for a
    file-edit tool triggers a `pushFileDiff` call before the final diff fetch runs.
    `npm test` green. Android: `pendingDiffs` list updates visibly before the final
    `CHAT_MESSAGE` arrives — manual check against a live bridge, recorded in
    `PROGRESS.md` (not machine-checkable end-to-end without a live model).

**Phase 5 exit criterion**: baseline green on `phase-5-diff`; 5.1 fully done; 5.2 and
5.3 resolved either as implemented+tested or explicitly deferred with cited reason.

---

## Phase 6 — Multi-workspace / multi-project (roadmap §6)

Branch: `phase-6-multiworkspace`. Depends on Phase 0 / 0.2.7 findings.

### Task 6.1 — Bridge: real multi-workspace tracking
- **6.1.1** Replace the single `activeWorkspace` string with a tracked list of
  workspace roots (e.g. `let workspaces = [{id, name, path}]`), keeping
  `activeWorkspace` as a derived "currently selected" pointer so existing code
  (`resolveInsideWorkspace`, `pushFileTree`, etc.) keeps working unchanged.
  - Exit criterion: `bridge/test/paths.test.js` (existing) still passes unmodified
    (proves `resolveInsideWorkspace`'s contract is preserved), plus a new test
    asserting `FETCH_WORKSPACES` now returns more than one entry when more than one
    workspace has been added via a new `ADD_WORKSPACE` frame. `npm test` green.
- **6.1.2** Decide, per 0.2.7 findings, between "one `opencode serve` + session per
  workspace" vs "scope sessions by workspace within one server via `/project`", and
  implement whichever the live API actually supports. Document the decision in a code
  comment at the top of the relevant section of `bridge/main.js`.
  - Exit criterion: `PROGRESS.md` Notes for this task states the decision and cites
    the 0.2.7 finding it's based on; the chosen implementation's test (new, mocking
    the relevant `/project` or multi-serve behavior) passes. `npm test` green.

### Task 6.2 — Android: meaningful workspace list
- **6.2.1** `WorkspaceListScreen` renders the real multi-item `workspaces` StateFlow
  (already exists in `RemoteSessionManager`) instead of assuming a single-item list;
  add an "add workspace" action sending the new `ADD_WORKSPACE` frame from 6.1.1.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; a
    `SessionManagerTest` case confirms `workspaces.value` reflects a multi-item
    `WORKSPACE_LIST` frame (the DTO/flow already exist — this is a UI-consumption
    check plus any new outbound frame test). `./gradlew :app:testDebugUnitTest` green.

**Phase 6 exit criterion**: baseline green on `phase-6-multiworkspace`; 6.1–6.2
subtasks passed with the 0.2.7-grounded design decision recorded.

---

## Phase 7 — Reachability, notifications, backgrounding (roadmap §7)

Branch: `phase-7-reachability`. No `opencode serve` API dependency — this phase is
Android + bridge networking/infra only, no `[VERIFY LIVE]` tags.

### Task 7.1 — Push notifications
- **7.1.1** Add FCM (or chosen equivalent) dependency and a minimal client-side
  token-registration flow; bridge gains an outbound notification hook fired on task
  completion, `PERMISSION_REQUEST`, and error (reusing the existing `broadcast`/
  `pushChatMessage`/`pushTaskUpdated`/error call sites as trigger points — do not
  duplicate logic, hook into existing functions).
  - Exit criterion: `grep -n "pushChatMessage\|pushTaskUpdated\|PERMISSION_REQUEST"
    bridge/main.js` shows the notification hook call adjacent to each trigger point
    (error path, task-removed-on-completion path, permission-request broadcast path);
    a bridge test confirms the hook function is invoked for at least the error path
    (mock the hook, assert call count). `npm test` green. Full FCM delivery is
    integration-only and cannot be unit-tested here — record manual delivery
    confirmation (a real notification observed on a real device) in `PROGRESS.md`;
    do not mark this Done on unit tests alone.
- **7.1.2** Android: handle the incoming push (foreground vs background) and surface
  it as a system notification tapping into the relevant screen.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; manual check
    on a device/emulator that backgrounding the app and triggering a completion still
    shows a system notification — recorded in `PROGRESS.md`.

### Task 7.2 — Foreground service / reconnect-on-resume
- **7.2.1** Add a foreground service (or `onResume` reconnect trigger reusing
  `WsClient.connect`'s existing backoff loop) so a backgrounded app reconnects instead
  of silently losing the socket.
  - Exit criterion: a `WsClient`-level unit test (new) confirms `connect()` is
    idempotent/safe to call again after a disconnect state without leaking a second
    session loop (`connectionState` ends up `Connected` once, not duplicated sends).
    `./gradlew :app:testDebugUnitTest` green. Manual check: background the app for
    >1 min, foreground it, confirm chat/git state refreshes — recorded in
    `PROGRESS.md`.

### Task 7.3 — Remote reachability beyond LAN
- **7.3.1** Document and/or implement a relay/tunnel option (Tailscale, Cloudflare
  Tunnel, or a small relay server) — this is explicitly the largest, most open-ended
  item in the roadmap. Scope the first increment as documentation + a manual
  Tailscale/Cloudflare Tunnel setup guide in `bridge/README.md` (or wherever the
  bridge's docs live), not a code change, unless the user has specified a concrete
  relay implementation to build.
  - Exit criterion: a new doc section exists (`grep -n "Tailscale\|Cloudflare Tunnel"
    bridge/README.md` or equivalent matches) with concrete steps a user followed to
    reach the bridge from off-LAN — do not claim "done" without an actual documented,
    testable procedure. If the user later asks for a built-in relay server, that is
    new scope requiring its own plan, not silently absorbed into this subtask.

**Phase 7 exit criterion**: baseline green on `phase-7-reachability`; 7.1–7.2 passed
with manual confirmations recorded; 7.3 has at minimum the documented LAN-escape
procedure in place.

---

## Phase 8 — Security hardening (roadmap §8)

Branch: `phase-8-security`. Depends on Phase 2 having landed (per roadmap: "Once §2
lands, actually turn off blanket auto-approval").

### Task 8.1 — Per-device pairing/token issuance
- **8.1.1** Replace the single `AUTH_TOKEN` shared-by-anyone-with-the-QR model with
  per-device tokens: on `CONNECT`, if no token or an unrecognized token is supplied,
  issue a new per-device token (persisted to a small local store, e.g. JSON file
  keyed by device name/id) instead of always checking against the one static
  `AUTH_TOKEN`.
  - Exit criterion: new `bridge/test/auth.test.js` cases (extend existing file)
    covering: (a) a fresh device gets issued a distinct token, (b) two devices with
    different tokens can both authenticate, (c) a revoked token (see 8.2) is rejected.
    `npm test` green, and specifically `node --test bridge/test/auth.test.js` shows
    all three passing.

### Task 8.2 — Token revocation from the app
- **8.2.1** Bridge: `REVOKE_TOKEN {deviceId}` frame (or an admin-only path) removing
  a device's token from the store and closing any of its open sockets.
  - Exit criterion: `auth.test.js` case: issue token, authenticate a mock connection,
    send `REVOKE_TOKEN`, assert the connection is closed (`ws.close` called) and a
    subsequent connect attempt with the same token fails auth. `npm test` green.
- **8.2.2** Android: a settings/devices screen listing paired devices with a revoke
  action sending `REVOKE_TOKEN`.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; `grep -n
    "REVOKE_TOKEN" opencode_remote_android_app/app/src/main -r` shows a real UI call
    site.

### Task 8.3 — Turn off blanket auto-approval
- **8.3.1** With Phase 2's permission-relay in place, confirm no code path still
  auto-approves tool calls server-side without going through the
  `PERMISSION_REQUEST`/`PERMISSION_REPLY` flow (re-check the 2.1.3 finding — if it
  found no existing auto-approve flag, this subtask is "confirm still true" not "add
  new code").
  - Exit criterion: `grep -n "auto\|approve" bridge/main.js` reviewed manually; either
    no blanket-approval code path exists (record as confirmed in `PROGRESS.md`), or
    any found path is now gated behind the Phase 2 permission flow with a passing
    test proving a denied `PERMISSION_REPLY` actually blocks the tool call
    (`bridge/test/permission.test.js` extended). `npm test` green.

### Task 8.4 — Audit log
- **8.4.1** Bridge: append-only log (e.g. JSON-lines file) of every approved
  command/edit — permission decisions from Phase 2, git commands executed, terminal
  commands executed — with timestamp and deciding device.
  - Exit criterion: `bridge/test/audit.test.js` (new): triggering a `GIT` command and
    a `PERMISSION_REPLY` each append one well-formed JSON line to the log file (parse
    each line with `JSON.parse`, confirm expected fields). `npm test` green.
- **8.4.2** Android: a screen reading the audit log (new `FETCH_AUDIT_LOG` frame
  returning recent entries) and rendering it.
  - Exit criterion: `./gradlew :app:assembleDebug
    -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64` succeeds; a
    `SessionManagerTest` case confirms `FETCH_AUDIT_LOG`'s response is routed into a
    new StateFlow. `./gradlew :app:testDebugUnitTest` green.

**Phase 8 exit criterion**: baseline green on `phase-8-security`; 8.1–8.4 subtasks
passed with their individual test suites green.

---

## Out of scope

Nothing beyond the 8 roadmap sections' listed "Work" items (including their
"Stretch" sub-items, which are included above as conditional tasks). Do not add
features, refactors, or infrastructure not traceable to a specific roadmap bullet.
