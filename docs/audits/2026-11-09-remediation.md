# OpenCode Remote: 9 November 2026 audit remediation

## Scope and baseline

- Audited and fetched `main`: `bdb0dbc0a5b90fd6fc743084391c5da363bb62af`.
- Isolated branch: `fix/audit-remediation-2026-11`.
- Implementation commits: `1b4191d` (bridge security), `9f253c7` (CI and signing), `e7b15d5` (Android transport and diff UI).
- Working directory: `/home/vipul/My Projects/opencode-remote-remediation-2026-11`.
- The user's original `main` worktree contains a pre-existing change to `bridge/main.js` and an untracked `.opencode-remote-session.json`. Neither was modified or reset.
- Machine: HPpavilionpro, Ubuntu Linux; Node v24.20.0 via nvm, OpenJDK 17.0.20.1, Android SDK 36.1 and Gradle 9.6.
- No ADB emulator or device connected, and no network-accessible production bridge was tested. No release publication or merge to `main`.

## Verification evidence

| Command / check | Recorded outcome |
|---|---|
| `npm ci --ignore-scripts --no-audit --no-fund`, `npm test` under Node 24.20.0 | **PASS: 55 tests, 0 failures**, log `/tmp/opencode-remediation-bridge-correlation.log` |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain --no-daemon` | **PASS**, local JDK 17 and `ANDROID_HOME=$HOME/Android/Sdk`; log `/tmp/opencode-remediation-gradle-session-filter.log` |
| Android XML test result files | **101 tests, 0 failures, 0 errors, 0 skipped** in 11 test suites |
| `./gradlew :app:bundleRelease :app:processReleaseMainManifest --console=plain --no-daemon` | **PASS**, log `/tmp/opencode-remediation-release.log` |
| `jarsigner -verify -verbose app-release.aab` | **jar is unsigned**; no silent debug-key signing |
| `node --check bridge/main.js`, `git diff --check` | **PASS** |
| ADB device and emulator QA | **UNAVAILABLE**: `adb devices -l` returned no connected devices |
| Hosted GitHub Actions (run 42654877531, commit `db27c0b`) | **PASS: 4/4 jobs** (bridge, Android debug tests/build, Android release/compliance, OWASP scan). Additional correlation commit needs its own CI verification |
| TLS proxy / cellular handoff / multiple physical devices | **NOT RUN** |

Built outputs remain on HPpavilionpro under `android-application/app/build/outputs/apk/debug/app-debug.apk` and `android-application/app/build/outputs/bundle/release/app-release.aab`. The release AAB is **unsigned** and is not a distributable production build.

## Finding status matrix

**Status definitions:** FIXED + VERIFIED = implemented and relevant local check passed; FIXED + UNVERIFIED = changed but necessary check unavailable; PARTIALLY FIXED = one or more acceptance criteria outstanding; NOT FIXED = no patch; BLOCKED = requires external approval or prerequisite. A local check does not imply hosted CI, mobile connectivity or release readiness.

| Finding | Status | Evidence and residual work |
|---|---|---|
| SEC-01 inherited-property authentication | **FIXED + VERIFIED** | `bridge/main.js`, `bridge/test/securityRemediation.test.js`; own-key verifier lookup with validated records, negative tests for inherited and malformed token values, valid pairing/reconnect/revocation |
| SEC-02 reusable pairing secret | **FIXED + VERIFIED** | Single-use default, 120-second startup window, configurable explicit multi-device allowance; tests for spent, expired and valid pairing; a deliberately fixed `BRIDGE_TOKEN` can be reused on process restart and must be rotated by operator |
| SEC-03 bearer-token storage | **PARTIALLY FIXED** | SHA-256 verifier keys, safe in-place migration of legacy plaintext-key store, atomic writes and mode 0600; audit file mode restricted. Hardware/local credential compromise, token rotation and broader log redaction remain |
| SEC-04 symlink-based path escape | **FIXED + VERIFIED** | Canonical root/file/workspace resolution in `bridge/main.js`; synthetic symlink and traversal regression tests pass. As documented, no containment claim against a legitimate authenticated shell operator or adversarial local symlink race |
| SEC-05 WS/WSS transport | **PARTIALLY FIXED** | Explicit scheme parsed and stored, Ktor WSS URL choice, OkHttp-engine heartbeat configured; Android builds and QR JVM tests pass. Trustworthy WSS endpoint, actual certificate and off-LAN tests not run; global cleartext exception remains |
| QA-01 false Revert action | **FIXED + VERIFIED** | Android action now says 'Dismiss review'; run log states bytes are not reverted; regression test confirms dismissal leaves files untouched. A genuine conflict-aware undo remains out of scope |
| CI-01 Node runtime mismatch | **FIXED + VERIFIED** | CI Node 24 and package engines/lockfile updated; local 55/55 bridge tests pass. GitHub Actions run 42654877531 bridge job succeeded before the subsequent correlation patch |
| CI-02 Android CI directory | **FIXED + VERIFIED** | Workflow `working-directory: android-application`; local Gradle reached, compiled and tested successfully. Hosted CI run 42654877531 passed |
| CI-03 machine-specific JDK path | **FIXED + VERIFIED** | Removed tracked `org.gradle.java.home`; local Gradle ran using JDK 17 |
| REL-01 release debug signing | **FIXED + VERIFIED** | Removed explicit debug signing override from release. Generated release AAB is unsigned according to `jarsigner`; proper controlled upload-key signing still required before release |
| NET-01 WebSocket safeguards | **PARTIALLY FIXED** | Restrict `/ws`, 16 MiB inbound message limit, 64 connections, 10-second unauthenticated timeout, ping/pong cleanup, bounded broadcast-buffer handling and malformed-frame tests. Per-IP rate control, comprehensive saturation/backpressure tests and streaming for large direct replies remain |
| STATE-01 global mutable workspace/session | **PARTIALLY FIXED** | Android ignores foreign session-tagged stream/tool/permission/question events when the selected session is known, regression-tested. Shared bridge workspace, prompt, PTY and session state remains; needs ownership and deterministic cross-device tests |
| STATE-02 reconnect recovery | **PARTIALLY FIXED** | Corrected OkHttp ping setting. No authoritative replay of missed permissions/questions/tasks; Android reconnect/device verification unavailable |
| STATE-03 response correlation | **PARTIALLY FIXED** | Correlated FETCH_FILE requestId/path/content and operation-tagged file/global-session errors with backward-compatible legacy frames. Android ignores stale file results and unrelated errors; regression-tested. Other operations still need comprehensive IDs and out-of-order tests |
| UX-01 background alerts | **NOT FIXED** | `NOTIFY` still operates over a live WebSocket; disconnected or killed-app delivery not guaranteed |
| UX-02 pairing QR metadata | **FIXED + VERIFIED** | QR now includes actual port and scheme; Kotlin parser supports nondefault port and legacy payloads, with JVM tests. Physical QR scanning untested |
| PERF-01 synchronous startup settings | **NOT FIXED** | `MainActivity` still uses `runBlocking`; no startup profile available to validate a safe asynchronous replacement |
| OPS-01 branch protection | **BLOCKED** | Requires owner approval to change GitHub branch/merge policies. No protection change or merge was performed |
| MAINT-01 documentation consistency | **PARTIALLY FIXED** | Updated Node requirement, pairing/token protection, transport, security caveats, bridge and API descriptions. Further full documentation and ADR review remains |

## Residual priorities and exact continuation

1. Confirm hosted CI for every subsequent remediation commit (initial run 42654877531 passed all four jobs). Investigate hosted-only failures from logs, not inference.
2. Complete STATE-01/STATE-03: extend tested file request/error correlation to remaining operations, add per-session ownership and serialize workspace transitions. Build deterministic overlapping-client tests.
3. Implement STATE-02 authoritative state refresh after reconnect, including pending permissions, questions, tasks and terminal state. Test disconnections against a disposable bridge.
4. Add per-IP auth rate limiting, connection-pressure integration tests, graceful handling of credential-store errors and bounded large-response pagination.
5. Validate WSS behind a controlled TLS proxy, certificate trust and reconnect on authorized Android hardware. Check Android API 24/33/36 and QR scan/lifecycle.
6. Improve notification claims, non-blocking cold-start settings, CameraX resource cleanup, accessibility and instrumentation. Measure before performance claims.
7. Produce a dedicated release signing workflow with owner-provided secret-managed upload credentials. No release/signing-key changes or Play publication without separate authorization.
8. Request owner approval for branch protection after hosted CI passes. Do **not** auto-merge.

## Workflow compliance

Read and applied installed Android Engineering Suite audit, security/privacy, build repair, testing, CI/release and Test Android Apps emulator QA protocols. Context7 and official Ktor docs were checked for OkHttp heartbeat behavior and WebSocket URL selection. Executed on HPpavilionpro through Remote Desktop Commander; no device QA was falsely represented as complete.

## Rollback / safety

All work is isolated in `fix/audit-remediation-2026-11` and can be reviewed independently. Original `main` and pre-existing local modifications are retained. Do not revert SEC-01 to address connection issues. If a token store is inconsistent, restore a verified backup or perform deliberate re-pairing; do not weaken the verifier lookup. The branch has not been merged or published as an Android release.
