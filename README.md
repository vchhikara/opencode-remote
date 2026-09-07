# OpenCode Remote

A remote control for [OpenCode](https://opencode.ai) running on your laptop, operated from an Android phone. A Node.js **bridge** process wraps a local `opencode serve` instance and exposes it over an authenticated WebSocket. A native Kotlin/Compose **Android app** connects to it to send prompts, watch responses stream in, review diffs, approve permissions, run a real terminal, and manage git, all from your phone while OpenCode does the work on your machine.

This repo has two parts that run independently:

- **`bridge/`**, a Node.js process (`main.js`) that spawns and wraps `opencode serve`, and speaks a small JSON-over-WebSocket protocol to clients.
- **`android-application/`**, the Kotlin/Compose Android client (package `com.opencode.remote`) that pairs with a bridge and drives it.

## Why

OpenCode does the actual coding work on your machine, where your repo, your shell, and your `opencode` CLI already live. This project doesn't replace that. It lets you supervise and steer it from a phone: watch the agent's response stream in as it's generated, approve or deny a permission request, answer an in-progress question, review a live diff, or open a real terminal on your laptop, without being at your laptop.

## Prerequisites

- **Node.js** 18+ (bridge process)
- **`opencode` CLI** installed and on `PATH`, so the bridge can spawn `opencode serve` for you; see [opencode.ai](https://opencode.ai)
- **Android Studio** with Android SDK, minSdk 24, target/compileSdk 36
- A JDK compatible with the Android Gradle Plugin (JDK 17 recommended, used in this project's own verified builds; Gradle's toolchain will auto-provision it if missing)
- Your phone and laptop on the same network, or a tunnel (see [`bridge/README.md`](bridge/README.md) for Tailscale/Cloudflare Tunnel setup), so the app can reach the bridge's WebSocket port

## Running the bridge

```bash
cd bridge
npm install
npm test        # runs the bridge's own test suite (node --test)
npm start        # or: node main.js
```

`npm start` boots the WebSocket server, prints a pairing QR code and token, and, lazily on the first prompt, spawns `opencode serve` bound to the workspace directory.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `PORT` (or first CLI arg) | `8080` | WebSocket listen port |
| `OPCODE` | `opencode` | Command used to spawn the OpenCode CLI |
| `BRIDGE_TOKEN` | random 32-byte hex | One-time pairing secret (printed as a QR code on startup); see [ADR 0001](adr/0001-per-device-tokens.md) |
| `WORKSPACE_ROOT` | `process.cwd()` | Root directory that `OPEN_WORKSPACE`/`ADD_WORKSPACE` are confined to |
| `DEVICE_STORE_PATH` | `~/.opencode-remote-devices.json` | Where issued per-device tokens are persisted |
| `AUDIT_LOG_PATH` | `<WORKSPACE_ROOT>/.opencode-remote-audit.log` | Append-only JSON-lines log of approved commands/edits |
| `OC_SERVE_PORT` | `4096` | Local port the bridge starts `opencode serve` on |
| `OC_PROMPT_TIMEOUT_MS` | `120000` | Timeout for a single prompt round-trip to `opencode serve` |

Example:

```bash
BRIDGE_TOKEN=mysecret WORKSPACE_ROOT=/home/me/projects PORT=8080 node main.js
```

## Building/running the Android app

```bash
cd android-application
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # build the debug APK
```

Both commands are verified to print `BUILD SUCCESSFUL` in this project's own merge gates. Open the `android-application/` directory in Android Studio to run on a device or emulator, or install the assembled APK from `app/build/outputs/apk/debug/`.

## Pairing a device

1. Start the bridge (`node main.js`). It prints a QR code and a pairing token (`ip=<ip>;key=<token>`). This token is a **one-time pairing secret**, not a long-lived credential.
2. Scan the QR code (or enter the IP/token manually) in the Android app. The app sends a `CONNECT` frame with that token.
3. The bridge validates the pairing secret, issues a fresh **per-device token**, and returns it in `CONNECTED.payload.issuedToken`. The app persists this token and uses it, not the pairing secret, on every future `CONNECT`.
4. Devices can be listed (`LIST_DEVICES`) and individually revoked (`REVOKE_TOKEN`) from the app's settings screen, without affecting other paired devices or requiring re-pairing.

See [`bridge/API.md`](bridge/API.md) for the full WebSocket protocol and [ADR 0001](adr/0001-per-device-tokens.md) for why per-device tokens exist instead of one shared credential.

## What works today

Chat with live token streaming, mid-task control (interrupt, steer, approve permission/question prompts, kill a running task), session management (list/switch/new/fork, persisted across bridge restarts), a real PTY-backed terminal, multi-file diff review with diff-on-edit streaming, multi-workspace support, reconnect-on-resume, and per-device token security with an append-only audit log. All of it is implemented and covered by the bridge's `node --test` suite and Android's unit tests plus `assembleDebug`/`testDebugUnitTest` builds.

Known, honestly-tracked gaps: per-hunk (as opposed to whole-file) diff accept/reject isn't possible, `opencode serve` has no such endpoint upstream. Android push notifications via FCM are not implemented (no Firebase project configured).

## Repository layout

```
bridge/               Node.js WebSocket bridge (main.js) and its tests
  API.md               full WebSocket protocol reference
  README.md            LAN-escape setup (Tailscale, Cloudflare Tunnel)
android-application/   Kotlin/Compose Android client (package com.opencode.remote)
adr/                   architecture decision records
tools/
```

## Documentation map

For anyone wanting more depth than this README:

- [`bridge/API.md`](bridge/API.md), the full bridge WebSocket protocol: every inbound/outbound frame type, payload shape, and behavior.
- [`adr/`](adr/), architecture decision records (e.g. why per-device tokens instead of one shared token).
- [`CONTRIBUTING.md`](CONTRIBUTING.md), how to build, test, and verify changes before proposing them.
- [`SECURITY.md`](SECURITY.md), this bridge grants shell/filesystem access over the network; read this before exposing it beyond your own LAN.

## License

No license file is currently included in this repository. Treat the code as all-rights-reserved unless the repository owner states otherwise.
