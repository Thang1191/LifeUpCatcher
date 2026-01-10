# ⚠️ Disclaimer: THIS APP IS ENTIRELY VIBE CODED AND IS FOR MY PERSONAL USE. YOU CAN REPORT ISSUES OR REQUEST FEATURES BUT I'LL PROBABLY WON'T READ THEM.
# LifeUpCatcher

**LifeUpCatcher** is a companion app for [LifeUp](https://play.google.com/store/apps/details?id=net.sarasarasa.lifeup) that lets you use your hard-earned LifeUp coins to "buy" screen time for distracting apps and games.

It acts as a bridge between LifeUp and your device, allowing you to block or unblock apps based on items you purchase in the LifeUp shop.

> **The Concept**: You create a "Shop Item" in LifeUp, for example, "1 Hour of Gaming". When you buy that item, LifeUpCatcher automatically unblocks your games. When the time is up, it locks them again.

## ✨ Features

*   **App Groups**: Bundle apps together (e.g., "Social Media", "Games") to control them all with a single LifeUp item.
*   **Two Blocking Techniques**:
    *   **Accessibility Service**: Instantly blocks apps by navigating to the home screen the moment your purchased time runs out.
    *   **Shizuku/Root Integration**: For more powerful control, completely disable apps, preventing them from being opened at all.
*   **Seamless LifeUp Integration**: Listens for signals from LifeUp to automatically start or stop access to your apps.
*   **Custom Toast Messages**: Set your own custom alert messages for when app time starts, stops, or when an app is blocked.
*   **Efficient & Battery-Friendly**: Designed to be fast and consume minimal battery (maybe).

## 🚀 How It Works

1.  **Purchase an Item in LifeUp**: When you use a connected shop item in LifeUp, it sends a signal (broadcast).
2.  **LifeUpCatcher Responds**: The app's monitoring service catches this signal and activates the rules for that item.
3.  **Access Granted**: The apps in the linked group are now unblocked.
4.  **Time's Up!**: When the timer on your LifeUp item expires, the apps are instantly blocked again using your chosen method.

## 🛠️ Setup Guide

### 1. Build and Install
Build the project in Android Studio and install the APK on your phone.

### 2. Grant Core Permissions
*   Open LifeUpCatcher and go to the **Activity** tab.
*   You will be prompted to enable the **Accessibility Service**. This is required for the basic blocking feature.
*   Toggle the **Monitoring Service** switch to "ON".
*   You will be asked for **Notification** permission. This is necessary to keep the service running in the background.
*   You may also be asked to **disable battery optimizations**. This is crucial to prevent the OS from shutting down the monitoring service.

### 3. Create an App Group
*   Go to the **Apps** tab.
*   Tap the **+** button to create a new group (e.g., "Distracting Apps").
*   Select all the apps you want to include in this group and save it.

### 4. Link a LifeUp Shop Item
*   Go back to the **Activity** tab.
*   Tap the **+** button to add a new monitored item.
*   **Item Name**: Enter a name that is **exactly the same** as the item you will create in LifeUp.
*   **Group**: Select the App Group you created in the previous step.
*   **Blocking Technique**:
    *   `Global Action Home` is the standard method and works for most cases.
    *   `Disable Apps` is a more powerful option but requires Shizuku or Root access (see Step 5).
*   **Toast Messages (Optional)**: You can set custom messages that will appear when the item is used, when it expires, or when an app is blocked.
*   Save the item.

### 5. (Optional) Configure Shizuku
For the `Disable Apps` blocking technique, you need to grant permissions via Shizuku.

*   [Install and run Shizuku](https://shizuku.rikka.app/guide/setup/) on your device.
*   In LifeUpCatcher, go to the **Activity** tab.
*   Enable the **Shizuku Integration** switch.
*   Grant permission to LifeUpCatcher when prompted by Shizuku.
*   You can now select the `Disable Apps` technique when creating or editing a monitored item.

### 6. Configure LifeUp
*   Open LifeUp and go to the **Shop**.
*   Create a new shop item. Give it the **exact same name** as you did in LifeUpCatcher.
*   Under "Effects", choose **Custom Effects** -> **Countdown Timer**.
*   Make sure to enable **Broadcast events** in **Settings** -> **Labs** -> **Developer Mode**.

Now, whenever you buy your item in LifeUp, your selected apps will be unblocked for the duration of the countdown!

## 📝 Why These Permissions?

*   `BIND_ACCESSIBILITY_SERVICE`: To see the currently running app and return to the home screen to block access.
*   `QUERY_ALL_PACKAGES`: To show you a list of all your installed apps so you can choose which ones to block.
*   `POST_NOTIFICATIONS`: To show a persistent notification, which is required by Android to keep the background monitoring service alive.
*   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: To ensure the operating system doesn't shut down the service to save battery.
