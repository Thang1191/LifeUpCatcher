package com.skibidi.lifeupcatcher

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import com.skibidi.lifeupcatcher.data.repository.AppGroupRepository
import com.skibidi.lifeupcatcher.data.repository.MonitoredItemRepository
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MyAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appGroupRepository: AppGroupRepository

    @Inject
    lateinit var monitoredItemRepository: MonitoredItemRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    @Volatile
    private var blockedPackagesCache: Map<String, String?> = emptyMap()

    private var lastForegroundPackage: String? = null
    private val lastAppliedPackageStates = mutableMapOf<String, Boolean>()
    private var workProfileUserHandle: UserHandle? = null

    private val homeActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.skibidi.lifeupcatcher.PERFORM_HOME_ACTION") {
                Log.d("MyAccessibilityService", "Received home action broadcast. Performing GLOBAL_ACTION_HOME.")
                performGlobalAction(GLOBAL_ACTION_HOME)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.skibidi.lifeupcatcher.CHECK_ENFORCE") {
            checkForegroundAndEnforce()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("MyAccessibilityService", "Service connected")

        val homeActionFilter = IntentFilter("com.skibidi.lifeupcatcher.PERFORM_HOME_ACTION")
        ContextCompat.registerReceiver(this, homeActionReceiver, homeActionFilter, ContextCompat.RECEIVER_EXPORTED)

        val workProfileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(this, workProfileStateReceiver, workProfileFilter, ContextCompat.RECEIVER_EXPORTED)

        serviceScope.launch {
            settingsRepository.isMonitoringEnabled.collectLatest { enabled ->
                if (enabled) {
                    startForegroundService()
                    if (workProfileUserHandle == null) findWorkProfileUserHandle()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }

        serviceScope.launch {
            settingsRepository.isShizukuEnabled.collectLatest { enabled ->
                if (enabled) grantQuietModePermission()
            }
        }

        serviceScope.launch {
            combine(
                appGroupRepository.allGroups,
                monitoredItemRepository.allItems
            ) { groups, items ->
                val currentDay = getCurrentDayOfWeek()

                // 1. Build Block Cache for "HOME" technique
                val activeGroupIds = items.filter { it.isActive && it.linkedGroupId != null && it.weekdayLimit.contains(currentDay) }
                    .map { it.linkedGroupId!! }
                    .toSet()

                val groupsMap = groups.associate { it.id to it.packageNames }
                val allowedPackages = activeGroupIds.flatMap { groupsMap[it] ?: emptySet() }.toSet()

                val newCache = mutableMapOf<String, String?>()
                items.filter { !it.isActive && it.linkedGroupId != null && it.blockingTechnique == "HOME" }.forEach { item ->
                    groupsMap[item.linkedGroupId!!]?.forEach { pkg ->
                        if (pkg !in allowedPackages && pkg !in newCache) {
                            newCache[pkg] = item.forceQuitMessage
                        }
                    }
                }
                blockedPackagesCache = newCache

                // 2. Enforce Shizuku Blocking
                if (settingsRepository.isShizukuEnabled.first()) {
                    handleAppDisabling(items, groupsMap, currentDay)
                    handleWorkProfile(items, currentDay)
                }
            }.collectLatest { }
        }
    }

    private fun handleAppDisabling(items: List<MonitoredItemEntity>, groupsMap: Map<String, Set<String>>, currentDay: String) {
        val activeGroupIds = items
            .filter { it.isActive && it.blockingTechnique == "DISABLE" && it.linkedGroupId != null && it.weekdayLimit.contains(currentDay) }
            .mapNotNull { it.linkedGroupId }
            .toSet()

        val managedPackages = items
            .filter { it.blockingTechnique == "DISABLE" && it.linkedGroupId != null }
            .flatMap { groupsMap[it.linkedGroupId] ?: emptySet() }
            .toSet()

        val packageToGroupsMap = mutableMapOf<String, MutableSet<String>>()
        groupsMap.forEach { (groupId, packages) ->
            packages.forEach { pkg -> packageToGroupsMap.getOrPut(pkg) { mutableSetOf() }.add(groupId) }
        }

        val commands = StringBuilder()
        val updates = mutableMapOf<String, Boolean>()

        for (pkg in managedPackages) {
            val shouldEnable = packageToGroupsMap[pkg]?.any { it in activeGroupIds } ?: false
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

    private fun handleWorkProfile(items: List<MonitoredItemEntity>, currentDay: String) {
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_QUIET_MODE") != PackageManager.PERMISSION_GRANTED) return

        if (workProfileUserHandle == null) {
            findWorkProfileUserHandle()
            if (workProfileUserHandle == null) return
        }

        val workProfileItem = items.find { it.blockingTechnique == "WORK_PROFILE" && it.weekdayLimit.contains(currentDay) }
        val shouldBeActive = workProfileItem?.isActive ?: false
        val desiredQuietMode = !shouldBeActive

        val userManager = getSystemService(USER_SERVICE) as UserManager
        if (userManager.isQuietModeEnabled(workProfileUserHandle!!) != desiredQuietMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                userManager.requestQuietModeEnabled(desiredQuietMode, workProfileUserHandle!!)
            }
        }
    }

    private fun findWorkProfileUserHandle() {
        val userManager = getSystemService(USER_SERVICE) as UserManager
        workProfileUserHandle = userManager.userProfiles.find { it.hashCode() != 0 }
    }

    private fun grantQuietModePermission() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_QUIET_MODE") != PackageManager.PERMISSION_GRANTED) {
            serviceScope.launch(Dispatchers.IO) {
                executeShizukuCommand("pm grant $packageName android.permission.MODIFY_QUIET_MODE")
            }
        }
    }

    private suspend fun checkWorkProfileAndEnforce() = withContext(Dispatchers.IO) {
        if (!settingsRepository.isMonitoringEnabled.first()) return@withContext

        val items = monitoredItemRepository.allItems.first()
        val currentDay = getCurrentDayOfWeek()
        val isWorkProfileItemActive = items.any { it.blockingTechnique == "WORK_PROFILE" && it.isActive && it.weekdayLimit.contains(currentDay)}

        if (!isQuietModeEnabled() && !isWorkProfileItemActive) {
            handleWorkProfile(items, currentDay)
            val relevantItem = items.find { it.blockingTechnique == "WORK_PROFILE" }
            val message = relevantItem?.forceQuitMessage ?: "Work profile disabled by LifeUp Catcher."
            withContext(Dispatchers.Main) { Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun isQuietModeEnabled(): Boolean {
        if (workProfileUserHandle == null) findWorkProfileUserHandle()
        if (workProfileUserHandle == null) return true
        return (getSystemService(USER_SERVICE) as UserManager).isQuietModeEnabled(workProfileUserHandle!!)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        serviceScope.launch {
            if (!settingsRepository.isMonitoringEnabled.first()) return@launch
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = event.packageName?.toString() ?: return@launch
                lastForegroundPackage = packageName
                checkAndEnforceRules(packageName)
            }
        }
    }

    private fun checkForegroundAndEnforce() {
        val currentPkg = lastForegroundPackage ?: return
        checkAndEnforceRules(currentPkg)
    }

    private fun checkAndEnforceRules(currentPackage: String) {
        val cache = blockedPackagesCache
        if (cache.containsKey(currentPackage)) {
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
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            process.waitFor()
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Shizuku command failed", e)
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

        ServiceCompat.startForeground(this, 1, notification, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun getCurrentDayOfWeek(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"; Calendar.TUESDAY -> "Tuesday"; Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"; Calendar.FRIDAY -> "Friday"; Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"; else -> ""
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(homeActionReceiver)
            unregisterReceiver(workProfileStateReceiver)
        } catch (_: Exception) {}
    }
}
