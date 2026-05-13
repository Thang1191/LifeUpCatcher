package com.skibidi.lifeupcatcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch
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

    private val homeActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.skibidi.lifeupcatcher.PERFORM_HOME_ACTION") {
                Log.d("MyAccessibilityService", "Received home action broadcast. Performing GLOBAL_ACTION_HOME.")
                performGlobalAction(GLOBAL_ACTION_HOME)
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

        serviceScope.launch {
            combine(
                appGroupRepository.allGroups,
                monitoredItemRepository.allItems
            ) { groups, items ->
                val currentDay = getCurrentDayOfWeek()

                // Build Block Cache for "HOME" technique
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
            }.collectLatest { }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        serviceScope.launch {
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
        } catch (_: Exception) {}
    }
}
