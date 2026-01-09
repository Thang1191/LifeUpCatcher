package com.skibidi.lifeupcatcher

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

class MyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var groupsMap: Map<String, Set<String>> = emptyMap()

    private val countdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val action = it.action
                val itemName = it.getStringExtra("item") ?: it.getStringExtra("name") ?: it.getStringExtra("title")
                
                if (itemName != null) {
                    val isStart = action == "app.lifeup.item.countdown.start"
                    Log.d("MyAccessibilityService", "Received broadcast: $action for item: $itemName")
                    
                    val currentItem = ShopItemRepository.items.value.find { it.name == itemName }
                    ShopItemRepository.updateItemState(itemName, isStart)
                    
                    if (currentItem != null) {
                        val message = if (isStart) currentItem.startMessage else currentItem.stopMessage
                        if (!message.isNullOrBlank()) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
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
        }
        registerReceiver(countdownReceiver, filter, RECEIVER_EXPORTED)

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
        
        loadGroups()
    }

    private fun loadGroups() {
        val prefs = getSharedPreferences("app_picker_prefs", 0)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!ShopItemRepository.isMonitoringEnabled.value) return
        
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Reload groups to ensure we have latest data
            loadGroups() 
            
            checkAndEnforceRules(packageName)
        }
    }

    private fun checkAndEnforceRules(currentPackage: String) {
        val items = ShopItemRepository.items.value
        
        for (item in items) {
            if (!item.isActive && item.linkedGroupId != null) {
                val allowedPackages = groupsMap[item.linkedGroupId]
                if (allowedPackages != null && allowedPackages.contains(currentPackage)) {
                    Log.i("MyAccessibilityService", "Blocking $currentPackage because item '${item.name}' is stopped.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    
                    if (!item.forceQuitMessage.isNullOrBlank()) {
                         Toast.makeText(this, item.forceQuitMessage, Toast.LENGTH_SHORT).show()
                    }
                    
                    break 
                }
            }
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
        } catch (e: IllegalArgumentException) {
            // Receiver might not be registered
        }
    }
}
