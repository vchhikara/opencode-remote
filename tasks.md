# OpenCode Android Remote Client: Incremental Integration Checklist

This document breaks down the remaining integration and productionizing work from `docs/archive/list.md` and `docs/integration-context.md` into highly manageable, structured, and sequentially actionable tasks. Use this checklist inside OpenCode to track implementation progress.

---

## Phase 1: Communication Protocol & Strongly-Typed Messaging

Goal: Transition from raw string messages to a robust, type-safe JSON protocol.

- [x] **Task 1.1: Add kotlinx.serialization Plugin & Dependencies**
  - Configure the build catalog and Gradle plugin to enable Kotlin Serialization compiler features.
- [x] **Task 1.2: Define Protocol Schema Entities**
  - Create the serialized Kotlin data classes corresponding to all OpenCode entities (e.g., `WorkspaceDto`, `FileNodeDto`, `ChatMessageDto`, `DiffPatchDto`, `TaskProcessDto`, `GitStatusDto`).
- [x] **Task 1.3: Author WebSocket Frame Envelope**
  - Define a sealed interface or polymorphic JSON wrapper to serialize/deserialize all inbound and outbound client-server frames under a single schema structure.
  - E.g., `data class WebSocketFrame(val eventType: String, val payload: JsonElement)`
- [x] **Task 1.4: Refactor Ktor Connection Lifecycle in `RemoteSessionManager`**
  - Enable the JSON content negotiation plugin for Ktor's WebSocket client.
  - Refactor packet reception to route decoded frames directly to their respective StateFlow handlers.

---

## Phase 2: Connection Resilience & Auto-Discovery

Goal: Make connecting to OpenCode seamless, self-healing, and secure.

- [x] **Task 2.1: Model the Connection State Machine**
  - Replace raw connection booleans in `RemoteSessionManager` with an explicit, observable enum/sealed class: `Disconnected`, `Searching`, `Pairing`, `Connecting`, `Connected`, `Reconnecting`.
- [x] **Task 2.2: Implement Local Service Discovery (mDNS)**
  - Integrate Android's `NsdManager` (Network Service Discovery) to automatically resolve the OpenCode daemon's IP address on the local network using the `_opencode._tcp` service type.
- [x] **Task 2.3: Establish Heartbeats (Ping/Pong)**
  - Configure background coroutine loops to send periodically timed WebSocket ping frames to verify active channel status.
- [x] **Task 2.4: Build Exponential Backoff Reconnection Logic**
  - Write a robust reconnect scheduler that handles physical network loss, Wi-Fi switching, and laptop sleep states with exponential retry intervals.

---

## Phase 3: Cryptographic Device Pairing & Authorization

Goal: Protect files and terminals from unauthorized access.

- [x] **Task 3.1: Design One-Time PIN / QR Pairing UI**
  - Build a beautiful overlay or initial configuration wizard inside the onboarding stream.
- [x] **Task 3.2: Secure Handshake Negotiation**
  - Implement a key-exchange flow over REST where the companion app provides its device metadata and receives a cryptographically signed JSON Web Token (JWT) or persistent API Key.
- [ ] **Task 3.3: Configure Encrypted Local Persistence (EncryptedDataStore)**
  - Migrate plain-text server URLs and paired auth tokens to an AES-encrypted Android DataStore.
  - STATUS: NOT DONE — current impl (`data/DataStoreManager.kt`) uses plain-text Preferences DataStore for API key + last IP; no encryption layer present.

---

## Phase 4: Production Terminal & Shell Parsing

Goal: Turn raw CLI streams into an interactive, readable visual terminal.

- [x] **Task 4.1: Write ANSI Color Escape Parser**
  - Create a utility class that strips or parses raw ANSI strings into Material 3 `AnnotatedString` structures (interpreting bold, text-color changes, and reset flags).
- [x] **Task 4.2: Build Keyboard Input Interceptor**
  - Expand `TerminalScreen` text field inputs to support standard control commands like `Ctrl+C`, `Ctrl+Z`, `Ctrl+D`, and `Tab` auto-compositions.
- [x] **Task 4.3: Implement Window Bounds & PTY Resizing Sync**
  - Listen to container layout measurements in Compose and send column/row resize updates to the OpenCode host on size changes.

---

## Phase 5: Rich Interactive AI Chat Extensions

Goal: Enhance collaboration with voice and assets.

- [x] **Task 5.1: Integrate Android Speech Recognizer SDK**
  - Connect a recording state to the Speech-to-Text API to allow developers to transcribe spoken prompts into the AI search panel.
- [x] **Task 5.2: Build Document & Asset Picker**
  - Implement standard Android storage/gallery intent pickers to attach images, logs, or reference code files.
- [x] **Task 5.3: Implement Multipart Chunk Upload Manager**
  - Build a background upload helper that sends larger binary files to the daemon securely via Ktor REST endpoints with progress reporting.

---

## Phase 6: Granular Code Review & Source Control

Goal: Enable precision editing oversight.

- [x] **Task 6.1: Build Adaptive Split View (Side-by-Side Diffs)**
  - Leverage Jetpack Compose Window Size classes to show side-by-side file comparisons on Tablet and Foldable screens.
- [x] **Task 6.2: Implement Hunk-Level Diff Acceptance**
  - Break down full-file diff blocks into individual hunks/regions, allowing users to accept or discard code updates granularly.
- [x] **Task 6.3: Advanced Git Interactive Actions**
  - Extend the Git tab to support branching (creation/switching), stash-pop, and cherry-picking commit hashes directly from the UI.
