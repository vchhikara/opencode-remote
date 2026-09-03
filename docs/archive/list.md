# Remaining Features for Full Integration

Below is the list of features, protocols, and technical integrations yet to be implemented in order to transition the OpenCode Companion App from mocked simulation states to a fully live, real-world connection with the OpenCode CLI/IDE.

---

## 1. Real-Time JSON Protocol & Serialization
*   **Implement Strong-Typed JSON Frames:** Replace raw string commands (e.g., `"ACCEPT_DIFF:file"`) with structured JSON models for both incoming and outgoing messages.
*   **Ktor Serialization Binding:** Configure the Ktor WebSocket client in `RemoteSessionManager.kt` to automatically deserialize incoming frames into Kotlin data classes using `kotlinx.serialization`.
*   **State Stream Subscriptions:** Dynamically subscribe to separate message topics or event channels (e.g., `/topic/terminal`, `/topic/workspace_updates`) rather than expecting a single socket endpoint to handle all multiplexed state.

## 2. Service Discovery & Connection Resilience
*   **Local MDNS/NSD Discovery:** Implement Android Network Service Discovery (NSD) to auto-detect OpenCode IDE servers running on the local Wi-Fi network, eliminating the need to type in port numbers and local IP addresses manually.
*   **Robust Reconnection Loop:** Enhance `RemoteSessionManager` with an exponential backoff retry system that safely handles connection drops, Wi-Fi handovers, and remote server restarts without locking or crashing the UI.
*   **Secure Authentication Pairing:** Implement a one-time cryptographic PIN or QR-code scanner pairing process to authorize the Android device before opening sensitive filesystem and terminal access.

## 3. Advanced Terminal & Shell Features
*   **ANSI Escape Sequence Parser:** Integrate a lightweight ANSI parser on the client side to translate terminal color codes, text weights, and cursor instructions into RichText/AnnotatedStrings inside `TerminalScreen.kt`.
*   **Interactive PTY Control:** Connect keyboard IME actions to transmit special control keys (like `Ctrl+C`, `Ctrl+Z`, `Tab` autocomplete signals) to the remote terminal instance.
*   **Terminal Resizing Sync:** Send orientation and screen-size dimension updates to the remote PTY on size changes so that text wrapping matches the mobile screen width.

## 4. Enhanced Diff & Source Control Review
*   **Detailed Side-by-Side Diffing:** Create a tabbed or side-by-side diff view on tablet screens using window width classes, allowing users to see the original file next to the proposed AI patch.
*   **Granular Block-Level Reviews:** Implement a split line review system where developers can accept or reject individual hunks/blocks of a patch instead of only accepting the entire file diff at once.

## 5. Live Task Logs & Output Monitors
*   **Task Output Streams:** Implement a stream channel to capture and view live log outputs (`stdout`/`stderr`) of background processes (like dev servers, compilers, and test runs) when clicking on a task inside `TasksScreen.kt`.
*   **Task Configurator:** Add the ability to launch new tasks directly from the companion app by selecting from a list of configured scripts (e.g., `package.json` scripts, Gradle tasks, or bash files).

## 6. Real Assets, Voice, and Secure Local State
*   **Voice Inputs:** Wire up Android's Speech Recognizer API to the microphone button inside `ProjectChatScreen.kt` to dictate voice prompts to the remote AI assistant.
*   **File Attachment Uploading:** Connect the attachment button to Android's document picker to allow developers to upload local files or reference assets directly to the remote workspace.
*   **Secure Local Storage:** Utilize Jetpack DataStore to encrypt and store historical session tokens, workspace paths, and successful connection details on-device.
