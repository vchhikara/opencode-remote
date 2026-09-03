# Domain Analysis Report: OpenCode

**Project:** OpenCode
**Languages:** Kotlin, JavaScript, Python
**Frameworks:** Android, Jetpack Compose, WebSocket (Node.js)
**Description:** OpenCode Remote Client for Android. A native Android app acting as a secure remote control, dashboard, and development terminal for an OpenCode instance running on a laptop.

---

## Domain: Connection and Pairing
**Summary:** Manages device pairing and WebSocket communication between the Android client and the laptop instance.
**Tags:** `websocket`, `pairing`, `network`, `auth`
**Complexity:** Complex
**Entities:** Device, WebSocketClient, RemoteSessionManager
**Business Rules:** Must establish a secure connection to the local instance for real-time syncing.
**Cross-Domain Interactions:** Provides the underlying transport layer for Agent Interaction and Workspace Management domains.

### Flow: Device Pairing
**Summary:** Pair the Android app with a remote local instance using an IP address or by scanning a QR code.
**Entry Point:** `AuthScreen` / `DevicesScreen`
**Entry Type:** Manual

* **Step: Scan QR Code / Input IP**
  * **Summary:** User scans a QR code using camera or inputs an IP to pair.
  * **File:** `app/src/main/java/com/example/AuthScreen.kt`
* **Step: Manage Devices**
  * **Summary:** View and select previously paired devices.
  * **File:** `app/src/main/java/com/example/DevicesScreen.kt`

### Flow: WebSocket Connection
**Summary:** Establish and maintain the real-time bidirectional WebSocket connection to the Node bridge server.
**Entry Point:** Node.js Bridge Server (`bridge/main.js`)
**Entry Type:** Event

* **Step: Accept Connection**
  * **Summary:** Server accepts incoming WebSocket connections from the client.
  * **File:** `bridge/main.js`
* **Step: Handle Messages**
  * **Summary:** Parse and route incoming commands and data.
  * **File:** `bridge/main.js`
* **Step: Manage Session State**
  * **Summary:** Maintain connection state within the Android app architecture.
  * **File:** `app/src/main/java/com/example/MainViewModel.kt`

---

## Domain: Workspace and File Management
**Summary:** Handles exploring project repositories, viewing source files, reviewing code diffs, and switching active workspaces.
**Tags:** `files`, `diff`, `workspace`, `repository`
**Complexity:** Moderate
**Entities:** FileTree, Workspace, DiffHunk
**Business Rules:** Provides read-only views of remote file systems and pending repository changes.

### Flow: Workspace Navigation
**Summary:** Browse available workspaces and explore the nested file repository structure.
**Entry Point:** `WorkspacesScreen` / `RepositoryScreen`
**Entry Type:** Manual

* **Step: List Workspaces**
  * **Summary:** Display available workspaces to the user.
  * **File:** `app/src/main/java/com/example/WorkspacesScreen.kt`
* **Step: Explore Files**
  * **Summary:** Navigate the file tree of the selected workspace.
  * **File:** `app/src/main/java/com/example/ProjectExplorerScreen.kt`
* **Step: View Repository**
  * **Summary:** Present the repository structure and contents.
  * **File:** `app/src/main/java/com/example/RepositoryScreen.kt`

### Flow: Code Review
**Summary:** View syntax-highlighted source code, rendered markdown, and review git diffs before accepting changes.
**Entry Point:** `CodeViewerScreen` / `DiffReviewScreen`
**Entry Type:** Manual

* **Step: View Code File**
  * **Summary:** Render the content of a selected source code file.
  * **File:** `app/src/main/java/com/example/CodeViewerScreen.kt`
* **Step: Render Markdown**
  * **Summary:** Render markdown files (like READMEs) natively in the app.
  * **File:** `app/src/main/java/com/example/MarkdownViewer.kt`
* **Step: Review Diff**
  * **Summary:** Review pending diff hunks to accept or reject agent code modifications.
  * **File:** `app/src/main/java/com/example/DiffReviewScreen.kt`

---

## Domain: Agent Interaction and Chat
**Summary:** The core conversational interface for sending commands to the AI coding assistant and tracking its task progress.
**Tags:** `chat`, `ai`, `tasks`, `terminal`
**Complexity:** Complex
**Entities:** ChatMessage, GenerationState, Task, Logs
**Business Rules:** Commands must be processed sequentially, and updates must stream back in real-time.

### Flow: Agent Chat
**Summary:** Interact with the AI agent through a chat interface and track generation states.
**Entry Point:** `ProjectChatScreen`
**Entry Type:** Manual

* **Step: Send Prompt**
  * **Summary:** User types or dictates a prompt and sends it to the agent.
  * **File:** `app/src/main/java/com/example/ProjectChatScreen.kt`
* **Step: Track Generation State**
  * **Summary:** Monitor whether the agent is idle, generating, succeeding, or erroring.
  * **File:** `app/src/main/java/com/example/GenerationState.kt`
* **Step: Update View Model**
  * **Summary:** Update UI state continuously as the agent streams responses.
  * **File:** `app/src/main/java/com/example/MainViewModel.kt`

### Flow: Task Monitoring
**Summary:** Monitor running background tasks and view live terminal logs.
**Entry Point:** `TasksScreen` / `TerminalScreen`
**Entry Type:** Manual

* **Step: View Active Tasks**
  * **Summary:** Display a list of currently running processes or tasks.
  * **File:** `app/src/main/java/com/example/TasksScreen.kt`
* **Step: View Terminal Output**
  * **Summary:** Display standard output and standard error from the running terminal processes.
  * **File:** `app/src/main/java/com/example/TerminalScreen.kt`
* **Step: View Logs**
  * **Summary:** Display application or system logs for debugging and monitoring.
  * **File:** `app/src/main/java/com/example/LogsScreen.kt`
