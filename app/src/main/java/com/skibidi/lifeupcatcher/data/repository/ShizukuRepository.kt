package com.skibidi.lifeupcatcher.data.repository

import android.content.pm.PackageManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

data class ShizukuState(
    val isAvailable: Boolean = false,
    val isPermissionGranted: Boolean = false
)

@Singleton
class ShizukuRepository @Inject constructor() {

    val state: Flow<ShizukuState> = callbackFlow {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            trySend(getCurrentState())
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            trySend(ShizukuState(isAvailable = false, isPermissionGranted = false))
        }
        val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            trySend(getCurrentState().copy(isPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED))
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        trySend(getCurrentState())

        awaitClose {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
    }.onStart { emit(getCurrentState()) }

    fun getCurrentState(): ShizukuState {
        val isAvailable = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
        val isPermissionGranted = isAvailable && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        return ShizukuState(isAvailable, isPermissionGranted)
    }
}
