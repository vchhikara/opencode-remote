# ADR 0003: Contain shared bridge state with a device control lease

- Date: 2026-11-09
- Status: Accepted as an interim containment, pending end-to-end QA
- Related audit: [second-pass remediation](../docs/audits/2026-11-09-remediation-round2.md)

## Context

The Node bridge exposes a single mutable `activeWorkspace`, OpenCode server process and active session ID, plus a shared PTY and task/diff store. All authenticated WebSocket clients previously operated on that state without arbitration. One device could change the workspace or session while another device was running a prompt or reading files. This could route edits or terminal activity into a different context than a client expected.

## Decision

Only one authenticated `deviceId` may issue mutating operations at a time. The first valid modifying request acquires the in-memory controller lease. Requests from a different authenticated device receive `ERROR` with the attempted `operation`, while passive read operations remain available. A modifying client cannot initiate workspace/session switches or overlapping prompts while a prompt task is in progress, even if it owns the lease.

The controller can explicitly send `RELEASE_CONTROL` while no prompt is active. The lease is also released after the controlling device disconnects and the task map is idle. Lease state is transient and resets on bridge restart.

## Consequences

- Prevents the most direct two-device workspace/session hijack caused by unrestricted concurrent mutations.
- Does not isolate data between paired devices; both trusted devices may still view shared state.
- Does not serialize every command from one device and does not provide per-device PTYs, server processes, session stores or output journals.
- In-flight external subprocesses may outlive the task map that guards context changes. A full request queue with explicit resource ownership is still needed.
- Changes intended to provide true multi-device state isolation must replace this lease with per-device/per-workspace OpenCode instances and per-client routing, not remove the guard without a replacement.

## Future exit criteria

1. Distinct context ownership for each device/workspace and server instance.
2. End-to-end correlation of commands, data and errors.
3. Deterministic cleanup and cancellation, including terminal and Git subprocesses.
4. Automated concurrency stress tests and physical-device network handoff QA.
