# OpenCode Project Roadmap

This document outlines the progress made so far and the planned phases for the completion of the OpenCode Android Application.

## ✅ Foundation & UI Mockups
- [x] **Project Initialization**: Set up the Android project using Jetpack Compose, Hilt for Dependency Injection, and Navigation Compose.
- [x] **Theme & Design System**: Implement the core design system matching the Lovable aesthetics (custom colors, typography, and reusable components).
- [x] **Authentication UI (`LoginScreen`)**: Build the connection interface for users to enter their local IDE's IP address.
- [x] **Dashboard UI (`DashboardScreen`)**: Create the main dashboard with tab navigation (Projects, Settings, Design System).
- [x] **Project Ideation UI (`ProjectScreen`)**: Build the chat interface for interacting with the AI, including the floating input pill and chat bubbles.
- [x] **Connectors UI (`ConnectorsScreen`)**: Implement the integrations list with search, category filtering, and toggle switches for various services (Supabase, Firebase, GitHub, etc.).

## 🔄 Current State
- [x] The application currently functions as a static frontend shell with mock data. 
- [x] Roll back the experimental local database and network API integrations to allow for a clean, structured implementation phase.

## 🚀 Upcoming Phases (Implementation)

### Phase 1: Local Networking & API Integration
- [ ] Integrate Ktor client for HTTP and WebSocket communication.
- [ ] Implement the health check ping to verify the IDE IP address during login.
- [ ] Set up WebSocket listeners for real-time AI streaming and IDE status updates.

### Phase 2: Local Persistence
- [ ] Set up Room Database for offline caching.
- [ ] Create DAOs and Entities for `Projects`, `Messages`, and `Connectors`.
- [ ] Wire up local persistence to ensure chat history and connector states survive app restarts.

### Phase 3: State Management & ViewModels
- [ ] Implement Repository layer to act as the single source of truth (mediating between Room DB and Ktor API).
- [ ] Upgrade all ViewModels (`LoginViewModel`, `DashboardViewModel`, `ProjectViewModel`, `ConnectorsViewModel`) to consume flows from repositories.
- [ ] Replace all mock data in the Jetpack Compose screens with reactive `StateFlow` collections.

### Phase 4: Polish & Device Testing
- [ ] Conduct end-to-end testing on a physical device.
- [ ] Ensure the app correctly falls back and shows error states when the local IDE backend is unreachable.
- [ ] Refine animations, keyboard handling (`imePadding`), and system bar padding for edge-to-edge support.
- [ ] Final code review and performance profiling (removing over-engineering, optimizing recompositions).

### Phase 5: Production Launch Preparation
- [ ] Generate signed APK/App Bundle.
- [ ] Ensure ProGuard/R8 rules are correctly configured for Ktor and Room.
- [ ] Final deployment.
