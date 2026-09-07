# Intent — OpenCode Remote Android Client (Rebuild)

## Why this exists

The current Android client (`app/`) compiles and packages now, but is not
reliably usable — it accumulated hallucinated dependencies, dead/duplicated
screens, mock data left in production paths, and architecture drift from
several rounds of AI-assisted patching without a single owner keeping the
whole picture straight. Patching it further has diminishing returns: every
fix surfaces another file with the same class of problem.

The bridge (`bridge/main.js`) is the opposite story — small, single-file,
fully tested (14/14 `node --test`), and its wire protocol is already proven
correct against a real client. **This rebuild only replaces the Android app.
The bridge is a fixed, trusted contract — do not modify it to make the app's
life easier; the app conforms to the bridge, not the other way round.**

## Problem statement

A developer runs `opencode` (the AI coding CLI) on their laptop. They want to
watch it work, review and approve its changes, and nudge it — from their
phone, without carrying the laptop around or being tied to a desk. The bridge
already exposes everything needed for that over a local WebSocket. Nothing
today lets a phone consume it reliably.

## Goals

- A phone can pair with the bridge (QR, manual IP+token, or mDNS) and stay
  connected across app backgrounding and brief network drops.
- Every screen shows **real bridge state** — nothing simulated, seeded, or
  hardcoded as a placeholder for a feature that doesn't exist yet.
- The AI agent's activity, chat, file tree, diffs, terminal output, running
  tasks, and git status are all live and observable from the phone.
- A rebuild a competent Android developer can actually finish: small,
  reviewable increments, each one runnable and demonstrably working before
  the next starts.

## Non-goals

- Redesigning or extending the bridge protocol. If a feature needs a bridge
  change, that's a separate, explicit decision — not something the app
  quietly works around.
- Feature parity with the old app's screen *count*. Several existing screens
  (Devices management, System Logs, Preview/WebView) have no real backing
  data and are explicitly out of scope until the bridge supports them (see
  [docs/integration-context.md](docs/integration-context.md) "Not
  implemented" list, which remains authoritative).
- Offline-first / local caching of project files or AI history. The app is a
  thin remote window, not a sync engine (this principle carries over
  unchanged from [roadmap.md](roadmap.md) §1–3).
- Multi-bridge / multi-device management. One phone, one active bridge
  connection at a time.
- Publishing to Play Store, CI signing, ProGuard/R8 tuning. Get it correct
  and usable first.

## Guiding principles (carried over, still correct)

From the existing [roadmap.md](roadmap.md) architecture section — these
were right, the implementation just didn't follow them:

- The laptop is the single source of truth. The phone displays state and
  sends commands; it does not compute anything the bridge already computes.
- No screen talks to Ktor/WebSocket directly — everything flows through one
  session manager and a repository layer.
- Every AI modification is reviewable before acceptance; nothing auto-applies.
- UI stays responsive and legible even when disconnected — a disconnected
  state is a real, designed state, not a crash or a blank screen.

## Definition of done for this rebuild

- Fresh install → scan QR → connected within the same session, workspace
  file tree visible, chat sends a prompt and streams a real response from
  the bridge-spawned `opencode` process.
- Kill the bridge process mid-session: the app shows "disconnected", does
  not crash, and reconnects automatically when the bridge comes back.
- Every screen in the nav graph is reachable and shows either real data or
  an explicit "not available" state — never a hardcoded sample.
- `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` succeed on a
  clean checkout with no manual source edits.
