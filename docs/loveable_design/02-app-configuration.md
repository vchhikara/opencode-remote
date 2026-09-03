# App Configuration (app.json / app.config.js)

When replicating this app, the Expo configuration is critical. Below are the key settings extracted from the original `app.config`.

## General Settings
- **Name**: Lovable
- **Slug**: lovable
- **Scheme**: `exp+lovable`, `lovable`
- **User Interface Style**: automatic (supports light/dark mode)
- **New Architecture**: Enabled (`newArchEnabled: true`)

## Expo Plugins
To replicate the native capabilities, include these plugins in the Expo config:
```json
"plugins": [
  "expo-apple-authentication",
  "expo-router",
  "react-native-iap",
  ["expo-splash-screen", {
    "image": "./assets/images/splash-icon.png",
    "backgroundColor": "#ffffff",
    "dark": {
      "image": "./assets/images/splash-icon-dark.png",
      "backgroundColor": "#0D0D0D"
    }
  }],
  ["@sentry/react-native/expo", {
    "url": "https://sentry.io/",
    "project": "react-native",
    "organization": "lovable"
  }],
  "@react-native-firebase/app",
  "@react-native-firebase/auth",
  "@react-native-firebase/messaging",
  ["expo-build-properties", {
    "ios": {
      "useFrameworks": "static",
      "forceStaticLinking": ["RNFBApp", "RNFBAuth", "RNFBFirestore", "RNFBMessaging"]
    },
    "android": {
      "kotlinVersion": "2.2.0",
      "minSdkVersion": 28
    }
  }]
]
```

## Platform Specifics

### iOS
- **Bundle Identifier**: `dev.lovable.build`
- **Uses Apple Sign In**: true
- **Info.plist Requirements**:
  - `NSMicrophoneUsageDescription`: "Lovable needs microphone access so you can dictate prompts and chat with AI using your voice."
  - `NSCameraUsageDescription`: "Lovable needs camera access so you can take photos to include in your prompts."
  - `NSUserTrackingUsageDescription`: "This allows Lovable to provide personalized content and measure advertising effectiveness."
  - `UIBackgroundModes`: ["remote-notification"]

### Android
- **Package**: `dev.lovable.build`
- **Permissions Required**: 
  - `android.permission.CAMERA`
  - `android.permission.RECORD_AUDIO`
  - `android.permission.MODIFY_AUDIO_SETTINGS`
  - `android.permission.POST_NOTIFICATIONS`
  - `com.android.vending.BILLING` (For IAP)
