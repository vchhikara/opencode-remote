# Replication Guide for AI Assistant

**Instructions for the AI:** You are tasked with recreating a mobile application based on this documentation package. You should use React Native with Expo SDK 54.

## Step 1: Project Initialization

1. Initialize a new Expo project with Expo Router:
   `npx create-expo-app@latest my-app --template tabs`
2. Update `app.json` with the configurations provided in `02-app-configuration.md`.
3. Enable the New Architecture in `app.json` (`newArchEnabled: true`).

## Step 2: Dependency Installation

Install the necessary packages:

```bash
npx expo install expo-router expo-apple-authentication react-native-iap expo-splash-screen @sentry/react-native @react-native-firebase/app @react-native-firebase/auth @react-native-firebase/messaging react-native-webview
```

## Step 3: Setup Navigation & Deep Linking

1. Configure `expo-router` to handle deep links for `lovable.dev` as defined in `03-features-and-routing.md`.
2. Scaffold the directories: `(auth)`, `(tabs)`, and the dynamic `project/[id].tsx` routes.

## Step 4: Implement Core Features

1. **Authentication**: Implement Firebase Auth in the `(auth)` group. Use Apple Sign-In and Google Sign-In.
2. **Chat UI**: In `project/[id].tsx`, build a chat interface. It must include a text input and a microphone button for dictation.
3. **Web Preview**: In the project screen, include a `WebView` that points to the live deployment URL of the user's project.
4. **Permissions**: Use `expo-image-picker` and `expo-av` to request camera and microphone permissions gracefully before allowing the user to attach photos or dictate prompts.

## Step 5: Modifying to a Different App

If the user requests a *different* app using this same framework, you can reuse this architecture:

- Keep Expo Router, Firebase Auth, Sentry, and IAP.
- Swap out the Chat/WebView UI for the new app's specific UI components.
- Maintain the static iOS/Android plugin configurations in `app.json` for robust native compilation.
