# OpenCode Remote

A remote control for [OpenCode](https://opencode.ai) running on your laptop, operated from an Android phone. A Node.js **bridge** process wraps a local `opencode serve` instance and exposes it over an authenticated WebSocket; a native Kotlin/Compose **Android app** connects to it to send prompts, review diffs, approve permissions, run terminal commands, and manage git — all from your phone while OpenCode does the work on your machine.

## Prerequisites

- **Node.js** 18+ (bridge process)
- **`opencode` CLI** installed and on `PATH` (the bridge spawns `opencode serve`) — see [opencode.ai](https://opencode.ai)
- **Android Studio** with Android SDK — minSdk 24, targetSdk/compileSdk 36
- A JDK compatible with the Android Gradle Plugin (JDK 17 recommended; used in this project's own verified builds)
- Your phone and laptop on the same network (or a tunnel) so the app can reach the bridge's WebSocket port

## Running the bridge

```bash
cd bridge
npm install
npm test        # runs the bridge's own test suite (node --test)
npm start        # or: node main.js
```

`npm start` boots the WebSocket server, prints the pairing QR code/token, and (lazily, on the first prompt) spawns `opencode serve` bound to the workspace directory.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `PORT` (or first CLI arg) | `8080` | WebSocket listen port |
| `OPCODE` | `opencode` | Command used to spawn the OpenCode CLI |
| `BRIDGE_TOKEN` | random 32-byte hex | One-time pairing secret (printed as a QR code on startup); see [ADR 0001](docs/adr/0001-per-device-tokens.md) |
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
cd opencode_remote_android_app
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # build the debug APK
```

Both commands are verified to print `BUILD SUCCESSFUL` in this project's own CI/merge gates (see `PROGRESS.md`). Open the `opencode_remote_android_app/` directory in Android Studio to run on a device or emulator, or install the assembled APK from `app/build/outputs/apk/debug/`.

## Pairing a device

1. Start the bridge (`node main.js`). It prints a QR code and a pairing token (`ip=<ip>;key=<token>`) — this token is a **one-time pairing secret**, not a long-lived credential.
2. Scan the QR code (or enter the IP/token manually) in the Android app. The app sends a `CONNECT` frame with that token.
3. The bridge validates the pairing secret, issues a fresh **per-device token**, and returns it in `CONNECTED.payload.issuedToken`. The app persists this token and uses it (not the pairing secret) on every future `CONNECT`.
4. Devices can be listed (`LIST_DEVICES`) and individually revoked (`REVOKE_TOKEN`) without affecting other paired devices or requiring re-pairing.

See [bridge/API.md](bridge/API.md) for the full WebSocket protocol and [docs/adr/0001-per-device-tokens.md](docs/adr/0001-per-device-tokens.md) for why per-device tokens exist.

## Repository layout

- `bridge/` — the Node.js WebSocket bridge (`main.js`) and its tests
- `opencode_remote_android_app/` — the Android client (Kotlin/Compose)
- `docs/adr/` — architecture decision records
- `PROGRESS.md`, `IMPLEMENTATION_PLAN.md` — development history and phased roadmap for this project
