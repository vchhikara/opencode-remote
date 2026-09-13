# 0002: Global cross-workspace session search

## Status
Accepted (locked design, not yet implemented — see `docs/plans/global-session-search-tasks.md`)

## Context

The bridge scopes session listing (`LIST_SESSIONS`) to whatever `opencode serve`
process is currently running, which is bound to `activeWorkspace`. Confirmed
during a brainstorm with the user (2026-09-14) that this is a genuine gap, not
a UI omission:

- Sessions belonging to a workspace other than the currently-active one never
  appear in the Android app's session list.
- Sessions created directly via the OpenCode CLI (not through the bridge) are
  equally invisible — the bridge only ever queries the one `opencode serve`
  instance it's currently running.
- The Android UI *can* type/browse an arbitrary workspace path already, so the
  missing piece is specifically: "let me see and jump into a session without
  first knowing/typing which workspace it lives in."

Investigated OpenCode's actual on-disk storage as evidence before designing
anything: `~/.local/share/opencode/opencode.db` is a SQLite database (WAL
journal mode, confirmed via `PRAGMA journal_mode`), with a `session` table
(`project_id` FK, `title`, `time_updated`, ...) joined to a `project` table
(`worktree` = absolute filesystem path). A live query against it already
returns a clean, global, cross-workspace session index spanning multiple
distinct project directories, sorted by recency — proving the data this
feature needs already exists in one place, independent of which (if any)
`opencode serve` process is currently running.

## Decision

The bridge reads `opencode.db` directly, read-only, to build the global
session list — it does **not** spin up or query multiple `opencode serve`
instances. A `serve` process is started (or restarted) bound to a workspace
only when the user actually selects a session to open, reusing the exact
`OPEN_WORKSPACE` + `SWITCH_SESSION` machinery that already exists.

### Data access

```js
const { DatabaseSync } = require('node:sqlite'); // Node 22 built-in, confirmed working — no new npm dependency
const db = new DatabaseSync(
  path.join(os.homedir(), '.local/share/opencode/opencode.db'),
  { readOnly: true }
);
```

```sql
SELECT s.id, s.title, p.worktree, s.time_updated
FROM session s JOIN project p ON s.project_id = p.id
ORDER BY s.time_updated DESC
LIMIT ? OFFSET ?
```

WAL mode means this read-only connection never blocks on, or is blocked by,
`opencode serve`'s own writer connection. One `DatabaseSync` instance opened
once at bridge startup, reused per request.

### New wire frames

**`FETCH_ALL_SESSIONS`** (request payload: `{ limit?: number, cursor?: string }`)
→ **`ALL_SESSIONS_LIST`**
```jsonc
{
  "sessions": [
    { "id": "...", "title": "...", "worktree": "/abs/path", "updatedAt": 173..., "reachable": true }
  ],
  "nextCursor": "..." // present only if more rows exist
}
```
`reachable` = `fs.existsSync(worktree) && fs.statSync(worktree).isDirectory()`,
computed at query time. Unreachable sessions are still returned (not
filtered out) — the client is responsible for showing them disabled, not the
server for hiding them.

**`OPEN_SESSION_GLOBAL`** (request payload: `{ id: string, worktree: string }`)
→ **`SESSION_OPENED`** (`{ id, worktree }`) or **`ERROR`**

Handling, reusing existing logic rather than inventing new:
1. Reject if `worktree` resolves outside `WORKSPACE_ROOT` — identical boundary
   check to `OPEN_WORKSPACE`.
2. Reject if `worktree` doesn't exist / isn't a directory.
3. `activeWorkspace = worktree`; register in `workspaces[]` if not already
   present (same as `OPEN_WORKSPACE`).
4. Restart `opencode serve` bound to the new cwd if one is running (same as
   `OPEN_WORKSPACE`).
5. `ocSessionId = id; persistSessionId()` (same as `SWITCH_SESSION`).
6. Reply `SESSION_OPENED`, then broadcast `WORKSPACE_LIST` (same as
   `OPEN_WORKSPACE`).

No new session-opening concept is introduced — this is `OPEN_WORKSPACE` and
`SWITCH_SESSION` chained together under one frame.

### Android client

- `RemoteSessionManager` gets `_allSessions: MutableStateFlow<List<GlobalSessionDto>>`
  and `fetchAllSessions()` / `openGlobalSession(id, worktree)` methods,
  decoding the new frames with an explicit shape (per the Files-bug lesson —
  no bare-list decoding of a payload that isn't actually a bare list).
- A new screen (or a tab on the existing Sessions screen — left open, see
  Phase 3 below) lists sessions sorted by `updatedAt`, each row showing title
  plus the worktree's basename as a subtitle. Unreachable sessions render
  visibly disabled with their path shown, never silently dropped.
- Tapping a reachable row sends `OPEN_SESSION_GLOBAL`; on `SESSION_OPENED`,
  navigate into the existing chat/session view exactly as any other session
  switch does today.

### Explicit edge-case handling

| Case | Handling |
|---|---|
| `worktree` deleted/moved since last use | `reachable:false`; row shown disabled, not hidden |
| `worktree` outside `WORKSPACE_ROOT` | `ERROR`, same boundary as `OPEN_WORKSPACE` |
| Session belongs to the already-active workspace | still listed; tapping it is a cheap no-op switch |
| `opencode.db` missing / locked / corrupt | `FETCH_ALL_SESSIONS` → `ERROR` with message; bridge does not crash |
| Large session count | `limit`/`cursor` pagination, default limit TBD in Phase 1 (proposed: 50) |

## Consequences

- No new runtime dependency — `node:sqlite` is a Node 22 built-in
  (`ExperimentalWarning` only, confirmed functional against this project's
  Node version).
- Couples the bridge to OpenCode's on-disk schema (`session`/`project`
  tables, `opencode.db` path). A future OpenCode storage-format change would
  break this read path; there is no API-based fallback today, and none is
  in scope for this design.
- Deliberately reuses `OPEN_WORKSPACE`/`SWITCH_SESSION` semantics rather than
  adding a parallel "remote session open" code path, keeping the
  single-`opencode serve`-process model (see the existing note in
  `bridge/main.js` around `activeWorkspace`) intact.
- Out of scope for this design (separate, still-unscheduled brainstorm
  items): daemonizing the bridge process itself, and any redesign of the
  workspace-picker UX beyond what already exists.

## Alternatives considered

- **Query multiple `opencode serve` instances, one per known workspace.**
  Rejected — expensive (a `serve` process per workspace just to list
  sessions), and doesn't solve the "CLI-created session in an unknown
  workspace" case, since the bridge would still need to already know about
  every workspace to spin up a server for it.
- **Add a project-scoped API endpoint request to opencode itself.** Rejected
  as out of scope — this ADR is about what the bridge can do today against
  the currently-installed OpenCode version, not a change to OpenCode.
