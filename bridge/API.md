# Bridge WebSocket API

Protocol reference for `bridge/main.js`. The bridge exposes one WebSocket endpoint (`ws://<host>:<port>/ws`) using a flat JSON frame on both directions:

```json
{ "eventType": "SOME_TYPE", "payload": { ... } }
```

`bridge/main.js`'s `switch` statement (inbound) and its `broadcast()`/`ws.send()` call sites (outbound) are the authoritative source of truth — this document is generated from reading that switch statement plus every `eventType:` string emitted in the file, cross-checked against the Kotlin DTOs in `opencode_remote_android_app/app/src/main/java/com/opencode/remote/data/dto/DtoModels.kt`. Where the two disagreed, the bridge won; no such case exists as of this pass.

Until `CONNECT` succeeds, the only frame the bridge accepts is `CONNECT` itself — every other event type gets the socket closed (code `4001`).

## Inbound events (client → bridge)

### `CONNECT`
Authenticates the socket. Must be the first frame sent.

```json
{ "eventType": "CONNECT", "payload": { "deviceName": "string", "token": "string" } }
```

- `token` is either a previously-issued per-device token, or (first time) the pairing secret (`BRIDGE_TOKEN`).
- On success: replies `CONNECTED`, then `WORKSPACE_LIST`, then pushes `GIT_STATUS`.
- On failure (unrecognized token): closes the socket with code `4001`.

### `FETCH_WORKSPACES`
No payload. Replies with `WORKSPACE_LIST`.

### `OPEN_WORKSPACE`
Switches the active workspace and restarts `opencode serve` bound to it if already running.

```json
{ "eventType": "OPEN_WORKSPACE", "payload": "/absolute/path" }
```
`payload` is a bare string path, must resolve inside `WORKSPACE_ROOT`. Replies `WORKSPACE_OPENED` or `ERROR`; broadcasts `WORKSPACE_LIST` to all clients on success.

### `ADD_WORKSPACE`
Registers a directory in the workspace list without switching to it.

```json
{ "eventType": "ADD_WORKSPACE", "payload": "/absolute/path" }
```
Same path-confinement rule as `OPEN_WORKSPACE`. Replies with `ERROR` on failure; broadcasts `WORKSPACE_LIST` on success.

### `FETCH_FILE_TREE`
No payload. Replies `FILE_TREE` with the active workspace's file tree, or `ERROR`.

### `FETCH_FILE`
```json
{ "eventType": "FETCH_FILE", "payload": "relative/or/absolute/path" }
```
Path is resolved and confined inside the active workspace (`resolveInsideWorkspace`). Replies `FILE_CONTENT` (bare string) or `ERROR`.

### `PROMPT`
Sends a prompt to the persistent `opencode serve` session.

```json
{ "eventType": "PROMPT", "payload": "prompt text" }
```
`payload` is a bare string. No direct reply frame — starts/reuses the session, streams `STREAM_TEXT_DELTA`/`STREAM_TOOL_CALL`/`STREAM_TOOL_RESULT` via the `/event` SSE relay, then pushes `CHAT_MESSAGE`, `FILE_DIFF` (per changed file), `TASK_UPDATED`/`TASK_REMOVED`, and `NOTIFY`.

### `ACCEPT_DIFF` / `REJECT_DIFF`
```json
{ "eventType": "ACCEPT_DIFF", "payload": "fileName" }
```
`payload` is a bare filename string. Both just clear the bridge's local `pendingDiffs` bookkeeping for that file — neither calls back into opencode serve (there is no upstream per-hunk/partial-apply endpoint; see `ACCEPT_HUNK`/`REJECT_HUNK` below). No reply frame.

### `LIST_SESSIONS`
No payload. Replies `SESSION_LIST` (array) or `ERROR`.

### `SWITCH_SESSION`
```json
{ "eventType": "SWITCH_SESSION", "payload": "sessionId" }
```
Bare string session id. Replies `SESSION_SWITCHED` or `ERROR`.

### `NEW_SESSION`
```json
{ "eventType": "NEW_SESSION", "payload": { "title": "optional string" } }
```
Replies `SESSION_SWITCHED` or `ERROR`.

