# Documentation Audit — OpenCode Remote Client

- **Date:** 2026-08-25 · **HEAD:** 1a0f7f3
- **Scope:** README.md, roadmap.md, tasks.md, docs/integration-context.md, docs/README.md, docs/Rough_idea.md, opencode-remote/README.md, .understand-anything/domain-analysis.md, metadata.json, example-environment file

## Executive summary

Docs are well-written and internally tidy after the reconciliation commit, but they describe **an integration that does not exist**: the protocol contract matches the bridge server while the shipped Android client speaks an incompatible dialect, and a second, unrelated backend is presented as if it were the companion server. Accuracy of small factual claims (theme hexes, ports) is good; accuracy of integration claims is poor.

## Findings table

| # | Severity | Doc | Issue | Evidence | Fix |
|---|---|---|---|---|---|
| D1 | Critical | docs/integration-context.md | Contract documents raw text commands (`PROMPT:<msg>` etc.) — matches bridge/main.js but NOT the client, which emits JSON envelopes `{"eventType","payload"}` | WebSocketFrame.kt:7-9 + RSM.kt:207-213 vs main.js:218-320 | Rewrite contract to the JSON envelope schema; version it |
| D2 | Critical | tasks.md Phase 1 | "Transition to type-safe JSON protocol" marked [x] — only client half done; canonical doc still specifies raw strings; end-to-end claim false | see D1 matrix below | Re-mark as in-progress (done in cleanup for Task 3.3 only so far) |
| D3 | High | integration-context vs client inbound direction | Server pushes `{type:'AgentState',data:…}`; client requires `{eventType,…}` → every frame fails deserialization silently (RSM.kt:94-98 logs and drops) | main.js:77,81,86,90 vs RSM.kt:120-181 | Same fix as D1 |
| D4 | High | README.md:69 | Claims "Type-Safe Routing keys" — navigation uses plain string routes | MainActivity composable("auth")… | Correct the claim or migrate to type-safe nav |
| D5 | High | opencode-remote/README.md | "Mobile App Integration" shows /qr·/pair·3000 calls that NO Android code performs (zero refs to :3000, /qr, /pair in Kotlin) | grep across app/src/main | Label that project as legacy/alternate experiment, not the app's backend |
| D6 | High | (gap, all docs) | NsdHelper expects `_opencode._tcp` advertisement; neither server registers any NSD service → discovery can never work, yet nothing documents this prerequisite | NsdHelper.kt:15 vs absence in both servers | Add NSD advertisement to bridge + document |
| D7 | High | (gap) | bridge TERMINAL handler runs `cmd /c ${command}` — Windows-only shell, nowhere documented; repo carries cmd.exe artifact consistent with Windows origin | main.js:298 | Document platform constraint or make POSIX-safe |
| D8 | Medium | roadmap §5/Rough_idea | Security story drift: vision promises TLS/JWT/key exchange; reality is cleartext ws:// with unauthenticated frames | Rough_idea security section vs main.js:209 | State current security posture honestly; keep vision section clearly aspirational |
| D9 | Medium | ports story | 8080 (bridge, argv>env>default) vs 3000 (express default) vs hardcoded :8080 in client (no config surface) | main.js:8, server.js PORT, RSM.kt:67-68 | One table of "who listens where" in integration-context |
| D10 | Medium | settings.gradle.kts vs branding | `rootProject.name = "My Application"` vs product "OpenCode" everywhere | settings.gradle.kts:25 | Rename |
| D11 | Low | README theme section | Verified accurate: all six hex values match Color.kt exactly | Color.kt:6-15 ✓ | none |
| D12 | Low | metadata.json | Generic template metadata ("My Application" era), adds no value | root file | Refresh or drop |

## Protocol triple-drift matrix

| Operation | Doc contract (integration-context.md) | bridge/main.js (server) | Android client (RemoteSessionManager) |
|---|---|---|---|
| Connect | `CONNECT:<device>` | ✔ raw string, replies `{type:'Connected'}` (+Math.random token, never enforced) | ✗ never sends CONNECT at all |
| Workspaces | `FETCH_WORKSPACES` | ✔ raw string | JSON envelope `{"eventType":"FETCH_WORKSPACES"}` ✗ |
| Open workspace | `OPEN_WORKSPACE:<path>` | ✔ raw string | envelope ✗ |
| File tree / file content | `FETCH_FILE_TREE` / `FETCH_FILE:<path>` | ✔ raw strings | envelopes ✗ |
| Prompt | `PROMPT:<text>` | ✔ raw string | envelope ✗ (+ local mock reply) |
| Accept/reject diff | `ACCEPT_DIFF:`/`REJECT_DIFF:` | ✔ raw strings | envelopes ✗ |
| Hunk accept/reject | *(absent from doc)* | ✗ no handler | envelope ACCEPT_HUNK/REJECT_HUNK ✗ |
| Terminal | `TERMINAL:<cmd>` | ✔ raw string via `cmd /c` | envelope ✗ |
| Resize | *(absent)* | ✗ no handler | envelope TERMINAL_RESIZE ✗ |
| Kill task | `KILL_TASK:<id>` | ✔ raw string | envelope ✗ |
| Git | `GIT:<args>` | ✔ raw string via exec | envelope ✗ |
| Inbound event shape | "structured JSON payloads" (unspecified) | `{type,data,error?}` | requires `{eventType,payload?}` ✗ |

**Net:** zero operations currently succeed end-to-end. The document tells a server implementer to build something incompatible with the shipped client.

## Completeness gaps (what a new developer cannot learn)

1. Which backend is canonical (bridge ws:8080 vs opencode-remote REST:3000) — undocumented.
2. How to run the system end-to-end (start bridge, pair, connect) — no runbook anywhere.
3. Build prerequisites — including that `gradle/libs.versions.toml` is missing entirely (build cannot configure).
4. Platform constraints — bridge is Windows-shell-bound (D7); original dev box was Windows (local.properties SDK path).
5. Security posture — what is actually authenticated today (answer: nothing).

## Prioritized fixes

- **P0:** rewrite integration-context.md around the JSON envelope + `{type,data}` inbound schema; add the drift matrix above; mark tasks.md Phase-1 honestly.
- **P1:** D4-D7 corrections/additions; run-the-system runbook; NSD advertisement note.
- **P2:** D8-D10, D12 polish.
