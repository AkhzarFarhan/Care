# Care
An Android application for better parenting.
This application designed to comprehensively monitor device activity, running in the background as a foreground service.
## Features
1. **Browser URL Tracking**: Uses Android's `AccessibilityService` to capture URLs accessed in major browsers (e.g., Google Chrome).
2. **App Usage Tracking**: Uses `UsageStatsManager` to monitor which applications are opened and closed, recording their precise start and end times.
3. **Screen State Monitoring**: Tracks screen wake up (ON) and sleep (OFF) times.
4. **Device Lock/Unlock Monitoring**: Tracks when the device is unlocked by the user.
5. **Permission Management**: Provides a user-friendly UI to guide the user in granting specialized permissions like Usage Access and Accessibility.
## Technical Requirements
- **Minimum SDK**: Android 8.0 (API Level 26)
- **Language**: Kotlin
- **Architecture**: MVVM with Room Database for local data storage
## Required Permissions
The app requires the following sensitive permissions to function properly:
- `android.permission.PACKAGE_USAGE_STATS`: For tracking app usage start and end times.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: For reading URLs from browser address bars.
- `android.permission.FOREGROUND_SERVICE`: To run the monitoring service continuously in the background.
- `android.permission.RECEIVE_BOOT_COMPLETED`: To automatically start the monitor when the device boots up.
## Setup Instructions
1. Clone or download the repository.
2. Open the project in Android Studio.
3. Build and run the app on a physical Android device (emulators may not reflect accurate usage stats or browser behaviors).
4. Follow the in-app prompts to grant **Usage Access** and **Accessibility** permissions.
5. Start the monitoring service.
