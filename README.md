# OpenCode Remote Client for Android

OpenCode is a state-of-the-art native Android application acting as a secure remote control, dashboard, and development terminal for an OpenCode instance running on a laptop.

Designed for developers, designers, and prompt engineers, OpenCode streamlines the gap between natural language instruction and production-ready applications by providing remote monitoring and control over your local AI coding assistant workspace. This repository contains the complete Android application written in **Kotlin** and **Jetpack Compose**, fully adhering to **Material Design 3 (M3)** guidelines.

---

## 🎨 Visual Identity & Color Palette

The application uses an elegant, modern dark interface named the **Slate-Space Theme**. It uses high-contrast typography, crisp interactive boundaries, and spacious padding to establish focus and visual clarity.

- **Background:** `Color(0xFF171717)` (Deep Space Charcoal)
- **Surface:** `Color(0xFF1F1F1F)` (Slate Grey)
- **Surface Variant:** `Color(0xFF242424)` / `Color(0xFF2B2B2B)` (Elevated Card Surfaces)
- **Primary Accent:** `Color(0xFF4F7BFF)` (Electric Blue)
- **Secondary Accent:** `Color(0xFF8B5CF6)` (Royal Violet)
- **Tertiary Accent:** `Color(0xFFE95FD9)` (Vibrant Magenta)
- **Border:** `Color(0xFF343434)` (Crisp Separation Borders)

---

## 🚀 Key Functional Modules

### 💬 1. Project Chat (`ProjectChatScreen`)
The primary interface for interacting with OpenCode. Simulates real-time user input parsing, streaming responses, and direct chat bubbles.
*   **Context Cards:** Dedicated cards representing system actions (e.g., *"Replaced Steel with Alabaster"*).
*   **Build/Plan Action Menu:** Integrated dropdown controls to switch between "Build" (making changes directly) and "Plan" (discussing before building).
*   **Voice Prompting:** Includes a microphone action button mapping the dictation UI.

### 📁 2. Project Explorer (`RepositoryScreen`)
An interactive file tree explorer simulating real-time project navigation, optimized for reading source code natively.
*   **Tree Navigation:** Dynamic directory nodes with collapsible folders.
*   **Native Code View:** Simulated read-only code display for the selected file with proper typography scale.

### 🔍 3. AI Diff Review (`DiffReviewScreen`)
Instead of automatically modifying files, AI-generated changes are presented as patches.
*   **Line Changes:** View red/green line comparisons for pending changes.
*   **Approval Controls:** Dedicated Accept / Reject floating action buttons to merge AI work safely.

### 💻 4. Remote Terminal (`TerminalScreen`)
A simulated build and execution logs console displaying live compiler logs and step-by-step reasoning outputs.

### ⚡ 5. Running Tasks (`TasksScreen`)
Displays currently executing processes on the remote machine (e.g., node servers, docker containers, test runners).

### 🌳 6. Git Dashboard (`GitScreen`)
Provides a dashboard to sync and perform operations (Pull, Push, Fetch, Merge) with connected Git providers like GitHub and GitLab.

### 🧠 7. AI Activity Monitor (`ActivityScreen`)
Live visualization of the AI agent's internal workflow.
*   Tracks execution states such as *Thinking...*, *Reading*, *Searching*, and *Generating Patch*.

### 📝 8. System Logs (`LogsScreen`)
Chronological activity timeline to audit and monitor all historical connections and deployments.

### 🌐 9. Live Preview (`PreviewScreen`)
Integrated WebView to preview and interact with the running web application hosted by the remote workspace.

---

## 🛠️ Architecture & Tech Stack

The application relies on standard Android Jetpack architecture practices:
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material Design 3)
*   **Architecture Pattern:** MVVM (Model-View-ViewModel)
*   **State Distribution:** Kotlin Coroutines & `StateFlow`
*   **Navigation:** Jetpack Navigation Compose with string-based route keys
