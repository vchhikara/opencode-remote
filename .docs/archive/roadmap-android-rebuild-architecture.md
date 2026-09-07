# OpenCode Android Remote Client

## Architecture Principles

> This document defines the non-negotiable architectural principles for the OpenCode Android Remote Client.
>
> Every implementation decision must comply with these principles.
> If a feature conflicts with this document, the feature should be redesigned rather than the architecture.

---

### 1. Project Philosophy

The Android application is **NOT** an IDE.

It is **NOT** an AI runtime.

It is **NOT** a development environment.

It is a secure remote interface for a laptop running the OpenCode daemon.

The laptop performs every meaningful operation.

The Android application only visualizes state and sends commands.

---

### 2. System Ownership

```text
Android Client
        │
        ▼
Remote Session
        │
        ▼
OpenCode Daemon (Laptop)
        ├── AI Agent
        ├── Filesystem
        ├── Git
        ├── Terminal
        ├── Running Tasks
        ├── Logs
        └── Model Providers
```

#### Responsibilities

**Android**
- Display UI
- Display code
- Display logs
- Display terminal
- Display AI progress
- Send commands
- Receive streamed events

**Laptop**
- Execute AI
- Read/write files
- Run shell commands
- Manage Git
- Execute builds
- Manage tasks
- Stream logs
- Stream AI events

The laptop is the **single source of truth**.

---

### 3. Thin Client Architecture

The Android application should contain almost zero business logic.

Business rules belong on the laptop.

The mobile application should never duplicate logic already implemented by OpenCode.

**Good**
```text
Phone → Prompt → Laptop → AI → Result
```

**Bad**
```text
Phone → Business Logic → Laptop → Different Business Logic
```

---

### 4. Navigation Philosophy

Avoid unnecessary navigation layers.

Do **not** build a dashboard simply because most apps have one.

Preferred flow:
```text
Pair Device → Workspace List → Project → Project Modules
```

No fake analytics.
No marketing home screen.
No empty dashboards.

---

### 5. Device Pairing

The application should not have a traditional login screen.

Instead:
```text
Pair Device
├── Scan QR Code
├── Manual IP
├── Remember Device
└── Secure Authentication
```

Once paired, reconnect automatically.

---

### 6. Remote Workspace

Projects remain on the laptop.

Android requests information when needed.
```text
Open Project → Request Directory → Receive Directory → Display Tree
```

No local copies.
No synchronization engine.

---

### 7. Remote File Browser

This is **NOT** file synchronization.

It is a remote browser.
```text
Android → GET /files → Laptop → Directory Listing → Android
```

Opening a file:
```text
Open File → GET /file/{path} → Laptop → File Contents → Android
```

The Android application never owns project files.

---

### 8. Code Viewing Philosophy

The Android application is optimized for reading code.

Primary goals:
- Syntax highlighting
- Fast scrolling
- Search
- Line numbers
- Copy
- Share

Editing occurs through AI-generated patches.

---

### 9. Activity Monitor

The Activity Monitor displays **real** execution events.

Never generate fake animations.

The laptop streams actual events.

Example:
```text
Reading
/app/src/MainActivity.kt

Searching
Theme.kt

Planning
Editing
Running
gradlew assembleDebug

Applying Patch
Finished
```

Every event originates from OpenCode.

---

### 10. Remote Terminal

The terminal is a live stream.

Android never interprets commands.
```text
Phone → POST /terminal → Laptop → PTY → Terminal Output → Android
```

Everything displayed is produced by the laptop.

---

### 11. Networking Strategy

Use two communication mechanisms.

**REST**
For deterministic operations.
Examples:
```text
GET /projects
GET /files
GET /logs
POST /prompt
POST /terminal
POST /git
```

**WebSocket**
For streaming events.
Examples:
```text
AI Token Stream
AI Progress
Terminal Output
Git Updates
Task Updates
Filesystem Events
Logs
Connection Status
```

---

### 12. Remote Session Manager

