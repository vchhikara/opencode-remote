# Contributing

This is a two-part repo (a Node.js bridge and a Kotlin/Compose Android app) with one rule that matters more than any style preference: **verify by running, not by reading.** See below for what that means in practice. It's the discipline that produced [`.docs/PROGRESS.md`](.docs/PROGRESS.md), and it's the standard any change here is held to.

## Before you start

Read [`.docs/PROGRESS.md`](.docs/PROGRESS.md) for current project state, what's actually built and verified, phase by phase, and [`bridge/API.md`](bridge/API.md) for the WebSocket protocol if you're touching the bridge or the client's networking layer. If you're proposing a new capability, check [`.docs/remote-control-roadmap.md`](.docs/remote-control-roadmap.md) and [`.docs/IMPLEMENTATION_PLAN.md`](.docs/IMPLEMENTATION_PLAN.md) first. It may already be scoped, in progress, or deliberately marked "won't do" for a documented reason (e.g. an endpoint that doesn't exist upstream).

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

This project tracks work in [`.docs/PROGRESS.md`](.docs/PROGRESS.md), where every completed subtask has a **Verified-by** column recording the literal command that was run and what it actually printed, not "looks correct" or "should work." A few concrete rules that discipline implies for any contribution:

- **A task is done when its exit criterion has been run and passed**, not when the diff looks right. If you're fixing a bug, reproduce it first (a failing test, a real request/response), then fix it, then show the same check now passes.
- **Claims about a live API are verified against a live server, not assumed from documentation or memory.** `.docs/PROGRESS.md`'s "Live-API findings" section exists because the roadmap's initial assumptions about `opencode serve`'s endpoints were wrong in several places. They were caught by actually probing `/doc` and the `/event` SSE stream, not by re-reading the plan more carefully.
- **UI changes get an honest verification status.** "Compiles and the code looks right" and "manually tapped through on a device" are different claims, and this project's own ledger tracks which is which for every screen, rather than quietly upgrading one to the other. Don't claim a manual verification you didn't do.
- **A test suite passing is the baseline, not the finish line**, for anything that talks to a real external process (`opencode serve`, a PTY, a filesystem). Where practical, prefer a test that starts a real (or realistically faked) `opencode serve` fixture over one that mocks the bridge's own internals. See `bridge/test/` for the existing pattern (`test-fixtures/fake-opencode.js`).

If you're picking up an in-progress task, `.docs/PROGRESS.md`, not conversation history and not commit messages alone, is the source of truth for what's actually done versus what merely looks done. Update it as part of your change if your work advances a tracked task.

## Commit and branch conventions

Follow the pattern visible in this repo's own history (`git log`):

- Prose, imperative-ish subject lines scoped by area, e.g. `Android: wire up a real Material3 theme`, `Phase 8: security hardening - per-device tokens, revocation, audit log`, `PROGRESS.md: record Phase N completion`.
- Feature branches are named for their scope, e.g. `phase-N-<slug>`, merged back with `git merge --no-ff` so the merge commit itself marks a completed unit of work.
- A docs-only change gets its own commit prefixed by what it documents, e.g. `docs: README quickstart, bridge WebSocket API reference`.

Small, verifiable commits are preferred over one large one. Each commit is effectively an undo point, and `.docs/PROGRESS.md` rows link back to the specific commit that made their exit criterion pass.

## Scope discipline

Match the shape of the existing code rather than introducing a new pattern for a one-off change. If you find something else worth fixing while you're in a file (dead code, a stale comment, a missing test), consider a separate, smaller change rather than folding it into the diff you're already proposing. It's easier to review and easier to revert independently.

## Security-sensitive changes

The bridge grants shell execution, filesystem access, and git operations over the network (see [SECURITY.md](SECURITY.md)). Any change to authentication (`bridge/main.js`'s `CONNECT` handling), the audit log, the `GIT`/`TERMINAL` command allowlists, or workspace path confinement (`resolveInsideWorkspace`) should note in the PR description exactly what was tested and how. A passing `npm test` alone is not sufficient justification for a security-relevant change; describe the attack scenario you checked against.
