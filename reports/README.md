# Reports Index — Review Session 2026-08-25 (HEAD 1a0f7f3)

Four-track review of the OpenCode Remote Client. Every Critical/High finding was re-verified against source before publication.

| Track | Report | Verdict in one line |
|---|---|---|
| 01 | `01-code-review/CODE_REVIEW.md` | NOT production-ready — app cannot connect on modern Android; fabricated features in client; build broken as committed |
| 02 | `02-spec/SPEC_CONFORMANCE.md` | Majority of sprint/phase checkboxes are PARTIAL or FALSE end-to-end; scaffolding was marked done without server halves |
| 03 | `03-docs/DOCS_AUDIT.md` | Docs describe an integration that does not exist — protocol contract matches server, not the shipped client |
| 04 | `04-audit/SECURITY_AUDIT.md` | CRITICAL risk — bridge is unauthenticated RCE for any LAN peer (`cmd /c` injection, arbitrary file reads) |

## Cross-cutting P0s (agree across tracks)

1. **Protocol unification** — pick the JSON envelope, migrate bridge/main.js to it (fixes H1/D1/D3 and enables S1 auth gating in the same change).
2. **Restore `gradle/libs.versions.toml`** — nothing can compile/verify until the version catalog exists.
3. **Network security config** — cleartext currently blocked by default on API 28+; the app cannot connect at all today.
4. **Delete all fabrication** — mock AI reply, fake QR timer, static Activity/Logs screens (roadmap §9 violations).

## Provenance note

Findings originated from four parallel subagent review sessions whose transcripts survived tooling interruptions; this session's lead then independently verified each Critical/High claim (file:line) before authoring these reports. Raw agent reasoning dumps are preserved at `/tmp/opencode/agent_dumps/` (ephemeral).
