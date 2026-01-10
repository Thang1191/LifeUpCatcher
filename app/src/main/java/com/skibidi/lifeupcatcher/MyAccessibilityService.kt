package com.skibidi.lifeupcatcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import rikka.shizuku.Shizuku

@SuppressLint("AccessibilityService")
class MyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    // Cache structures for O(1) lookups and to avoid frequent I/O
    @Volatile
    private var groupsMap: Map<String, Set<String>> = emptyMap()
    
    @Volatile
    private var blockedPackagesCache: Map<String, String?> = emptyMap()
    
    private var lastForegroundPackage: String? = null
    
    // Cache for Shizuku state to avoid redundant commands
    private val lastAppliedPackageStates = mutableMapOf<String, Boolean>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "app_groups") {
            serviceScope.launch {
                loadGroups(prefs)
            }
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
                    ShopItemRepository.updateItemState(itemName, isStart)
                    
                    // Rebuild cache immediately for UI responsiveness
                    rebuildBlockedCache()

                    if (currentItem != null) {
                        val message = if (isStart) currentItem.startMessage else currentItem.stopMessage
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Immediately re-check the current foreground app
                    checkForegroundAndEnforce()
                } else {
                     Log.w("MyAccessibilityService", "Received broadcast $action but could not extract item name.")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ShopItemRepository.initialize(applicationContext)
        Log.d("MyAccessibilityService", "Service connected")
        
        val filter = IntentFilter().apply {
            addAction("app.lifeup.item.countdown.start")
            addAction("app.lifeup.item.countdown.stop")
            addAction("app.lifeup.item.countdown.complete")
        }
        
        ContextCompat.registerReceiver(this, countdownReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Observe monitoring state
        serviceScope.launch {
            ShopItemRepository.isMonitoringEnabled.collectLatest { enabled ->
                if (enabled) {
                    startForegroundService()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        
        // Observe item changes
        serviceScope.launch {
            ShopItemRepository.items.collectLatest {
                rebuildBlockedCache()
                enforceShizukuBlocking()
            }
        }

        val prefs = getSharedPreferences("app_picker_prefs", 0)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        // Initial load
        serviceScope.launch {
            loadGroups(prefs)
        }
    }

    private suspend fun loadGroups(prefs: SharedPreferences) = withContext(Dispatchers.IO) {
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
    
    private suspend fun enforceShizukuBlocking() = withContext(Dispatchers.IO) {
        val items = ShopItemRepository.items.value
        val currentGroups = groupsMap // Read volatile
        
        // Map package -> shouldEnable (true = enable, false = disable)
        // Logic: If ANY item for a group is Active -> Enable.
        //        Else if ANY item for a group is Inactive (and DISABLE technique) -> Disable.
        val desiredStates = mutableMapOf<String, Boolean>()
        
        val managedPackages = mutableSetOf<String>()
        val allowedGroups = mutableSetOf<String>()
        
        for (item in items) {
            if (item.linkedGroupId != null) {
                if (item.isActive) {
                    allowedGroups.add(item.linkedGroupId)
                }
                if (item.blockingTechnique == "DISABLE") {
                    currentGroups[item.linkedGroupId]?.let { managedPackages.addAll(it) }
                }
            }
        }
        
        for (pkg in managedPackages) {
            // Find which groups contain this package
            val belongingGroups = currentGroups.filterValues { it.contains(pkg) }.keys
            // If any of the belonging groups is allowed, then Enable.
            val isAllowed = belongingGroups.any { allowedGroups.contains(it) }
            desiredStates[pkg] = isAllowed
        }

        val commands = StringBuilder()
        val updates = mutableMapOf<String, Boolean>()

        for ((pkg, shouldEnable) in desiredStates) {
            val lastState = lastAppliedPackageStates[pkg]
            if (lastState != shouldEnable) {
                val cmd = if (shouldEnable) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
                commands.append(cmd).append(";")
                updates[pkg] = shouldEnable
            }
        }
        
        if (commands.isNotEmpty()) {
            Log.d("MyAccessibilityService", "Enforcing Shizuku state: $commands")
            executeShizukuCommand(commands.toString())
            lastAppliedPackageStates.putAll(updates)
        }
    }

    private fun rebuildBlockedCache() {
        val items = ShopItemRepository.items.value
        val newCache = mutableMapOf<String, String?>()
        val currentGroups = groupsMap

        // 1. Identify all packages that should be allowed (whitelisted)
        // These are packages belonging to groups linked to currently ACTIVE items.
        // If a package is in an allowed group, it should NOT be blocked,
        // even if it is also in a blocked group.
        val allowedPackages = mutableSetOf<String>()
        
        for (item in items) {
             if (item.isActive && item.linkedGroupId != null) {
                 currentGroups[item.linkedGroupId]?.let {
                     allowedPackages.addAll(it)
                 }
             }
        }

        // 2. Build the block cache
        for (item in items) {
            // If item is inactive (timer not running), it may block apps via "HOME" technique
            if (!item.isActive && item.linkedGroupId != null && item.blockingTechnique == "HOME") {
                val targetPackages = currentGroups[item.linkedGroupId]
                if (targetPackages != null) {
                    for (pkg in targetPackages) {
                         // Only block if the package is NOT explicitly allowed by an active group
                         if (!allowedPackages.contains(pkg)) {
                             if (!newCache.containsKey(pkg)) {
                                 newCache[pkg] = item.forceQuitMessage
                             }
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
    
    private fun checkForegroundAndEnforce() {
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
            checkAndEnforceRules(currentPkg)
        }
    }

    private fun checkAndEnforceRules(currentPackage: String) {
        val cache = blockedPackagesCache // Atomic read
        if (cache.containsKey(currentPackage)) {
            Log.i("MyAccessibilityService", "Blocking $currentPackage")
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            val message = cache[currentPackage]
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
