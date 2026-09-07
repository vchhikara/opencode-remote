# Contributing

This is a two-part repo (a Node.js bridge and a Kotlin/Compose Android app) with one rule that matters more than any style preference: **verify by running, not by reading.** See below for what that means in practice.

## Before you start

Read [`bridge/API.md`](bridge/API.md) for the WebSocket protocol if you're touching the bridge or the client's networking layer, and [ADR 0001](adr/0001-per-device-tokens.md) if you're touching authentication. If you're proposing a new capability, check whether `opencode serve` actually supports what you need before building around it. Some features (e.g. per-hunk diff accept/reject) were scoped out specifically because the upstream API has no endpoint for them, not by oversight.

## Running the tests

### Bridge

```bash
cd bridge
npm install
npm test        # node --test, runs every test/**/*.js file
```

### Android

```bash
cd android-application
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # build the debug APK, a real compile+link check
```

A change isn't done until both of these pass (or, for an Android-only change, at minimum the two Gradle commands above; for a bridge-only change, `npm test`). If your change touches the WebSocket protocol on both sides, run both.

## The verification discipline

A completed task means its exit criterion was actually run and passed, not that the diff looks right. A few concrete rules that implies for any contribution:

- **A task is done when its exit criterion has been run and passed**, not when the diff looks right. If you're fixing a bug, reproduce it first (a failing test, a real request/response), then fix it, then show the same check now passes.
- **Claims about a live API are verified against a live server, not assumed from documentation or memory.** This project's assumptions about `opencode serve`'s endpoints have been wrong before, and were caught by actually probing `/doc` and the `/event` SSE stream, not by re-reading a plan more carefully.
- **UI changes get an honest verification status.** "Compiles and the code looks right" and "manually tapped through on a device" are different claims. Don't describe one as the other, and don't claim a manual verification you didn't do.
- **A test suite passing is the baseline, not the finish line**, for anything that talks to a real external process (`opencode serve`, a PTY, a filesystem). Where practical, prefer a test that starts a real (or realistically faked) `opencode serve` fixture over one that mocks the bridge's own internals. See `bridge/test/` for the existing pattern (`test-fixtures/fake-opencode.js`).

## Commit and branch conventions

Follow the pattern visible in this repo's own history (`git log`):

- Prose, imperative-ish subject lines scoped by area, e.g. `Android: wire up a real Material3 theme`, `Phase 8: security hardening - per-device tokens, revocation, audit log`.
- Feature branches are named for their scope, e.g. `phase-N-<slug>`, merged back with `git merge --no-ff` so the merge commit itself marks a completed unit of work.
- A docs-only change gets its own commit prefixed by what it documents, e.g. `docs: README quickstart, bridge WebSocket API reference`.

Small, verifiable commits are preferred over one large one. Each commit is effectively an undo point.

## Scope discipline

Match the shape of the existing code rather than introducing a new pattern for a one-off change. If you find something else worth fixing while you're in a file (dead code, a stale comment, a missing test), consider a separate, smaller change rather than folding it into the diff you're already proposing. It's easier to review and easier to revert independently.

## Security-sensitive changes

The bridge grants shell execution, filesystem access, and git operations over the network (see [SECURITY.md](SECURITY.md)). Any change to authentication (`bridge/main.js`'s `CONNECT` handling), the audit log, the `GIT`/`TERMINAL` command allowlists, or workspace path confinement (`resolveInsideWorkspace`) should note in the PR description exactly what was tested and how. A passing `npm test` alone is not sufficient justification for a security-relevant change; describe the attack scenario you checked against.
