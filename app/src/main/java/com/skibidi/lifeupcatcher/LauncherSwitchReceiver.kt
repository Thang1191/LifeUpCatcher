package com.skibidi.lifeupcatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "LauncherSwitchReceiver"

class LauncherSwitchReceiver : BroadcastReceiver() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val repository = LauncherSettingsRepository(context)
        val action = intent.action
        Log.d(TAG, "Received intent with action: $action")

        when (action) {
            ACTION_SWITCH_TO_FOCUS_LAUNCHER, ACTION_SWITCH_TO_MAIN_LAUNCHER -> {
                val weekdays = repository.weekdays
                val today = Calendar.getInstance()
                val todayIndex = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday is 0, Sunday is 6

                Log.d(TAG, "Today's index: $todayIndex (Monday is 0)")
                Log.d(TAG, "Weekday settings: $weekdays")

                if (weekdays.getOrNull(todayIndex) == true) {
                    coroutineScope.launch {
                        val mainLauncher = repository.mainLauncher
                        val focusLauncher = repository.focusLauncher

                        if (mainLauncher.isNullOrBlank() || focusLauncher.isNullOrBlank()) {
                            Log.w(TAG, "Main or Focus launcher is not set. Aborting switch.")
                            return@launch
                        }

                        val (newLauncher, oldLauncher) = if (action == ACTION_SWITCH_TO_FOCUS_LAUNCHER) {
                            focusLauncher to mainLauncher
                        } else {
                            mainLauncher to focusLauncher
                        }

                        Log.d(TAG, "Conditions met. Switching from $oldLauncher to $newLauncher")

                        ShizukuUtils.executeCommand("pm set-home-activity $newLauncher")
                        delay(500)

                        val homeIntent = Intent("com.skibidi.lifeupcatcher.PERFORM_HOME_ACTION")
                        context.sendBroadcast(homeIntent)
                        delay(500)

                        ShizukuUtils.executeCommand("am force-stop $oldLauncher")
                    }
                } else {
                    Log.d(TAG, "Conditions not met. Today is not an active day.")
                }
                rescheduleAlarm(context, action)
            }
        }
    }

    private fun rescheduleAlarm(context: Context, action: String?) {
        if (action == null) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "Cannot reschedule exact alarm, permission not granted.")
            return
        }

        val repository = LauncherSettingsRepository(context)
        val time = if (action == ACTION_SWITCH_TO_FOCUS_LAUNCHER) {
            repository.startTime
        } else {
            repository.endTime
        }
        val hour = time.split(":")[0].toInt()
        val minute = time.split(":")[1].toInt()

        val nextTriggerCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1) // Set for tomorrow
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        val rescheduleIntent = Intent(context, LauncherSwitchReceiver::class.java).apply {
            this.action = action
        }

        val requestCode = if (action == ACTION_SWITCH_TO_FOCUS_LAUNCHER) 0 else 1
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            rescheduleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        Log.d(TAG, "Rescheduling alarm for action '$action' at ${nextTriggerCalendar.time}")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTriggerCalendar.timeInMillis,
            pendingIntent
        )
    }

    companion object {
        const val ACTION_SWITCH_TO_FOCUS_LAUNCHER = "com.skibidi.lifeupcatcher.SWITCH_TO_FOCUS_LAUNCHER"
        const val ACTION_SWITCH_TO_MAIN_LAUNCHER = "com.skibidi.lifeupcatcher.SWITCH_TO_MAIN_LAUNCHER"
    }
}