Every network interaction flows through a single session manager.
```text
UI → Repository → RemoteSessionManager → REST / WebSocket → Laptop
```

Responsibilities:
- Authentication
- Connection lifecycle
- Heartbeats
- Automatic reconnect
- Retry policies
- Encryption
- Message routing
- Session state

No screen communicates with Ktor directly.

---

### 13. Repository Layer

Repositories isolate the UI from networking.
```text
ViewModel → Repository → RemoteDataSource → RemoteSessionManager → Ktor
```

Never:
```text
ViewModel → Ktor
```

---

### 14. Command Bus

Every user interaction becomes a typed command.
```text
UI → Command → Remote Session Manager → Laptop
```

The UI emits commands.
The session manager executes them.

---

### 15. Local Storage

Use Preferences DataStore.

Persist only lightweight client state.
Examples:
- Paired devices
- Last connected device
- Authentication token
- Theme
- User preferences
- Recent connections

Do **not** store:
- Projects
- Source code
- AI history
- Git repositories

If local persistence grows significantly, evaluate Room only when justified.

---

### 16. Connected Device

The application should expose remote system information.

This screen is informational only.

---

### 17. Notifications

Long-running tasks should notify the user.
Examples:
- AI task completed
- Build finished
- Build failed
- Tests completed
- Terminal exited
- Git operation completed

The user should not need to keep the application open.

---

### 18. Design Principles

Every feature should satisfy these rules.

- Laptop owns application state.
- Android owns presentation state.
- No duplicated business logic.
- Every filesystem operation is remote.
- Every AI operation is remote.
- Every shell command is remote.
- Every Git action is remote.
- Every long-running task is observable.
- Every AI modification is reviewable before acceptance.
- UI remains responsive even when disconnected.

---

### 19. Target Architecture

```text
Jetpack Compose
        │
Navigation Compose
        │
ViewModels (State Only)
        │
Repositories
        │
RemoteDataSource
        │
RemoteSessionManager
        │
REST + WebSocket (Ktor)
        │
────────────────────────────────────────
OpenCode Remote Daemon
        ├── AI Agent
        ├── Filesystem
        ├── Git
        ├── Terminal
        ├── Running Tasks
        ├── Logs
        ├── Workspace
        ├── Patch Engine
        └── Model Providers
```

---

### 20. Guiding Principle

> **The Android application is a remote window into OpenCode, not a second implementation of OpenCode.**

Whenever a new feature is proposed, ask:

> **"Should this logic live on the laptop or on the phone?"**

If the answer is "laptop", the Android application should request the result rather than reproduce the logic.

---
---

## Engineering Roadmap & Sprints

> **Project Goal**
>
> Build a native Android client that securely controls a laptop running the OpenCode daemon.
> The Android application is a thin, state-driven remote interface that streams data from the desktop in real time.

---

### Sprint 1: Core Architecture & Connectivity (Foundation)
**Goal:** Create a clean production architecture and establish remote communication with OpenCode.

- [x] Configure Navigation Compose
- [x] Configure Material Theme
- [x] Resolve DI strategy — Hilt was removed (`remove_hilt.py`); manual injection in use. Revisit only if testability demands it.
- [x] Configure Ktor (REST + WebSockets) — `KtorClient.kt`: OkHttp engine, JSON content negotiation, WS ping 15s
- [x] Configure DataStore (Local persistence) — `data/DataStoreManager.kt` (plain-text; encryption tracked as open task 3.3)
- [x] Build REST client
- [x] Build WebSocket client wrapper
- [x] Implement `RemoteSessionManager` with automatic reconnect & heartbeat — reconnect loop w/ retry delay verified; heartbeat via Ktor WS pingInterval
- [ ] Remove all local mock/prototype logic (seeded terminal lines + static screen content still present)

**Deliverable:** A robust foundation capable of maintaining a stable connection to a remote backend.

---

### Sprint 2: Authentication & Workspace Management
**Goal:** Securely pair with the laptop and browse available remote workspaces.

