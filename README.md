# Care

Care is a native Android application installed on a parent-managed child's device for activity monitoring. It runs a disclosed foreground monitoring service, stores captured events locally with Room, and syncs five-minute aggregated activity batches to the configured Firebase project under a unique device ID.

## Features

1. **Browser URL tracking** through `AccessibilityService` for Chrome, Firefox, Edge, and Samsung Internet.
2. **App usage tracking** through `UsageStatsManager.queryEvents()`.
3. **Screen and unlock monitoring** for screen on, screen off, and user-present events.
4. **Local-first storage** with Room and per-event sync state.
5. **Firebase cloud sync** using aggregated event-type JSON batches in Realtime Database.
6. **First-launch permission onboarding** followed by automatic continuous monitoring.
7. **Permission tamper reporting** that uploads changes if required access is revoked while monitoring is running.
8. **Child-device status UI** that exposes the device identifier and permission health without activity browsing or export tools.

## Technical Requirements

- Minimum SDK: Android 8.0, API 26
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM-style ViewModel + repositories
- Local storage: Room
- Cloud: Firebase Realtime Database

## Firebase Setup

1. Create a Firebase project.
2. Add an Android app with package name `com.care`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json`.
5. Create a Firebase Realtime Database.
6. Deploy `database.rules.json` with `firebase deploy --only database`.

No parent sign-in or enrollment is required on the child's phone. Each installation creates a unique device ID and uploads below its Realtime Database node.

Captured data is held locally for at least five minutes, aggregated by event type, and written below:

- Device record: `/Care/{deviceId}/device`
- App usage batches: `/Care/{deviceId}/events/AppUsage/{batchId}`
- URL visit batches: `/Care/{deviceId}/events/Url/{batchId}`
- Screen-on batches: `/Care/{deviceId}/events/ScreenOn/{batchId}`
- Screen-off batches: `/Care/{deviceId}/events/ScreenOff/{batchId}`
- Unlock batches: `/Care/{deviceId}/events/Unlock/{batchId}`
- Permission-change batches: `/Care/{deviceId}/events/PermissionStatus/{batchId}`
- Current permission snapshot: `/Care/{deviceId}/permissionStatus/current`

The included Realtime Database rules allow public reads and writes for initial troubleshooting only. Replace them with restricted rules before using the application with real activity data.

The Gradle build applies the Google Services plugin only when `app/google-services.json` exists, so the project can compile before Firebase is configured.

## Required Permissions

- `android.permission.PACKAGE_USAGE_STATS`: app usage events.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: browser URL capture.
- `android.permission.FOREGROUND_SERVICE`: continuous monitoring.
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: foreground monitoring on newer Android versions.
- `android.permission.POST_NOTIFICATIONS`: foreground service notification on Android 13+.
- `android.permission.RECEIVE_BOOT_COMPLETED`: restart monitoring after reboot when previously enabled.
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: request reliable background monitoring.

## Setup Instructions

1. Open this folder in Android Studio.
2. Add `app/google-services.json` for Firebase sync.
3. Build and run on a physical Android device.
4. On first launch, complete the prompted Usage Access, Accessibility, notification, and battery optimization steps.

When required access is granted, monitoring starts automatically and continues in the foreground with an ongoing system notification.

If Usage Access, Accessibility, notification access, or the battery exemption changes while the foreground service is alive, Care records the new state locally and uploads it in the next eligible five-minute batch. Android does not allow an app to report after it is force-stopped or uninstalled.

Physical devices are recommended because usage stats, accessibility events, and browser behavior are often incomplete on emulators.
