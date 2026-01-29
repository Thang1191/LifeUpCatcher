package com.skibidi.lifeupcatcher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuUtils {

    private const val TAG = "ShizukuUtils"

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCommand(command: String) {
        if (!isShizukuAvailable()) {
            Log.e(TAG, "Cannot execute command, Shizuku is not available.")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Executing Shizuku command: $command")
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
                val exitCode = process.waitFor()
                Log.d(TAG, "Shizuku command finished with exit code: $exitCode")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute Shizuku command: $command", e)
            }
        }
    }
}