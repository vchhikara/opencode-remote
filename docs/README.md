# Documentation Index

| File | Purpose |
|---|---|
| `../roadmap.md` | Architecture principles (binding) + 7-sprint plan |
| `../tasks.md` | 6-phase integration checklist (source of truth for task status) |
| `integration-context.md` | Client↔server protocol contract (WebSocket commands + payloads). Canonical copy; `handofft.md` and root `opencode_integration_context.md` were duplicates and were removed. |
| `Rough_idea.md` | Original vision document |
| `loveable_design/` | Design references |
| `archive/list.md` | Superseded feature list (absorbed into `tasks.md`) |
| `archive/CODE_REVIEW_REPORT.md` | Prior review round; superseded by fresh reports under `reports/` |

## Status reconciliation notes (this cleanup)

- Sprint-1 items marked done are evidence-backed: Ktor/DataStore/session-manager verified in code.
- Hilt was intentionally removed — DI strategy recorded as resolved-by-decision.
- Task 3.3 (EncryptedDataStore) reverted to open: implementation stores API key in plain-text DataStore.
