# OpenCode Remote Client for Android

> A native Android application that acts as a secure remote control, dashboard, and development terminal for an OpenCode instance running on a laptop.

---

# Vision

The Android application is **not** an IDE and **does not** execute AI models locally.

Instead, it serves as a lightweight remote interface to a fully featured OpenCode server running on the user's laptop.

The laptop remains the execution engine responsible for:

- AI inference
- File operations
- Terminal execution
- Git operations
- Build systems
- Docker
- Android Studio
- Local development tools

The mobile application focuses exclusively on:

- Monitoring
- Commanding
- Reviewing
- Approving
- Managing

This architecture provides the flexibility of remote development without sacrificing the power of a desktop environment.

---

# High-Level Architecture

```text
                        ┌─────────────────────────┐
                        │     Android App         │
                        │  Remote Control Client  │
                        └────────────┬────────────┘
                                     │
                           TLS WebSocket + REST
                                     │
                     Authentication (JWT/API Key)
                                     │
                                     ▼
              ┌────────────────────────────────────┐
              │      OpenCode Remote Server         │
              │      Running on Your Laptop         │
              └────────────────────────────────────┘
                         │
         ┌───────────────┼────────────────┐
         ▼               ▼                ▼
   OpenCode CLI     File Manager     Terminal Manager
         │               │                │
         ▼               ▼                ▼
    AI Agent        Workspace FS      Shell Process
         │
         ▼
 Claude / Gemini / GPT / Local Models
```

---

# System Responsibilities

## Laptop (Execution Engine)

The laptop performs all compute-intensive operations.

### Responsibilities

- Runs OpenCode
- Executes AI models
- Hosts REST API
- Hosts WebSocket server
- Manages filesystem
- Executes terminal commands
- Runs Git
- Builds projects
- Runs Docker containers
- Executes Python
- Executes Node.js
- Runs Android Studio
- Stores project workspaces

---

## Android App (Remote Client)

The phone never directly edits the local filesystem.

Instead, it communicates with the Remote Server through secure APIs.

Responsibilities include:

- AI conversations
- File browsing
- Code viewing
- Patch approval
- Terminal interaction
- Task monitoring
- Git management
- Activity monitoring
- Log viewing
- Search
- Notifications

---

# Feature Modules

---

## 1. AI Chat

The primary interface for interacting with OpenCode.

### Flow

```text
User

↓

Chat UI

↓

Remote Server

↓

OpenCode

↓

LLM

↓

Streaming Response
```

### Features

- Streaming responses
- Markdown rendering
- Code blocks
- Image support (future)
- Context awareness
- Session history
- Token streaming

---

## 2. Project Explorer

A VS Code-style project browser.

```text
Project

▼ app

    ▼ src

        ▼ main

            ▼ java

                MainActivity.kt

                Theme.kt

                Navigation.kt

            ▼ res

        build.gradle

README.md
```

### Features

- Expand folders
- Collapse folders
- File icons
- Folder icons
- Rename
- Delete
- Move
- Copy
- Create file
- Create folder
- Search
- Recent files

---

## 3. Code Viewer

Optimized for reading source code.

```kotlin
MainActivity.kt

--------------------------------

class MainActivity {

    override fun onCreate(...)
```

### Features

- Syntax highlighting
- Search
- Jump to line
- Code folding
- Minimap (optional)
- Read-only mode
- Line numbers
- Word wrap
- Multiple language support

---

## 4. AI Diff Review

Instead of automatically modifying files, AI-generated changes are presented as patches.

```diff
MainActivity.kt

- Button(
-     onClick = {}
- )

+ FilledTonalButton(
+     onClick = {}
+ )
```

### Actions

- Accept
- Reject
- Copy
- View Full File
- Expand Context

Inspired by:

- Cursor
- GitHub Pull Requests
- VS Code Diff Editor

---

## 5. Remote Terminal

Interactive shell running on the laptop.

```text
PS F:\Projects\Portfolio>

git status

On branch main

Changes not staged...
```

### Features

- Interactive shell
- ANSI colors
- Scrollback
- Copy
- Paste
- Command history
- Auto-completion (future)
- Multiple sessions

---

## 6. Running Tasks

Displays currently executing processes.

```text
Running

● npm run dev

● gradlew assembleDebug

● docker compose up

● python app.py
```

### Features

- Live logs
- Process status
- Stop process
- Restart process
- Exit code
- Duration
- Resource usage (future)

---

## 7. File Changes

Tracks project modifications.

```text
Modified

MainActivity.kt

Theme.kt

PromptAgent.kt

README.md
```

