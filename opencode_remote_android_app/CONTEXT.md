# Domain glossary

- **Bridge** — the Node.js WebSocket server (`bridge/main.js` in the parent repo, treated as
  frozen infrastructure) that runs on a dev machine and exposes a local `opencode` CLI,
  filesystem, and git to this app over one `/ws` connection. It is the single source of
  truth for the wire protocol — DTOs and eventType strings in this app are written to match
  it, not the other way around.
- **Workspace** — a directory on the bridge host the agent operates in. The bridge tracks
  one active workspace at a time.
- **Pairing token** — one random hex token per bridge process; sent in the `CONNECT` frame
  to authenticate. Scanned from the bridge's printed QR code (`ip=<ip>;key=<token>`, see
  `data/pairing/QrParser.kt`) or entered manually.
- **Frame** — `{eventType, payload}`, the sole WebSocket message envelope
  (`data/dto/WebSocketFrame.kt`). No request/response correlation id.
- **AgentState** — free-text status from the wrapped `opencode` CLI ("Idle",
  "Thinking..."), no fixed vocabulary. Matched case-insensitively in `AgentStateBadge`.
- **Diff** — one outstanding patch from the CLI's file-edit tool calls, filed under a
  fixed bridge-side name. Represented as `FileDiffDto(filePath, patch)`.
- **Task** — a running OS process on the bridge host (currently only the `opencode run`
  invocation itself), `{id, name, port, status}`.
- **GitStatus** — `{branch, modifiedFiles, addedFiles, deletedFiles, canPush, canPull}`,
  computed from `git status --porcelain -b` on the bridge.

# Wire contract discipline

Several bridge payloads are bare strings, not JSON objects (`OPEN_WORKSPACE`,
`FETCH_FILE`, `PROMPT`, `ACCEPT_DIFF`/`REJECT_DIFF`, `TERMINAL`, `KILL_TASK`, `GIT`
outbound; `AGENT_STATE`, `FILE_CONTENT`, `TERMINAL_OUTPUT`, `TASK_REMOVED` inbound).
Where the wire shape differs from what the UI wants, a `Bridge*` DTO in
`data/dto/DtoModels.kt` models the wire shape exactly and a small mapper (`toUi()`, or
inline mapping in `RemoteSessionManager.handleIncomingMessage`) converts it — this is
the seam that stops a bridge-side field rename from silently breaking Compose code.
See `RemoteSessionManager`'s class doc for the authoritative list.

# Module shape

- `data/network/WsClient.kt` — connection lifecycle only (connect/reconnect/backoff/send).
  No domain knowledge.
- `data/network/RemoteSessionManager.kt` — domain state (one `StateFlow` per concept) +
  inbound frame routing + outbound commands. Drives `WsClient` with a session callback.
- `data/dto/DtoModels.kt` — wire DTOs (`Bridge*` where the shape differs from the UI) and
  their UI-facing counterparts/mappers.
- `data/pairing/QrParser.kt` — pure parsing logic, no Compose/CameraX dependency, unit
  tested in isolation.
- `ui/navigation/NavRoutes.kt` — every route in the app, including the 7 tabs nested
  under `Main`. Single source of truth; `MainDashboardScreen`'s tab bar is generated
  from `NavRoutes.Tab` rather than a parallel hand-aligned list.
