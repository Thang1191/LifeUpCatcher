package com.skibidi.lifeupcatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import com.skibidi.lifeupcatcher.data.repository.AppGroupRepository
import com.skibidi.lifeupcatcher.data.repository.DnsRepository
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
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class MainService : Service() {

    @Inject
    lateinit var appGroupRepository: AppGroupRepository

    @Inject
    lateinit var monitoredItemRepository: MonitoredItemRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var dnsRepository: DnsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var workProfileUserHandle: UserHandle? = null
    private val lastAppliedPackageStates = mutableMapOf<String, Boolean>()

    private val dnsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            serviceScope.launch {
                if (dnsRepository.isDnsLockingEnabled.first()) {
                    checkAndEnforceDns()
                }
            }
        }
    }

    private val workProfileStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            serviceScope.launch {
                checkWorkProfileAndEnforce()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MainService", "Service created")
        startForegroundService()

        contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, dnsObserver)
        
        val workProfileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(this, workProfileStateReceiver, workProfileFilter, ContextCompat.RECEIVER_EXPORTED)

        serviceScope.launch {
            if (workProfileUserHandle == null) findWorkProfileUserHandle()
            
            combine(
                appGroupRepository.allGroups,
                monitoredItemRepository.allItems,
                settingsRepository.isShizukuEnabled
            ) { groups, items, shizukuEnabled ->
                if (shizukuEnabled) {
                    val currentDay = getCurrentDayOfWeek()
                    val groupsMap = groups.associate { it.id to it.packageNames }
                    
                    handleAppDisabling(items, groupsMap, currentDay)
                    handleWorkProfile(items, currentDay)
                }
            }.collectLatest { }
        }

        serviceScope.launch {
            dnsRepository.dnsHostname.collectLatest { hostname ->
                if (hostname.isNotBlank() && dnsRepository.isDnsLockingEnabled.first()) {
                    checkAndEnforceDns()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.skibidi.lifeupcatcher.CHECK_ENFORCE") {
            serviceScope.launch {
                val groups = appGroupRepository.allGroups.first()
                val items = monitoredItemRepository.allItems.first()
                val shizukuEnabled = settingsRepository.isShizukuEnabled.first()
                
                if (shizukuEnabled) {
                    val currentDay = getCurrentDayOfWeek()
                    val groupsMap = groups.associate { it.id to it.packageNames }
                    handleAppDisabling(items, groupsMap, currentDay)
                    handleWorkProfile(items, currentDay)
                }
            }
        }
        return START_STICKY
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
            Log.d("MainService", "Enforcing app state: $commands")
            serviceScope.launch {
                ShizukuUtils.executeCommand(commands.toString())
            }
            lastAppliedPackageStates.putAll(updates)
        }
    }

    private fun handleWorkProfile(items: List<MonitoredItemEntity>, currentDay: String) {
        if (ContextCompat.checkSelfPermission(this, "android.permission.MODIFY_QUIET_MODE") != PackageManager.PERMISSION_GRANTED) {
            // Try to grant it if Shizuku is enabled
            serviceScope.launch {
                if (settingsRepository.isShizukuEnabled.first()) {
                    ShizukuUtils.executeCommand("pm grant $packageName android.permission.MODIFY_QUIET_MODE")
                }
            }
            return
        }

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

    private suspend fun checkWorkProfileAndEnforce() = withContext(Dispatchers.IO) {
        if (!settingsRepository.isMonitoringEnabled.first()) return@withContext

        val items = monitoredItemRepository.allItems.first()
        val currentDay = getCurrentDayOfWeek()
        val isWorkProfileItemActive = items.any { it.blockingTechnique == "WORK_PROFILE" && it.isActive && it.weekdayLimit.contains(currentDay)}

        if (!isQuietModeEnabled() && !isWorkProfileItemActive) {
            handleWorkProfile(items, currentDay)
            val relevantItem = items.find { it.blockingTechnique == "WORK_PROFILE" }
            val message = relevantItem?.forceQuitMessage ?: "Work profile disabled by LifeUp Catcher."
            withContext(Dispatchers.Main) { Toast.makeText(this@MainService, message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun isQuietModeEnabled(): Boolean {
        if (workProfileUserHandle == null) findWorkProfileUserHandle()
        if (workProfileUserHandle == null) return true
        return (getSystemService(USER_SERVICE) as UserManager).isQuietModeEnabled(workProfileUserHandle!!)
    }

    private fun findWorkProfileUserHandle() {
        val userManager = getSystemService(USER_SERVICE) as UserManager
        workProfileUserHandle = userManager.userProfiles.find { it.hashCode() != 0 }
    }

    private suspend fun checkAndEnforceDns() {
        val targetHostname = dnsRepository.dnsHostname.first()
        if (targetHostname.isBlank()) return

        val currentMode = Settings.Global.getString(contentResolver, "private_dns_mode")
        val currentHostname = Settings.Global.getString(contentResolver, "private_dns_specifier")

        if (currentMode != "hostname" || currentHostname != targetHostname) {
            Log.w("MainService", "Unauthorized DNS change detected! Reverting...")
            withContext(Dispatchers.IO) {
                ShizukuUtils.executeCommand("settings put global private_dns_mode hostname")
                ShizukuUtils.executeCommand("settings put global private_dns_specifier $targetHostname")
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "LifeUpCatcherMainService"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "LifeUp Catcher Core", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LifeUp Catcher Active")
            .setContentText("Monitoring system states and enforcing rules...")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(this, 10, notification, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun getCurrentDayOfWeek(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"; Calendar.TUESDAY -> "Tuesday"; Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"; Calendar.FRIDAY -> "Friday"; Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"; else -> ""
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        contentResolver.unregisterContentObserver(dnsObserver)
        try {
            unregisterReceiver(workProfileStateReceiver)
        } catch (_: Exception) {}
    }
}
