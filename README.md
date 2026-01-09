## ⚠️ WARNING: THIS APP IS ENTIRELY VIBE CODED. I MADE THEM FOR MY PERSONAL USE CASES. YOU CAN FILE BUG REPORTS AND FEATURE REQUESTS, BUT I WILL NOT READ THEM.


# LifeUpCatcher

**LifeUpCatcher** is a powerful companion application for [LifeUp](https://play.google.com/store/apps/details?id=net.sarasarasa.lifeup), a gamified to-do list app. It acts as an **enforcement bridge**, allowing you to physically restrict access to distractions (apps/games) on your phone unless you have "purchased" time for them using your LifeUp coins.

> **Concept**: Create a "Shop Item" in LifeUp (e.g., "1 Hour Gaming"). When you buy it, LifeUpCatcher unlocks your games. When the time expires, LifeUpCatcher immediately kicks you out of those games.

## ✨ Features

*   **Real-time Blocking**: Uses Android Accessibility Services to monitor the foreground app and block it instantly if unauthorized.
*   **LifeUp Integration**: Listens for standard broadcasts sent by LifeUp when items are used or timers expire.
*   **App Grouping**: Create custom groups of apps (e.g., "Social Media", "Games") to manage them easily.
*   **High Performance**:
    *   **O(1) Enforcement**: Uses optimized caching maps to check rules instantly without battery drain.
    *   **Reactive Architecture**: Updates rules immediately when LifeUp sends a signal, kicking you out of a restricted app the millisecond the timer ends.
*   **Customizable Messages**: Set custom Toast messages for start, stop, and force-quit events.

## 🚀 How It Works

1.  **LifeUp Trigger**: You use an item in LifeUp. LifeUp sends a system broadcast (intent).
2.  **Reception**: LifeUpCatcher's `MyAccessibilityService` receives this broadcast.
3.  **State Update**: The service updates the state of the item (Active/Inactive) and immediately rebuilds its enforcement cache.
4.  **Enforcement**:
    *   The service checks the currently active window.
    *   If you are currently in an app that is now restricted (because the timer stopped), it performs a **Global Home Action**, forcing you to the home screen.
    *   If you try to open the app again, it checks the package name against the blocked cache and kicks you out again.

## 🛠️ Technical Architecture

The project follows modern Android development practices using **Kotlin**, **Jetpack Compose**, and **Coroutines**.

### Core Components

*   **`ShopItemRepository`**: A singleton repository holding the state of all shop items. It uses `StateFlow` to provide reactive updates to the UI and the Background Service.
*   **`MyAccessibilityService`**: The heart of the application.
    *   **Service Type**: `foregroundServiceType="specialUse"` (Android 14+ compliant).
    *   **Optimization**: Maintains a `blockedPackagesCache` (Map<PackageName, Message>) to avoid iterating through lists or reading files during high-frequency window state changes.
    *   **Immediate Reaction**: Uses `rootInActiveWindow` to enforce rules immediately upon receiving a broadcast, rather than waiting for the next user interaction.
*   **`AppPickerViewModel`**: Manages the UI for selecting installed applications using `PackageManager`.

### Broadcast Integration

The app listens for the following actions:
*   `app.lifeup.item.countdown.start`: Item purchased/started.
*   `app.lifeup.item.countdown.stop`: Item manually stopped.
*   `app.lifeup.item.countdown.complete`: Timer finished naturally.

## 📦 Installation & Setup

Since this app requires deep system integration (`QUERY_ALL_PACKAGES` and Accessibility), it is designed to be sideloaded.

1.  **Install the APK** on your device (build it yourself).
2.  **Open LifeUpCatcher**.
3.  **Grant Permissions**:
    *   Allow **Notification** permission (for the foreground service).
    *   Enable **Accessibility Service** in Android Settings when prompted.
4.  **Configuration**:
    *   **Create a Group**: Go to the "Apps" tab, create a group (e.g., "Distractions"), and select the apps you want to block.
    *   **Create an Item**: Go to the "Activity" tab, add a new item. **The name must match your LifeUp Shop Item exactly.**
    *   **Link**: Link the Item to the App Group.
5.  **Enable Monitoring**: Toggle the switch at the top to "ON".

## 🎮 LifeUp Configuration

1.  Open **LifeUp**.
2.  Go to **Shop** -> Add Item.
3.  Set **Effects** -> **Process** -> **Broadcast**.
    *   Ensure the item name matches what you entered in LifeUpCatcher.
4.  (Optional) Set a **Countdown** (e.g., 30 minutes).
5.  **Enjoy!** Buying the item unlocks the apps. When the countdown ends, they are locked again.

## 📝 Permissions

*   `BIND_ACCESSIBILITY_SERVICE`: To detect the current app and perform the "Home" action.
*   `QUERY_ALL_PACKAGES`: To list installed apps for grouping.
*   `FOREGROUND_SERVICE`: To keep the enforcement active in the background.
*   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: To prevent the system from killing the enforcement service.