- [x] Build QR Code Scanner UI
- [x] Implement API key exchange / Secure token storage
- [x] Build Device Connection Management (Remember/Rename/Remove paired devices)
- [x] Fetch and display available workspaces
- [x] Open and switch active projects
- [x] Implement search functionality for projects

**Deliverable:** Secure laptop pairing and remote project selection.

---

### Sprint 3: Remote Filesystem & Reading
**Goal:** Browse and view remote files with full fidelity (Read-first philosophy).

- [x] Implement Directory Tree with lazy loading
- [x] Expand, collapse, and search remote files
- [x] Fetch remote file contents
- [x] Implement Syntax Highlighting in the native code viewer
- [x] Implement scroll restoration and opened file caching
- [x] Add line numbers, word wrap, and copy functionality

**Deliverable:** Production-quality file explorer and code browser.

---

### Sprint 4: AI Chat & Execution Telemetry
**Goal:** Enable complete control of the remote AI agent and visualize its thought process.

- [x] Connect Project Chat UI to WebSocket streams (streaming tokens)
- [x] Add Markdown and code block rendering support
- [x] Implement voice input and attachment capabilities
- [x] Hook Activity Monitor to real-time execution states (Reading, Searching, Planning, Running commands)
- [x] Implement conversation history synchronization

**Deliverable:** Fully functional remote AI control and telemetry visualization.

---

### Sprint 5: Advanced Controls (Diffs & Terminal)
**Goal:** Allow human oversight for AI patches and provide manual override via live terminal.

- [x] Implement Diff Review UI (Accept/Reject patches)
- [x] View full file patch context and copy patches
- [x] Connect Remote Terminal UI to live PTY stream
- [x] Handle terminal ANSI colors, history, and scrollback
- [x] Handle interactive terminal inputs and resizing

**Deliverable:** Safe AI editing with manual terminal override capabilities.

---

### Sprint 6: Source Control & Background Tasks
**Goal:** Monitor and manage background workloads and version control remotely.

- [x] Connect Running Tasks monitor (npm, Gradle, Python, etc.)
- [x] Implement task controls (Stop, Restart, View Output)
- [x] Build Remote Git Dashboard (Status, Branch, Pull, Push, Fetch)
- [x] Implement Git actions (Commit, Pull, Push, Fetch)
- [ ] Implement Git actions (Stash, Cherry Pick) — not implemented, no UI entry point
- [ ] Implement Push Notifications for background events — not implemented, no FCM wiring

**Deliverable:** Comprehensive remote system management (Git + Processes).

---

### Sprint 7: Hardening, Polish & Launch
**Goal:** Production readiness, testing, and distribution.

- [x] Optimization (Lazy loading, Image caching, Connection pooling)
- [x] UX Polish (Haptics, Animations, Accessibility, Tablet & Foldable layouts)
- [x] Write Unit Tests (ViewModels, Repositories, Session Manager)
- [x] Write UI & Screenshot Tests (Compose, Navigation, Roborazzi)
- [x] Configure ProGuard / R8
- [x] Setup CI/CD and Crash Reporting
- [ ] Release to Internal Testing / Play Store — not done; this is source, not a shipped release

**Deliverable:** A polished, secure, and performant production release.

---

## Current status (reconciled against the shipped code)

The backend is a single Node.js WebSocket bridge (`bridge/main.js`) speaking
the canonical `{eventType, payload}` envelope documented in
[`docs/integration-context.md`](docs/integration-context.md) — there is no
separate Express server. Auth is required (`CONNECT{deviceName, token}`
handshake); cleartext is restricted to LAN/loopback via network security
config.

Explicitly **not implemented** (surfaced to the user, not faked):
device management (remember/rename/remove), git stash/cherry-pick, system
logs, conversation history sync, push notifications, and (pending H-DEVICE
verification) automatic mDNS discovery. See
[`docs/integration-context.md`](docs/integration-context.md) for the full
list and the real wire protocol.
