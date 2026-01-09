# LifeUpCatcher

LifeUpCatcher is an Android utility application designed to integrate with the [LifeUp](https://play.google.com/store/apps/details?id=net.sarasarasa.lifeup) gamification to-do list app. It functions as an external enforcement module that restricts access to specific application groups based on the state of "Shop Items" within LifeUp.

The application leverages Android's **Accessibility Services** to monitor foreground activities and **Broadcast Receivers** to synchronize state with LifeUp.

## Architecture & Core Components

### 1. Shop Item Management (`ShopItemRepository`)
The application maintains a reactive state of "Shop Items" using a Singleton Repository pattern with Kotlin `StateFlow`.

-   **Data Model**: `ShopItemState`
    -   `name`: The unique identifier matching the LifeUp Shop Item name.
    -   `isActive`: Boolean flag indicating if the item is currently "running" (purchased/active in LifeUp).
    -   `linkedGroupId`: Foreign key reference to an `AppGroup`.
    -   `startMessage` / `stopMessage` / `forceQuitMessage`: Custom feedback strings for Toast notifications.
-   **State Synchronization**: The repository is updated via Broadcasts received by the Accessibility Service.

### 2. App Group Management (`AppPickerViewModel`)
Users can categorize installed applications into named groups (e.g., "Distractions", "Games").
-   **Implementation**:
    -   Uses `PackageManager` with `QUERY_ALL_PACKAGES` permission to retrieve installed applications.
    -   Persistence: Groups are serialized to JSON and stored in `SharedPreferences` under the key `app_groups`.
    -   **UI**: Built with Jetpack Compose (`LazyColumn`, `Card`), allowing selection and grouping of apps.

### 3. LifeUp Integration (Broadcast Receiver)
The app listens for specific standard broadcasts emitted by LifeUp when a Shop Item is used or expires.

-   **Intents**:
    -   `app.lifeup.item.countdown.start`: Signals the item is active.
    -   `app.lifeup.item.countdown.stop`: Signals the item has expired or stopped.
-   **Payload Extraction**: The receiver extracts the item name from the Intent extras (keys: `"item"`, `"name"`, or `"title"`).

### 4. Enforcement Engine (`MyAccessibilityService`)
This is the core background service that enforces rules. It extends `AccessibilityService`.

-   **Foreground Monitoring**:
    -   Subscribes to `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`.
    -   Extracts the `packageName` from the event source.
-   **Rule Logic**:
    1.  Iterates through all `ShopItemState` objects in the repository.
    2.  Checks if an item is **inactive** (`isActive == false`) and has a valid `linkedGroupId`.
    3.  Resolves the allowed packages for that group.
    4.  If the current foreground package is found in the restricted group, the service triggers `performGlobalAction(GLOBAL_ACTION_HOME)`.
-   **Lifecycle**:
    -   Runs as a **Foreground Service** (`ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`) to ensure high priority and process persistence.
    -   Displays a persistent notification when monitoring is enabled.

## Permissions & Manifest

The application requires high-level privileges to function correctly:

-   `android.permission.BIND_ACCESSIBILITY_SERVICE`: Required to monitor screen content and perform global actions (Home).
-   `android.permission.QUERY_ALL_PACKAGES`: Required to list all installed applications for group creation.
-   `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Required for Android 14+ compliance to run the continuous monitoring service.
-   `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Requested at runtime to prevent the system from killing the listener in Doze mode.
-   `android.permission.POST_NOTIFICATIONS`: For the foreground service status notification.

## Technical Setup Guide

1.  **Build**: Project uses Gradle with Kotlin DSL. Requires Android SDK 34+.
2.  **Installation**: Sideload the APK (Google Play may reject `QUERY_ALL_PACKAGES` and Accessibility usage without specific justification).
3.  **Initialization**:
    -   Launch App.
    -   Grant **Accessibility Permissions** when prompted (Settings -> Accessibility -> Downloaded Apps -> LifeUpCatcher).
    -   Create an **App Group** in the "Apps" tab.
    -   Create a **Shop Item** receiver in the "Activity" tab with a name matching your LifeUp item.
    -   Link the Shop Item to the App Group.
    -   Toggle "Monitoring Service" ON.

## Usage with LifeUp

1.  In LifeUp, create a Shop Item with "Process" effects.
2.  Ensure the "Broadcast" feature is enabled in LifeUp settings.
3.  When you **Use** the item in LifeUp -> LifeUpCatcher receives `start` -> `isActive = true` -> Apps in the group are allowed.
4.  When the item **Expires** -> LifeUpCatcher receives `stop` -> `isActive = false` -> Apps in the group are blocked (Force Quit).
