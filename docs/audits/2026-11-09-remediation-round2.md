# OpenCode Remote audit remediation: second pass (9 November 2026)

## Scope and provenance

This follow-up is an additive continuation of `docs/audits/2026-11-09-remediation.md`. The prior implementation and QA PRs, #1 and #2, were subsequently merged to `main` by the repository owner. Any statements in the first report about those PRs remaining unmerged are historical and no longer current.

All second-pass changes were authored directly to `fix/audit-remaining-2026-11-09` and proposed through [draft PR #3](https://github.com/vchhikara/opencode-remote/pull/3). No changes were made to the user's modified local main worktree or physical POCO device. Desktop Commander access to HPpavilionpro dropped during this pass, so no new ADB, emulator, local Gradle, stress or physical connectivity verification is claimed.

A successful **four-job GitHub Actions checkpoint** was observed on commit `cbce59b469e390a9201fff803a5846e6e12a88ec`, run `42666969197`, covering bridge tests, Android build/JVM tests, Android release build/compliance and OWASP dependency check. **This does not validate later commits.** Check the draft PR's most recent CI result before merging.

## Implementation and remaining work

| Audit item | Status | Changes and explicit limits |
|---|---|---|
| SEC-03 credentials | **PARTIAL** | Authenticated `ROTATE_TOKEN` replaces the current device's verifier atomically; Android accepts/persists the issued replacement; persistent git arguments, terminal commands and question-answer content no longer appear in plain audit logs. Hardware-backed storage guarantees and rotation-device failure recovery remain unverified. |
| SEC-05 WS/WSS | **NOT COMPLETE** | Earlier WSS URI support retained with system trust. Dynamic LAN-IP connections still require a global cleartext network-security exception. No trusted WSS endpoint/certificate failure test, cellular handoff or external network validation was possible. |
| NET-01 WebSocket hardening | **PARTIAL** | IP-based invalid-auth throttling, bounded outbound file and file-tree response size, file-tree depth/node caps and regression cases added. Network saturation, slow-consumer and multi-IP pressure tests remain pending. |
| STATE-01 ownership / isolation | **PARTIAL** | Single-device controller lease rejects cross-device mutations and disallows prompt/context switches during an in-flight prompt; client may explicitly `RELEASE_CONTROL`. Bridge state and terminal remain globally scoped, and same-device operation ordering/races are not fully serialized. |
| STATE-02 reconnect recovery | **PARTIAL** | New `FETCH_RUNTIME_STATE`/`RUNTIME_STATE` handshake sync replaces stale Android tasks, diffs and pending questions/permissions; matching new bridge-side caches are maintained from SSE events. Pending server state is not durable over bridge restarts, terminal output and stream history are not replayed, and real disconnect QA was not run. |
| STATE-03 request correlation | **PARTIAL** | Correlated `FETCH_FILE` retained, added request IDs for file-tree and global-session searches with stale response filtering in Android. Remaining operations, overlapping mutations and structured error coverage still need correlation. |
| MAINT-01 documentation | **PARTIAL** | This second-pass change log documents new contract and limitations. A full reconciliation of all README, API, architectural decisions and tests remains. |
| UX-01 disconnected/background alerts | **NOT FIXED** | NOTIFY is still carried over the live WebSocket. Guaranteed delivery when the app is killed or the connection is down requires a configured push provider, device registration and permissions; none were supplied. |
| PERF-01 blocking cold start | **CODE FIX, CHECKPOINT VERIFIED** | Removed `runBlocking` DataStore read from MainActivity; UI now renders with default settings until DataStore emits. Cold-start benchmarking remains pending; theme may briefly show default. |
| OPS-01 branch protection | **NOT CONFIGURED** | GitHub connector exposed read APIs but no branch/ruleset protection mutation. Owner must configure required checks, reviews and guarded merging on main. |

## Follow-up QA / delivery

- **Android API 24/33/36 physical QA:** not run in this pass. Historical isolated API36 smoke test remains documented in the first report.
- **Real WSS, TLS and Wi-Fi/cellular handoff:** not run.
- **Concurrent-client testing:** new synthetic regression covers controller conflict/release, subject to newest CI; physical two-client validation not run.
- **Stress/slow-consumer testing:** not run.
- **CameraX lifecycle:** scanner resource ownership moved into `DisposableEffect` with analyzer clear, selective unbind, ML Kit scanner close and executor shutdown. Compilation and camera lifecycle/device QA still require latest CI and ADB.
- **Accessibility:** no new verified TalkBack, focus, touch-target or accessibility-semantic audit.
- **Release signing:** new **manual-only** `.github/workflows/android-signed-release.yml` builds, upload-key signs, verifies and uploads the AAB without Play publication. Requires secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, plus appropriate GitHub environment permissions. The workflow has **not** been invoked with credentials; no signed production artifact is claimed.
- **Production qualification:** **NO-GO** until full release, security, device and connectivity acceptance is verified.

## Security and architecture decisions

### Controller lease (second-pass interim containment)

The previous bridge had a single mutable `activeWorkspace`, `ocSessionId`, `activePtyId` and task/diff store shared across authenticated sockets. Instead of pretending it was multi-tenant, the new controller lease permits **one active modifying device ID at a time**; others can use read-only operations. The owner may explicitly release the lease when no prompt task is active, or disconnect when idle. This is an interim safety measure, **not** equivalent to a per-device or per-session OpenCode server. Persistent, testable isolation will require per-workspace server instances with per-client state and operation serialization.

### Reconnection snapshot

A new typed `RUNTIME_STATE` frame reflects current in-memory tasks, diffs, permissions and questions and the current session/workspace. It is requested only after successful authentication. Earlier clients retain legacy behavior and do not receive a surprise frame on CONNECT; the Android client explicitly opts in. It must not be interpreted as an authoritative event journal or resumable terminal stream.

### Credential handling

A device may rotate its own bearer; the bridge commits a new SHA-256 verifier store before sending `TOKEN_ROTATED`. Older sockets for that device are invalidated. Android updates its in-memory token and uses the existing encrypted credential persistence flow. The connection can be stranded if the server commits the token and the phone crashes before persisting it. A two-phase issuance/acknowledgement and recovery protocol is a future security improvement.

### Data exposure and logs

The bridge now throttles repeated failed auth from the socket peer IP rather than trusting a client-supplied forwarding header. Large direct file/tree replies are rejected. Audit trails retain command categories/counts or cryptographic command digests instead of original terminal command bodies and git arguments, reducing incidental leakage. A compromised host or authenticated terminal still has its original privileges.

## Verification, merge and rollback

- **Checkpoint confirmed:** GitHub CI `42666969197` 4/4 successful, at `cbce59b`.
- **Newest commit:** verify its own four GitHub Actions jobs before marking subsequent changes verified.
- **ADB/physical QA:** not run; the authorized desktop became offline.
- **Release:** unsigned default CI build remains correct; manual signed workflow is separate and requires owner-managed secrets and explicit invocation.
- **Merge gate:** PR #3 is deliberately a **draft**, no auto-merge. Never bypass a failed CI job.
- **Rollback:** revert the draft branch commits or close PR #3; do not reset or discard local `main` modifications. Never disable the earlier authentication own-key check to work around pairing issues.
