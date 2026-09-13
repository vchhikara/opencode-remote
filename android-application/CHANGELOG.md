# Android App — Version History

Semantic versioning (`MAJOR.MINOR.PATCH`), applied retroactively to this
app's development history. `versionName`/`versionCode` in
`app/build.gradle.kts` track the entries below — `versionCode` increments by
1 per released version, `versionName` is the semver string.

No pre-existing versioning scheme or git tags existed in this repo before
this file; the mapping below is a retroactive allocation onto real commits,
not a scheme that was tracked live from the start. Nothing is tagged in git
yet — this file is the source of truth. (Git tags can be added later if
needed: `git tag v0.1.0 ccfd999`, etc.)

| Version | versionCode | Date       | Commit(s)                          | Summary |
|---------|--------------|------------|-------------------------------------|---------|
| 0.1.0   | 1            | 2026-09-03 | `ccfd999`, `21a04a1`                | First functional app: Phase 0–8 roadmap complete (streaming, mid-task control, session management, real PTY terminal, diff review, multi-workspace, notifications/backgrounding, per-device-token security hardening) + docs |
| 0.2.0   | 2            | 2026-09-08 | `6044582`, `256f2df`, `1a463c4`     | Repo reorganization, self-contained public docs, real launcher icon |
| 0.3.0   | 3            | 2026-09-11 | `87e28fa`                           | UI redesign merged: Home shell, run log, terminal transcript |
| 0.4.0   | 4            | 2026-09-11 | `970772d`                           | Offline-first settings repository (Jetpack DataStore, manual DI, replaces `AppearanceStore`) |
| 0.5.0   | 5            | 2026-09-11 | `d6047d5`                           | Settings content: `NOTIFY`-frame notifications, emergency kill switch, true-black OLED theme, diagnostic trace toggle |
| 0.6.0   | 6            | 2026-09-11 | `eaa726f`                           | CI: Play Store compliance audit, native `.so` stripping check, OWASP dependency CVE scan |
| 0.7.0   | 7            | 2026-09-11 | (current, uncommitted at time of writing) | Release-readiness process checklist pass (Phase 4); this version-tracking scheme itself |
| 0.7.1   | 8            | 2026-09-13 | `91f1dba`                           | Bugfix: Files screen never populated — `FILE_TREE` decoded as `List` instead of a single root node, silently swallowed by `decodePayload`'s catch-all. Fixed decode shape, added workspace-switch cache clear, removed dead `pushFileTree()` |
| 0.8.0   | 9            | 2026-09-14 | `4760ac4`, `888fc27`                | Global cross-workspace session search (ADR-0002): bridge reads OpenCode's own `opencode.db` directly (read-only, `node:sqlite`) for a session list spanning every workspace, not just the active one, including sessions created via the OpenCode CLI directly; new `FETCH_ALL_SESSIONS`/`OPEN_SESSION_GLOBAL` frames; new "All workspaces" screen |

## Scheme notes

- **Format:** standard semver. `MAJOR` stays `0` until a Play Store
  submission is made (pre-1.0 = prototype/internal testing, matching this
  app's actual status). `MINOR` bumps per feature/milestone batch. `PATCH`
  reserved for bug fixes with no new capability.
- **`versionCode`:** plain incrementing integer, one per version row above —
  required by Android/Play regardless of `versionName` format.
- Earlier ad hoc build labels used during manual device testing (e.g.
  `"prototype v2.1"`, installed on `RZGL322202Z` for a one-off verification
  build) are **not** part of this scheme — they predate it and are superseded
  by the semver `versionName` going forward.
