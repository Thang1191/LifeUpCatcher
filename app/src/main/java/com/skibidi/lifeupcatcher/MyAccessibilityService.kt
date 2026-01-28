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
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
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

    @Volatile
    private var groupsMap: Map<String, Set<String>> = emptyMap()

    @Volatile
    private var packageToGroupsMap: Map<String, Set<String>> = emptyMap()

    @Volatile
    private var blockedPackagesCache: Map<String, String?> = emptyMap()

    private var lastForegroundPackage: String? = null

    private val lastAppliedPackageStates = mutableMapOf<String, Boolean>()
    private var workProfileUserHandle: UserHandle? = null

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

                    ShopItemRepository.updateItemState(itemName, isStart)

                    rebuildBlockedCache()

                    if (currentItem != null) {
                        val message = if (isStart) currentItem.startMessage else currentItem.stopMessage
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    checkForegroundAndEnforce()
                } else {
                    Log.w("MyAccessibilityService", "Received broadcast $action but could not extract item name.")
                }
            }
        }
    }

    private val workProfileStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i("MyAccessibilityService", "Received work profile state change: ${intent.action}")
            serviceScope.launch {
                checkWorkProfileAndEnforce()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ShopItemRepository.initialize(applicationContext)
        Log.d("MyAccessibilityService", "Service connected")

        val countdownFilter = IntentFilter().apply {
            addAction("app.lifeup.item.countdown.start")
            addAction("app.lifeup.item.countdown.stop")
            addAction("app.lifeup.item.countdown.complete")
        }
        ContextCompat.registerReceiver(this, countdownReceiver, countdownFilter, ContextCompat.RECEIVER_EXPORTED)

        val workProfileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(this, workProfileStateReceiver, workProfileFilter, ContextCompat.RECEIVER_EXPORTED)

        serviceScope.launch {
            ShopItemRepository.isMonitoringEnabled.collectLatest { enabled ->
                if (enabled) {
                    startForegroundService()
                    if (workProfileUserHandle == null) {
                        findWorkProfileUserHandle()
                    }
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }

        serviceScope.launch {
            ShopItemRepository.isShizukuEnabled.collectLatest { enabled ->
                if (enabled) {
                    grantQuietModePermission()
                }
            }
        }

        serviceScope.launch {
            ShopItemRepository.items.collectLatest { items ->
                rebuildBlockedCache()
                enforceShizukuBlocking(items)
            }
        }

        val prefs = getSharedPreferences("app_picker_prefs", 0)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        serviceScope.launch {
            loadGroups(prefs)
        }
    }

    private suspend fun loadGroups(prefs: SharedPreferences) = withContext(Dispatchers.IO) {
        val jsonString = prefs.getString("app_groups", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(jsonString)
            val newGroupsMap = mutableMapOf<String, Set<String>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val packagesArray = obj.getJSONArray("packages")
                val packages = mutableSetOf<String>()
                for (j in 0 until packagesArray.length()) {
                    packages.add(packagesArray.getString(j))
                }
                newGroupsMap[id] = packages
            }
            groupsMap = newGroupsMap

            val newPackageToGroupsMap = mutableMapOf<String, MutableSet<String>>()
            newGroupsMap.forEach { (groupId, packages) ->
                packages.forEach { pkg ->
                    newPackageToGroupsMap.getOrPut(pkg) { mutableSetOf() }.add(groupId)
                }
            }
            packageToGroupsMap = newPackageToGroupsMap

            Log.d("MyAccessibilityService", "Loaded groups: $groupsMap")
            rebuildBlockedCache()
            enforceShizukuBlocking(ShopItemRepository.items.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun enforceShizukuBlocking(items: List<ShopItemState>) = withContext(Dispatchers.IO) {
        if (!ShopItemRepository.isShizukuEnabled.value) return@withContext

        handleAppDisabling(items)
        handleWorkProfile(items)
    }

    private fun handleAppDisabling(items: List<ShopItemState>) {
        val currentDay = getCurrentDayOfWeek()
        // Find all group IDs that are linked to an active "DISABLE" item.
        val activeGroupIds = items
            .filter { it.isActive && it.blockingTechnique == "DISABLE" && it.linkedGroupId != null && it.weekdayLimit.contains(currentDay) }
            .mapNotNull { it.linkedGroupId }
            .toSet()

        // Get all packages that are managed by any "DISABLE" item (active or inactive).
        val managedPackages = items
            .filter { it.blockingTechnique == "DISABLE" && it.linkedGroupId != null }
            .flatMap { groupsMap[it.linkedGroupId] ?: emptySet() }
            .toSet()

        val desiredStates = mutableMapOf<String, Boolean>()
        for (pkg in managedPackages) {
            val groupsForPkg = packageToGroupsMap[pkg] ?: emptySet()
            desiredStates[pkg] = groupsForPkg.any { it in activeGroupIds }
        }

        val commands = StringBuilder()
        val updates = mutableMapOf<String, Boolean>()

        for ((pkg, shouldEnable) in desiredStates) {
            if (lastAppliedPackageStates[pkg] != shouldEnable) {
                commands.append(if (shouldEnable) "pm enable $pkg;" else "pm disable-user --user 0 $pkg;")
                updates[pkg] = shouldEnable
            }
        }

        if (commands.isNotEmpty()) {
            Log.d("MyAccessibilityService", "Enforcing app state: $commands")
            executeShizukuCommand(commands.toString())
            lastAppliedPackageStates.putAll(updates)
        }
    }

    private fun handleWorkProfile(items: List<ShopItemState>) {
        val currentDay = getCurrentDayOfWeek()
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_QUIET_MODE") != PackageManager.PERMISSION_GRANTED) {
            Log.e("MyAccessibilityService", "MODIFY_QUIET_MODE permission not granted. Cannot control work profile.")
            return
        }

        if (workProfileUserHandle == null) {
            findWorkProfileUserHandle()
            if (workProfileUserHandle == null) {
                Log.e("MyAccessibilityService", "No work profile user handle found. Cannot toggle work profile.")
                return
            }
        }

        val workProfileItem = items.find { it.blockingTechnique == "WORK_PROFILE" && it.weekdayLimit.contains(currentDay) }
        val shouldBeActive = workProfileItem?.isActive ?: false
        val desiredQuietMode = !shouldBeActive

        val userManager = getSystemService(USER_SERVICE) as UserManager
        if (userManager.isQuietModeEnabled(workProfileUserHandle!!) != desiredQuietMode) {
            Log.i("MyAccessibilityService", "Requesting quiet mode for user ${workProfileUserHandle!!} to be $desiredQuietMode")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val result = userManager.requestQuietModeEnabled(desiredQuietMode, workProfileUserHandle!!)
                Log.i("MyAccessibilityService", "requestQuietModeEnabled result: $result")
            }
        }
    }

    private fun findWorkProfileUserHandle() {
        val userManager = getSystemService(USER_SERVICE) as UserManager
        workProfileUserHandle = userManager.userProfiles.find { user ->
            // A simple heuristic to find a work profile is to find a user that is not the primary user (id 0).
            // user.hashCode() on a UserHandle returns the user id.
            user.hashCode() != 0
        }

        if (workProfileUserHandle != null) {
            Log.i("MyAccessibilityService", "Found work profile UserHandle: $workProfileUserHandle")
        } else {
            Log.w("MyAccessibilityService", "Could not find a work profile UserHandle.")
        }
    }

    private fun grantQuietModePermission() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_QUIET_MODE") != PackageManager.PERMISSION_GRANTED) {
            serviceScope.launch(Dispatchers.IO) {
                Log.i("MyAccessibilityService", "Attempting to grant MODIFY_QUIET_MODE permission via Shizuku.")
                val command = "pm grant $packageName android.permission.MODIFY_QUIET_MODE"
                executeShizukuCommand(command)
            }
        }
    }

    private suspend fun checkWorkProfileAndEnforce() = withContext(Dispatchers.IO) {
        val currentDay = getCurrentDayOfWeek()
        if (!ShopItemRepository.isMonitoringEnabled.value) {
            Log.d("MyAccessibilityService", "Monitoring is disabled, skipping work profile check.")
            return@withContext
        }

        val isWorkProfileItemActive = ShopItemRepository.items.value.any { it.blockingTechnique == "WORK_PROFILE" && it.isActive && it.weekdayLimit.contains(currentDay)}
        val isQuietMode = isQuietModeEnabled()

        Log.d("MyAccessibilityService", "Work profile check: item active=$isWorkProfileItemActive, quiet mode=$isQuietMode")

        if (!isQuietMode && !isWorkProfileItemActive) {
            Log.i("MyAccessibilityService", "Work profile is ON without an active item. Forcing it OFF.")
            handleWorkProfile(ShopItemRepository.items.value)

            val relevantItem = ShopItemRepository.items.value.find { it.blockingTechnique == "WORK_PROFILE" }
            val message = relevantItem?.forceQuitMessage ?: "Work profile disabled by LifeUp Catcher."

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isQuietModeEnabled(): Boolean {
        if (workProfileUserHandle == null) {
            findWorkProfileUserHandle()
            if (workProfileUserHandle == null) {
                Log.e("MyAccessibilityService", "Cannot check quiet mode, no work profile user handle.")
                return true
            }
        }
        val userManager = getSystemService(USER_SERVICE) as UserManager
        val isQuiet = userManager.isQuietModeEnabled(workProfileUserHandle!!)
        Log.d("MyAccessibilityService", "Queried quiet mode for user $workProfileUserHandle, result: $isQuiet")
        return isQuiet
    }

    private fun rebuildBlockedCache() {
        val items = ShopItemRepository.items.value
        val currentDay = getCurrentDayOfWeek()

        // Get all group IDs linked to active items (any technique).
        val activeGroupIds = items.filter { it.isActive && it.linkedGroupId != null && it.weekdayLimit.contains(currentDay) }
            .map { it.linkedGroupId!! }
            .toSet()

        // Get all packages that belong to any active group.
        val allowedPackages = activeGroupIds.flatMap { groupId ->
            groupsMap[groupId] ?: emptySet()
        }.toSet()

        // Find inactive "HOME" items and build the block cache.
        val newCache = mutableMapOf<String, String?>()
        items.filter { !it.isActive && it.linkedGroupId != null && it.blockingTechnique == "HOME" }.forEach { item ->
            val groupId = item.linkedGroupId!!
            groupsMap[groupId]?.forEach { pkg ->
                // Block if not allowed by another active item.
                // The check for newCache.containsKey is to ensure the message from the first-encountered blocking item is used.
                if (pkg !in allowedPackages && pkg !in newCache) {
                    newCache[pkg] = item.forceQuitMessage
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
        val currentPkg = lastForegroundPackage ?: return
        checkAndEnforceRules(currentPkg)
    }

    private fun checkAndEnforceRules(currentPackage: String) {
        val cache = blockedPackagesCache
        if (cache.containsKey(currentPackage)) {
            Log.i("MyAccessibilityService", "Blocking $currentPackage with HOME action")
            performGlobalAction(GLOBAL_ACTION_HOME)

            val message = cache[currentPackage]
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeShizukuCommand(command: String) {
        if (!Shizuku.pingBinder()) return
        try {
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
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to execute Shizuku command: $command", e)
        }
    }

    private fun startForegroundService() {
        val channelId = "LifeUpCatcherService"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monitoring Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LifeUp Catcher Monitoring")
            .setContentText("Monitoring foreground apps and system states...")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .build()

        try {
            ServiceCompat.startForeground(
                this, 1, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to start foreground service", e)
        }
    }
    
    private fun getCurrentDayOfWeek(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"
            else -> ""
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(countdownReceiver)
            unregisterReceiver(workProfileStateReceiver)
        } catch (_: IllegalArgumentException) {}

        try {
            getSharedPreferences("app_picker_prefs", 0).unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
    }
}