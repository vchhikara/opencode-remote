# Archive

Historical/superseded documents, kept for reference only. Nothing here reflects
current project state — for that, see [`../PROGRESS.md`](../PROGRESS.md) and
[`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md).

## Dead "Android rebuild" thread

A separate engagement proposed rebuilding the Android app from scratch after a
report that "the app is not working." That rebuild was never carried out — the
current `android-application/` source is a continuation of the original,
already-shipped app tracked in `../PROGRESS.md`, not a rewrite. Kept for
historical context only; do not treat as a live plan.

| File | Was |
|---|---|
| `handoff.md` | Session handoff note that introduced the rebuild thread |
| `intent.md` | Why/goals for the proposed rebuild |
| `spec.md` | Contract the rebuild was meant to satisfy |
| `plan.md` | Phased execution plan for the rebuild |
| `tasks.md` | A separate integration checklist for the old app; stale even at the time it was written (e.g. claims encrypted token storage was missing — it was already implemented in `TokenStorage.kt`) |
| `roadmap-android-rebuild-architecture.md` | Architecture principles doc the rebuild plan cited as binding |

## Pre-rewrite design material (Lovable-era mock shell)

An earlier iteration of the app was a static frontend shell with mock data,
modeled after a Lovable.dev companion app design.

| File | Was |
|---|---|
| `roadmap.md` | Progress/phase tracking for the mock-shell iteration |
| `list.md` | Feature gap list from the mock-shell → live-integration transition |
| `integration-context.md` | Client↔server protocol contract as understood at that time |
| `Rough_idea.md` | Original vision document for the whole project |
| `loveable_design/` | Design reference docs for the Lovable-style mobile companion app |
| `CODE_REVIEW_REPORT.md` | A code review of the mock-shell iteration; superseded by the dated review under `../reports/` |

## Misc

| File | Was |
|---|---|
| `ABOUT.md` | Generic boilerplate self-description, not specific to this project |
| `welcome.md` | One-line placeholder greeting, not a real doc |
