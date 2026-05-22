---
name: create-android-activity-monitor
description: A comprehensive guide and skill for an agent to build an Android Activity Monitor app tracking URLs, app usage, and screen states.
---
# Android Activity Monitor Implementation Guide
This skill provides step-by-step instructions for an agent to build the Android Activity Monitor application.
## Prerequisites
- Android SDK installed.
- Kotlin and Gradle configured.
## Step 1: Project Initialization
1. Create a new Android project with an Empty Activity using Kotlin.
2. Set the `minSdk` to 26 and `targetSdk` to 34 (or latest).
3. Add dependencies for Room (local database), Kotlin Coroutines, and Lifecycle components in `build.gradle.kts`.
## Step 2: Define Permissions in Manifest
Add the following permissions to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```
## Step 3: Implement Local Storage (Room Database)
1. Create an Entity `ActivityEvent` with fields: `id`, `eventType` (URL, APP_USAGE, SCREEN, LOCK), `packageName`, `details`, `timestamp`.
2. Create a DAO `ActivityEventDao` for inserting and querying events.
3. Create the Room Database class `AppDatabase`.
## Step 4: Screen and Lock Monitoring
1. Create a `BroadcastReceiver` named `DeviceStateReceiver`.
2. Register it dynamically in a Foreground Service for the following actions:
   - `Intent.ACTION_SCREEN_ON` (Screen Wake up)
   - `Intent.ACTION_SCREEN_OFF` (Screen Stop/Sleep)
   - `Intent.ACTION_USER_PRESENT` (Device Unlock)
3. Upon receiving an intent, log the event to the Room database.
## Step 5: App Usage Tracking
1. Implement a tracking mechanism using `UsageStatsManager`.
2. Create a Coroutine in the Foreground Service that polls `UsageStatsManager.queryEvents()` periodically (e.g., every 5 seconds).
3. Track `UsageEvents.Event.ACTIVITY_RESUMED` (start time) and `UsageEvents.Event.ACTIVITY_PAUSED` / `UsageEvents.Event.ACTIVITY_STOPPED` (end time).
4. Save the calculated app usage sessions (Package name, Start time, End time) to the database.
## Step 6: Browser URL Tracking via AccessibilityService
1. Create a class `UrlMonitorAccessibilityService` extending `AccessibilityService`.
2. Register it in the Manifest with `BIND_ACCESSIBILITY_SERVICE` permission and an XML configuration file.
3. In the XML configuration, specify `android:accessibilityEventTypes="typeWindowContentChanged"` and `android:packageNames="com.android.chrome,org.mozilla.firefox"`.
4. Override `onAccessibilityEvent(event: AccessibilityEvent)`.
5. Check if the event source has a view ID matching the browser's URL bar (e.g., `"com.android.chrome:id/url_bar"`).
6. Extract the text (URL) and save it to the Room database if it has changed from the previously recorded URL.
## Step 7: Foreground Service
1. Create a `MonitorService` extending `Service`.
2. Implement `onCreate()` to start the service in the foreground with an ongoing Notification.
3. Initialize the `DeviceStateReceiver` and start the App Usage polling coroutine.
4. Provide a way to stop the service gracefully.
## Step 8: User Interface and Permission Handling
1. In `MainActivity`, check if the required permissions are granted:
   - Usage Access: `AppOpsManager.checkOpNoThrow()` for `OPSTR_GET_USAGE_STATS`.
   - Accessibility: Check if `UrlMonitorAccessibilityService` is enabled in Settings.
2. Display buttons to direct the user to the respective Android Settings pages to grant these permissions:
   - `startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))`
   - `startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))`
3. Add a button to Start/Stop the `MonitorService`.
4. (Optional) Display a simple RecyclerView to show the recent logs from the Room database.
## Critical Considerations
- **Battery Optimization**: Polling `UsageStatsManager` can drain battery. Optimize the polling interval or consider relying entirely on `AccessibilityService` for app switching detection.
- **Privacy**: This app collects highly sensitive data. Ensure data is stored securely locally and not transmitted without explicit user consent.
