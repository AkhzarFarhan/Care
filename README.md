# Care

Care is a native Android application for parent-managed device activity monitoring. It runs a foreground monitoring service, stores captured events locally with Room, and can sync raw activity events to Firebase Auth + Cloud Firestore under a parent account.

## Features

1. **Browser URL tracking** through `AccessibilityService` for Chrome, Firefox, Edge, and Samsung Internet.
2. **App usage tracking** through `UsageStatsManager.queryEvents()`.
3. **Screen and unlock monitoring** for screen on, screen off, and user-present events.
4. **Local-first storage** with Room and per-event sync state.
5. **Firebase cloud sync** using parent-owned devices in Firestore.
6. **Modern Compose UI** with dashboard, permissions, device setup, logs, export, and settings.
7. **CSV/JSON export** through Android's Storage Access Framework.

## Technical Requirements

- Minimum SDK: Android 8.0, API 26
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM-style ViewModel + repositories
- Local storage: Room
- Cloud: Firebase Auth + Cloud Firestore

## Firebase Setup

1. Create a Firebase project.
2. Add an Android app with package name `com.care`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json`.
5. Enable Email/Password sign-in in Firebase Auth.
6. Create a Cloud Firestore database.
7. Deploy `firestore.rules`.

The Gradle build applies the Google Services plugin only when `app/google-services.json` exists, so the project can compile before Firebase is configured.

## Required Permissions

- `android.permission.PACKAGE_USAGE_STATS`: app usage events.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: browser URL capture.
- `android.permission.FOREGROUND_SERVICE`: continuous monitoring.
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: foreground monitoring on newer Android versions.
- `android.permission.POST_NOTIFICATIONS`: foreground service notification on Android 13+.
- `android.permission.RECEIVE_BOOT_COMPLETED`: restart monitoring after reboot when previously enabled.

## Setup Instructions

1. Open this folder in Android Studio.
2. Add `app/google-services.json` for Firebase sync.
3. Build and run on a physical Android device.
4. In the app, grant Usage Access, Accessibility, and notification access.
5. Sign in or create the parent account.
6. Start monitoring from the Dashboard.

Physical devices are recommended because usage stats, accessibility events, and browser behavior are often incomplete on emulators.
