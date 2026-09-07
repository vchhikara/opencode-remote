# Features, Routing & Deep Links

## Deep Linking Configuration
The app uses deep linking to handle routing seamlessly from the web to the mobile app.
It listens for the following URLs:
- `https://lovable.dev/`
- `https://lovable.dev/dashboard`
- `https://lovable.dev/projects`
- `https://lovable.dev/settings`
- `https://auth.lovable.dev`

## Expected Route Structure (Expo Router)
Based on the deep links and string extraction, an AI recreating this app should use `expo-router` with a file structure similar to this:

```
app/
├── _layout.tsx           # Main layout, Authentication provider wrapping
├── index.tsx             # Landing/Home screen
├── (auth)/               
│   ├── login.tsx         # Firebase / Apple / Google auth
│   └── signup.tsx
├── (tabs)/
│   ├── _layout.tsx       # Bottom tab navigation
│   ├── dashboard.tsx     # Maps to /dashboard
│   ├── projects.tsx      # Maps to /projects
│   └── settings.tsx      # Maps to /settings
└── project/
    └── [id].tsx          # Chat interface & WebView preview for a specific project
```

## Core UI Components to Replicate
1. **Chat Interface**: A message list view supporting text, tool invocations, and AI responses. Needs auto-scroll and markdown rendering.
2. **Voice Input Bar**: A microphone button that triggers the device's voice recognition API, transcribing speech to text for prompts.
3. **Project WebView**: A `react-native-webview` component that loads the generated web app for live previewing.
4. **IAP Paywall**: A modal utilizing `react-native-iap` to offer subscription upgrades.
