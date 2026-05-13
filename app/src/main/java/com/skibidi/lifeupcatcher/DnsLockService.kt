package com.skibidi.lifeupcatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.skibidi.lifeupcatcher.data.repository.DnsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class DnsLockService : Service() {

    @Inject
    lateinit var dnsRepository: DnsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val TAG = "DnsLockService"

    private val dnsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "Global setting changed: $uri")
            serviceScope.launch {
                checkAndEnforceDns()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DNS Lock Service created")
        startForegroundService()

        contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, dnsObserver)

        // React to hostname changes and initial enforcement
        serviceScope.launch {
            dnsRepository.dnsHostname.collectLatest { hostname ->
                if (hostname.isNotBlank()) {
                    checkAndEnforceDns()
                }
            }
        }
    }

    private suspend fun checkAndEnforceDns() {
        val targetHostname = dnsRepository.dnsHostname.first()
        if (targetHostname.isBlank()) return

        val currentMode = Settings.Global.getString(contentResolver, "private_dns_mode")
        val currentHostname = Settings.Global.getString(contentResolver, "private_dns_specifier")

        if (currentMode != "hostname" || currentHostname != targetHostname) {
            Log.w(TAG, "Unauthorized DNS change detected! currentMode=$currentMode, currentHostname=$currentHostname. Reverting to $targetHostname...")
            withContext(Dispatchers.IO) {
                ShizukuUtils.executeCommand("settings put global private_dns_mode hostname")
                ShizukuUtils.executeCommand("settings put global private_dns_specifier $targetHostname")
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "DnsLockServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "DNS Locking Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DNS Locking Active")
            .setContentText("Enforcing private DNS settings...")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            2,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "DNS Lock Service destroyed")
        serviceScope.cancel()
        contentResolver.unregisterContentObserver(dnsObserver)
    }
}
