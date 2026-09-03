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
| 0 | 0.1.1 Baseline green check | Not started | | | |
| 0 | 0.2.1 Capture live OpenAPI doc | Not started | | | |
| 0 | 0.2.2 [VERIFY LIVE] /event SSE shape | Not started | | | |
| 0 | 0.2.3 [VERIFY LIVE] permission/question endpoints | Not started | | | |
| 0 | 0.2.4 [VERIFY LIVE] session list/fork/children endpoints | Not started | | | |
| 0 | 0.2.5 [VERIFY LIVE] /pty endpoints | Not started | | | |
| 0 | 0.2.6 [VERIFY LIVE] per-hunk/partial-apply diff capability | Not started | | | |
| 0 | 0.2.7 [VERIFY LIVE] /project API | Not started | | | |
| 0 | 0.2.8 Kill probe server | Not started | | | |
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

- `/event`, `/session/{id}/event` shape: _pending_
- `/session/{id}/permission/{id}`, `/permission/{id}/reply`, `/question/{id}/reply`,
  `/question/{id}/reject`: _pending_
- `GET /session`, `POST /session/{id}/fork`, `POST /session` with `parentID`,
  `/session/{id}/children`: _pending_
- `/pty`, `/pty/{id}`, `/pty/{id}/connect`, `/pty/{id}/connect-token`, `/pty/shells`:
  _pending_
- Per-hunk / partial-apply diff capability: _pending_
- `/project` API: _pending_