### Features

- Modified files
- Created files
- Deleted files
- Renamed files
- Tap to open diff

---

## 8. Git Dashboard

Git operations without leaving the mobile app.

```text
Branch

main

Commits

Status

Pull

Push

Fetch

Merge

Cherry Pick
```

### Features

- Repository status
- Branch switching
- Commit history
- Stage
- Unstage
- Commit
- Pull
- Push
- Fetch
- Merge
- Cherry-pick
- Rebase (future)

---

## 9. AI Activity Monitor

Live visualization of agent execution.

```text
Thinking...

Reading

MainActivity.kt

Theme.kt

Navigation.kt

Generating patch...

Applying patch...

Running tests...

Finished.
```

### Features

- Current task
- Current file
- Search progress
- Tool usage
- Token generation
- Agent state
- Runtime statistics

---

## 10. Logs

Chronological activity timeline.

```text
12:31

Agent created file

Prompt.kt

12:32

Running npm install

12:35

Patch succeeded

12:37

Tests passed
```

### Features

- Search
- Filter
- Export
- Error highlighting
- Timestamps

---

# Backend Architecture

```text
Android App

↓

REST API

↓

Remote Controller

↓

OpenCode SDK

↓

Filesystem

↓

Terminal

↓

Git

↓

LLM
```

---

# REST API

Example endpoints

```http
GET    /projects

GET    /project/{id}

GET    /files

GET    /file

POST   /file

DELETE /file

POST   /prompt

POST   /terminal

POST   /git

GET    /logs

GET    /status

GET    /tasks

GET    /activity
```

---

# WebSocket Events

Real-time communication eliminates the need for polling.

```text
AI Thinking

↓

Token Stream

↓

Patch Generated

↓

Terminal Output

↓

Task Finished

↓

Git Changed

↓

File Updated
```

### Event Types

- Chat stream
- Agent thinking
- Terminal output
- File changes
- Git updates
- Build status
- Notifications
- Task completion

---

# Navigation Structure

```text
Home

├── Chat
├── Explorer
├── File Viewer
├── Diff Review
├── Terminal
├── Tasks
├── Git
├── Activity
├── Logs
├── Search
└── Settings
```

---

# Authentication Flow

Secure device pairing.

```text
Android

↓

QR Pairing

↓

Laptop

↓

Exchange Public Keys

↓

Generate JWT

↓

Encrypted WebSocket Session
```

### Security Features

- QR-based pairing
- Public/private key exchange
- JWT authentication
- TLS encryption
- Session management
- Device revocation
- API key fallback

Inspired by:

- VS Code Remote
- KDE Connect
- Tailscale

---

# AI Mission Control

Unlike traditional IDEs that only display the final response, the Remote Client exposes the AI agent's internal workflow.

```text
Agent #3

Reading:
/app/src/MainActivity.kt

Searching:
"OpenCodeTheme"

Editing:
Theme.kt

Running:
./gradlew lint

Checking:
Compilation...

Finished
```

### Benefits

- Complete transparency
- Real-time execution monitoring
- Easier debugging
- Improved trust in AI actions
- Better understanding of agent decision-making

This transforms the mobile application into a true **Mission Control Dashboard**, allowing developers to observe the AI's reasoning and execution rather than simply consuming the final output.

---

# Design Principles

- Laptop-first architecture
- Thin mobile client
- Secure by default
- Streaming over polling
- Read-first, edit-second workflow
- Explicit approval for AI-generated changes
- Real-time observability
- Modular feature architecture
- Offline-aware UI with graceful reconnection
- Native Android experience using Material Design 3

---

# Future Enhancements

## Collaboration

- Multi-device synchronization
- Shared workspaces
- Team sessions
- Live cursors

## AI

- Multiple concurrent agents
- Agent delegation
- Long-running autonomous tasks
- Agent marketplace

## Productivity

- Push notifications for completed tasks
- Voice prompts
- Quick command palette
- Widget support
- Wear OS companion
- Tablet-optimized layouts

## Security

- Biometric authentication
- Hardware-backed key storage
- End-to-end encrypted sessions
- Device trust management
- Audit logging

---

# Summary

The OpenCode Remote Client is designed as a **native Android command center** rather than a mobile IDE.

The laptop performs all computation, development, and AI execution, while the Android application provides a secure, responsive interface for monitoring, commanding, reviewing, and managing the entire development workflow from anywhere.

This separation of concerns delivers desktop-class development capabilities with the convenience of a mobile-first remote experience.