### `FORK_SESSION`
```json
{ "eventType": "FORK_SESSION", "payload": "sourceSessionId" }
```
Bare string session id to fork from. Replies `SESSION_SWITCHED` (with the new forked session's id) or `ERROR`.

### `FETCH_ALL_SESSIONS`
```json
{ "eventType": "FETCH_ALL_SESSIONS", "payload": { "limit": 50, "cursor": "opaque string, optional" } }
```
Global, cross-workspace session list — read directly from OpenCode's own on-disk session database (`~/.local/share/opencode/opencode.db`), independent of `activeWorkspace` and of whether `opencode serve` is even running. `payload` is optional; both fields default (`limit` to 50, capped at 200). Replies `ALL_SESSIONS_LIST` or `ERROR` (e.g. the database is missing/unreadable). See `adr/0002-global-cross-workspace-session-search.md`.

### `OPEN_SESSION_GLOBAL`
```json
{ "eventType": "OPEN_SESSION_GLOBAL", "payload": { "id": "sessionId", "worktree": "/absolute/path" } }
```
Opens a session found via `FETCH_ALL_SESSIONS`, which may belong to a workspace other than the currently-active one. Same path-confinement rule as `OPEN_WORKSPACE` (rejects a `worktree` outside `WORKSPACE_ROOT` or that doesn't exist). On success: switches `activeWorkspace` to `worktree`, registers it in the workspace list if new, restarts `opencode serve` bound there, and switches to the given session id — replies `SESSION_OPENED` and broadcasts `WORKSPACE_LIST`. Replies `ERROR` if the worktree is invalid or the session id isn't found once bound to it.

### `KILL_TASK`
```json
{ "eventType": "KILL_TASK", "payload": "taskId" }
```
Bare string task id. Aborts the in-flight prompt generation server-side, removes the task, pushes `TASK_REMOVED` and a `CHAT_MESSAGE` ("Cancelled"). No direct reply.

### `LIST_DEVICES`
No payload. Replies `DEVICE_LIST` (never includes raw tokens).

### `REVOKE_TOKEN`
```json
{ "eventType": "REVOKE_TOKEN", "payload": "deviceId" }
```
Bare string device id (not the token itself). Deletes that device's token, force-closes any of its open sockets (code `4001`). Replies `TOKEN_REVOKED` or `ERROR` ("Unknown device").

### `FETCH_AUDIT_LOG`
No payload. Replies `AUDIT_LOG` with up to the last 200 entries.

### `PERMISSION_REPLY`
```json
{ "eventType": "PERMISSION_REPLY", "payload": { "permissionId": "string", "decision": "string" } }
```
Relayed to opencode serve (`POST /permission/{id}/reply`) and appended to the audit log. No reply frame.

### `QUESTION_REPLY`
```json
{ "eventType": "QUESTION_REPLY", "payload": { "questionId": "string", "answer": "string" } }
```
Relayed to opencode serve (`POST /question/{id}/reply`) and audit-logged. No reply frame.

### `QUESTION_REJECT`
```json
{ "eventType": "QUESTION_REJECT", "payload": { "questionId": "string" } }
```
Relayed to opencode serve (`POST /question/{id}/reject`). No reply frame. (Not currently in the documented Kotlin DTO set — bridge-only.)

### `ACCEPT_HUNK` / `REJECT_HUNK`
Accepted but **not implemented** — opencode serve has no per-hunk/partial-apply endpoint upstream (only whole-file diff endpoints exist). Logged and silently ignored.

### `TERMINAL`
```json
{ "eventType": "TERMINAL", "payload": "shell command" }
```
Bare string command. Spawns a PTY via opencode serve, streams output as `TERMINAL_OUTPUT`. Audit-logged. No direct reply frame.

### `TERMINAL_RESIZE`
```json
{ "eventType": "TERMINAL_RESIZE", "payload": { "cols": 80, "rows": 24, "ptyId": "optional string" } }
```
If `ptyId` is omitted, resizes the most recently started PTY. No reply frame.

### `GIT`
```json
{ "eventType": "GIT", "payload": "status --porcelain" }
```
Bare string of the git subcommand + args (parsed with quote-aware tokenization). Only these subcommands are allowed: `status, add, commit, push, pull, fetch, checkout, branch, stash, cherry-pick, diff, log, rev-parse` — anything else is rejected with a `TERMINAL_OUTPUT` error line. Audit-logged. Output streamed via `TERMINAL_OUTPUT`; refreshes and broadcasts `GIT_STATUS` on completion. No direct reply frame.

### Unknown event types
Any `eventType` not in the switch above (post-authentication) gets an `ERROR` reply: `{ "message": "Unknown eventType: <type>" }`.

## Outbound events (bridge → client)

### `CONNECTED`
```json
{ "sessionId": "token", "deviceId": "uuid", "deviceName": "string", "issuedToken": "token (only on first pairing)" }
```
Sent once per successful `CONNECT`.

### `WORKSPACE_LIST`
Array of `{ "id": "path", "name": "basename", "path": "path" }`. Sent after connect, `FETCH_WORKSPACES`, and broadcast on `OPEN_WORKSPACE`/`ADD_WORKSPACE`.

### `WORKSPACE_OPENED`
```json
{ "path": "resolved path" }
```

### `FILE_TREE`
```json
{ "name": "string", "path": "string", "isDirectory": true, "children": [ ... ] }
```
Recursive tree of the active workspace (dotfiles and `node_modules` excluded).

### `FILE_CONTENT`
Bare string — the requested file's raw content.

### `CHAT_MESSAGE`
```json
{ "id": "string", "text": "string", "isUser": false, "actionDescription": "string", "hasDetails": false }
```
Pushed for assistant responses and errors during a `PROMPT`. The bridge never echoes the user's own message back — the client appends that locally.

### `STREAM_TEXT_DELTA`
```json
{ "sessionId": "string|null", "text": "string" }
```
Incremental assistant text, relayed from opencode serve's `/event` SSE feed (`message.part.delta`).

### `STREAM_TOOL_CALL`
```json
{ "sessionId": "string|null", "tool": "string", "input": { } }
```
Emitted when a tool part's state is `pending` or `running`.

### `STREAM_TOOL_RESULT`
```json
{ "sessionId": "string|null", "tool": "string", "output": "string or error object" }
```
Emitted when a tool part's state is `completed` or `error`.

### `PERMISSION_REQUEST`
```json
{ "permissionId": "string", "sessionId": "string|null", "tool": "string|null", "input": { } }
```
Relayed from opencode serve's `permission.asked`/`permission.v2.asked` SSE events. Also triggers a `NOTIFY` frame.

### `QUESTION_REQUEST`
```json
{ "questionId": "string", "sessionId": "string|null", "text": "string|null", "options": ["string"] | null }
```
Relayed from `question.asked`/`question.v2.asked` SSE events.

### `TERMINAL_OUTPUT`
Bare string — one line/chunk of PTY or git command output (also used for `$ git ...` echo lines and error lines).

### `TASK_UPDATED`
```json
{ "id": "string", "name": "string", "port": "number|null", "status": "string" }
```

### `TASK_REMOVED`
Bare string — the removed task's id.

### `GIT_STATUS`
```json
{
  "branch": "string",
  "modifiedFiles": ["string"],
  "addedFiles": ["string"],
  "deletedFiles": ["string"],
  "canPush": false,
  "canPull": false
}
```

### `FILE_DIFF`
```json
{ "fileName": "string", "diffText": "string (unified diff)" }
```
Pushed live as each tool call's edit completes, and again with the session's full per-file diff set after a prompt finishes.

### `DEVICE_LIST`
Array of `{ "deviceId": "string", "deviceName": "string", "issuedAt": "number (ms epoch)" }`. Never includes raw tokens.

### `TOKEN_REVOKED`
```json
{ "deviceId": "string" }
```

### `AUDIT_LOG`
Array of entries: `{ "timestamp": "ISO string", "kind": "string", "detail": { }, "deviceId": "string|null", "deviceName": "string|null" }`. `kind` is one of `permission_decision`, `question_reply`, `terminal_command`, `git_command`; `detail`'s shape varies by `kind`.

### `SESSION_LIST`
Array of `{ "id": "string", "title": "string|null", "updatedAt": "number|null" }`.

### `SESSION_SWITCHED`
```json
{ "id": "string" }
```
Sent for `NEW_SESSION`, `SWITCH_SESSION`, and `FORK_SESSION`.

### `ALL_SESSIONS_LIST`
```json
{
  "sessions": [
    { "id": "string", "title": "string", "worktree": "/absolute/path", "updatedAt": "number", "reachable": "boolean" }
  ],
  "nextCursor": "opaque string, present only if more rows exist"
}
```
Reply to `FETCH_ALL_SESSIONS`, sorted most-recently-updated first. `reachable` reflects whether `worktree` currently exists on disk as a directory — an unreachable session is still included, never silently dropped.

### `SESSION_OPENED`
```json
{ "id": "string", "worktree": "/absolute/path" }
```
Reply to `OPEN_SESSION_GLOBAL`.

### `NOTIFY`
```json
{ "kind": "string", "detail": { } }
```
General-purpose notification hook — currently a broadcast-only stand-in for real push delivery (no FCM credentials configured); fired on `permission_request`, prompt errors, and task completion.

### `ERROR`
```json
{ "message": "string" }
```
Sent for malformed JSON frames, unauthorized access, unknown event types, and per-handler failures (e.g. `OPEN_WORKSPACE` outside `WORKSPACE_ROOT`, `LIST_SESSIONS` failure).

### Not observed as bridge-pushed frames
`AGENT_STATE` (`Thinking...`/`Idle`) and `FileTree` (a separate legacy tree-push helper, distinct from `FETCH_FILE_TREE`'s `FILE_TREE` reply) exist as internal broadcast helpers in `main.js` but are not part of the documented client-facing DTO set in `DtoModels.kt` — included here for completeness since they do go out over the wire.
