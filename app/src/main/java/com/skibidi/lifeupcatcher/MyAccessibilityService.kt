package com.skibidi.lifeupcatcher

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class MyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    // Cache structures for O(1) lookups and to avoid frequent I/O
    private var groupsMap: Map<String, Set<String>> = emptyMap()
    private var blockedPackagesCache: Map<String, String?> = emptyMap()
    private var lastForegroundPackage: String? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "app_groups") {
            loadGroups(prefs)
        }
    }

    private val countdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val action = it.action
                val itemName = it.getStringExtra("item") ?: it.getStringExtra("name") ?: it.getStringExtra("title")
                
                if (itemName != null) {
                    val isStart = action == "app.lifeup.item.countdown.start"
                    Log.d("MyAccessibilityService", "Received broadcast: $action for item: $itemName")
                    
                    val currentItem = ShopItemRepository.items.value.find { shopItem -> shopItem.name == itemName }
                    
                    // Update the state in the repository.
                    // This will trigger the collector in onServiceConnected, which will call enforceShizukuBlocking.
                    ShopItemRepository.updateItemState(itemName, isStart)
                    
                    // Force update cache immediately on the main thread (receiver runs on main)
                    // (Optional, as collect will do it too, but this makes UI response faster for Toast)
                    rebuildBlockedCache()

                    if (currentItem != null) {
                        val message = if (isStart) currentItem.startMessage else currentItem.stopMessage
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Immediately re-check the current foreground app
                    var currentPkg = lastForegroundPackage
                    try {
                        val root = rootInActiveWindow
                        if (root != null) {
                            root.packageName?.toString()?.let { pkg ->
                                currentPkg = pkg
                                lastForegroundPackage = pkg
                            }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                                @Suppress("DEPRECATION")
                                root.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MyAccessibilityService", "Error retrieving root window", e)
                    }

                    if (currentPkg != null) {
                        Log.d("MyAccessibilityService", "Re-checking rules for current package: $currentPkg")
                        checkAndEnforceRules(currentPkg)
                    }
                } else {
                     Log.w("MyAccessibilityService", "Received broadcast $action but could not extract item name.")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyAccessibilityService", "Service connected")
        
        val filter = IntentFilter().apply {
            addAction("app.lifeup.item.countdown.start")
            addAction("app.lifeup.item.countdown.stop")
            addAction("app.lifeup.item.countdown.complete")
        }
        
        ContextCompat.registerReceiver(this, countdownReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Observe monitoring state
        serviceScope.launch {
            ShopItemRepository.isMonitoringEnabled.collect { enabled ->
                if (enabled) {
                    startForegroundService()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        
        // Observe item changes to keep blocked cache up to date and enforce Shizuku rules
        serviceScope.launch(Dispatchers.Main) {
            ShopItemRepository.items.collect {
                rebuildBlockedCache()
                enforceShizukuBlocking()
            }
        }

        val prefs = getSharedPreferences("app_picker_prefs", 0)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadGroups(prefs)
    }

    private fun loadGroups(prefs: SharedPreferences) {
        val jsonString = prefs.getString("app_groups", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonString)
            val newMap = mutableMapOf<String, Set<String>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val packagesArray = obj.getJSONArray("packages")
                val packages = mutableSetOf<String>()
                for (j in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(j))
                }
                newMap[id] = packages
            }
            groupsMap = newMap
            Log.d("MyAccessibilityService", "Loaded groups: $groupsMap")
            rebuildBlockedCache()
            enforceShizukuBlocking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun enforceShizukuBlocking() {
        // Capture current state safely
        val items = ShopItemRepository.items.value
        val currentGroups = groupsMap // Read only map reference

        serviceScope.launch(Dispatchers.IO) {
            val commands = StringBuilder()
            
            for (item in items) {
                if (item.blockingTechnique == "DISABLE" && item.linkedGroupId != null) {
                    val packages = currentGroups[item.linkedGroupId]
                    if (packages != null) {
                        for (pkg in packages) {
                            val cmd = if (item.isActive) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
                            commands.append(cmd).append(";")
                        }
                    }
                }
            }
            
            if (commands.isNotEmpty()) {
                Log.d("MyAccessibilityService", "Enforcing Shizuku state: $commands")
                executeShizukuCommand(commands.toString())
            }
        }
    }

    /**
     * Rebuilds the set of blocked packages based on current items and groups.
     * Should be called on Main thread to avoid concurrency issues.
     */
    private fun rebuildBlockedCache() {
        val items = ShopItemRepository.items.value
        val newCache = mutableMapOf<String, String?>()
        
        for (item in items) {
            // If item is stopped (!isActive) and has a linked group, it blocks those apps
            // BUT ONLY if technique is HOME. If it's DISABLE, we don't enforce global home action.
            if (!item.isActive && item.linkedGroupId != null && item.blockingTechnique == "HOME") {
                val allowedPackages = groupsMap[item.linkedGroupId]
                if (allowedPackages != null) {
                    for (pkg in allowedPackages) {
                        // Avoid overwriting if a package is already blocked (first item priority)
                        if (!newCache.containsKey(pkg)) {
                            newCache[pkg] = item.forceQuitMessage
                        }
                    }
                }
            }
        }
        blockedPackagesCache = newCache
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ShopItemRepository.isMonitoringEnabled.value) return
        
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            lastForegroundPackage = packageName
            
            checkAndEnforceRules(packageName)
        }
    }

    private fun checkAndEnforceRules(currentPackage: String) {
        // Optimized O(1) lookup
        if (blockedPackagesCache.containsKey(currentPackage)) {
            Log.i("MyAccessibilityService", "Blocking $currentPackage")
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            val message = blockedPackagesCache[currentPackage]
            if (!message.isNullOrBlank()) {
                 Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun executeShizukuCommand(command: String) {
        try {
            if (Shizuku.pingBinder()) {
                // Use reflection because Shizuku.newProcess is private in newer versions
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", 
                    Array<String>::class.java, 
                    Array<String>::class.java, 
                    String::class.java
                )
                newProcessMethod.isAccessible = true
                val process = newProcessMethod.invoke(
                    null, 
                    arrayOf("sh", "-c", command), 
                    null, 
                    null
                ) as Process
                process.waitFor()
            } else {
                 Log.e("MyAccessibilityService", "Shizuku is not available.")
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to execute Shizuku command: $command", e)
        }
    }

    private fun startForegroundService() {
        val channelId = "LifeUpCatcherService"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LifeUp Catcher Monitoring")
            .setContentText("Monitoring foreground apps...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                 ServiceCompat.startForeground(
                     this, 
                     1, 
                     notification, 
                     ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                 )
            } else {
                 startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to start foreground service", e)
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(countdownReceiver)
        } catch (_: IllegalArgumentException) {}
        
        try {
             val prefs = getSharedPreferences("app_picker_prefs", 0)
             prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
    }
}
