# Architecture Overview: Lovable Mobile Companion App

## Purpose
This app is a mobile companion to the **Lovable.dev** AI coding platform. It allows users to interact with the Lovable AI, dictate prompts via voice, attach photos, and manage/preview their projects on the go.

## Technology Stack
The app is built using modern cross-platform technologies:
- **Framework**: React Native
- **Toolkit**: Expo (SDK 54.0.0)
- **Routing**: Expo Router (`expo-router`)
- **Backend / Auth**: Firebase & Supabase
- **Error Tracking**: Sentry (`@sentry/react-native`)
- **In-App Purchases**: React Native IAP (`react-native-iap`)

## Core Workflows
1. **AI Chat & Prompting**: Core interface for sending prompts and chatting with AI.
2. **Media & Voice**: Integrates device microphone for voice dictation and camera for image uploads into prompts.
3. **Project Previewing**: Uses WebViews (`react-native-webview`) to render and interact with the user's generated web projects natively on the mobile device.
4. **Authentication**: Uses Firebase Authentication and Google/Apple Sign-In. Deeply integrates with the Lovable web session.

## System Architecture
As an Expo application, the UI and business logic are entirely written in React Native (JavaScript/TypeScript). 
It relies heavily on Expo plugins (`app.config` / `app.json`) to manage native Android/iOS permissions (Camera, Microphone, Notifications) rather than manually modifying native code. 
The app communicates with the primary Lovable backend API (`api.lovable.dev`) via REST/GraphQL.
