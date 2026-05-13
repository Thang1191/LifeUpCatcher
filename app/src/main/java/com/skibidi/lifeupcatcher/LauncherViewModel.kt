package com.skibidi.lifeupcatcher

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.repository.LauncherRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val TAG = "LauncherViewModel"

data class LauncherSettingsUiState(
    val isServiceEnabled: Boolean = false,
    val mainLauncher: String = "",
    val focusLauncher: String = "",
    val startTime: String = "22:00",
    val endTime: String = "08:00",
    val weekdays: List<Boolean> = List(7) { false },
    val isShizukuAvailable: Boolean = false
)

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val application: Application,
    private val launcherRepository: LauncherRepository,
    private val shizukuRepository: ShizukuRepository
) : AndroidViewModel(application) {

    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val uiState: StateFlow<LauncherSettingsUiState> = combine(
        launcherRepository.isServiceEnabled,
        launcherRepository.mainLauncher,
        launcherRepository.focusLauncher,
        launcherRepository.startTime,
        launcherRepository.endTime,
        launcherRepository.weekdays,
        shizukuRepository.state
    ) { args: Array<Any?> ->
        val shizukuState = args[6] as ShizukuState
        LauncherSettingsUiState(
            isServiceEnabled = args[0] as Boolean,
            mainLauncher = args[1] as String? ?: "",
            focusLauncher = args[2] as String? ?: "",
            startTime = args[3] as String,
            endTime = args[4] as String,
            weekdays = (args[5] as List<*>).map { it as Boolean },
            isShizukuAvailable = shizukuState.isAvailable && shizukuState.isPermissionGranted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LauncherSettingsUiState())

    fun setServiceEnabled(isEnabled: Boolean) {
        if (!ShizukuUtils.isShizukuAvailable()) {
            Log.w(TAG, "Cannot enable service, Shizuku is not available.")
            return
        }

        viewModelScope.launch {
            launcherRepository.setServiceEnabled(isEnabled)
            if (isEnabled) {
                Log.d(TAG, "Enabling launcher service and scheduling switch.")
                scheduleLauncherSwitch()
                checkTimeWindowAndSwitchIfNeeded()
            } else {
                Log.d(TAG, "Disabling launcher service and canceling switch.")
                cancelLauncherSwitch()
                switchToMainLauncher()
            }
        }
    }

    private fun switchToMainLauncher() {
        viewModelScope.launch {
            val mainLauncher = uiState.value.mainLauncher
            if (mainLauncher.isNotBlank()) {
                ShizukuUtils.executeCommand("pm set-home-activity $mainLauncher")
            }
        }
    }

    private fun checkTimeWindowAndSwitchIfNeeded() {
        val today = Calendar.getInstance()
        val todayIndex = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday is 0, Sunday is 6
        val currentState = uiState.value
        if (currentState.weekdays.getOrNull(todayIndex) != true) {
            Log.d(TAG, "Immediate check: Not an active day.")
            switchToMainLauncher()
            return
        }

        val isInWindow = isCurrentTimeInWindow(currentState.startTime, currentState.endTime)
        val targetLauncher = if (isInWindow) currentState.focusLauncher else currentState.mainLauncher
        val launcherType = if (isInWindow) "FOCUS" else "MAIN"

        viewModelScope.launch {
            if (targetLauncher.isNotBlank()) {
                Log.d(TAG, "Immediate check: Switching to $launcherType launcher.")
                ShizukuUtils.executeCommand("pm set-home-activity $targetLauncher")
            }
        }
    }

    fun setMainLauncher(packageName: String) {
        viewModelScope.launch { launcherRepository.setMainLauncher(packageName) }
    }

    fun setFocusLauncher(packageName: String) {
        viewModelScope.launch { launcherRepository.setFocusLauncher(packageName) }
    }

    fun setStartTime(time: String) {
        viewModelScope.launch { launcherRepository.setStartTime(time) }
    }

    fun setEndTime(time: String) {
        viewModelScope.launch { launcherRepository.setEndTime(time) }
    }

    fun setWeekdays(weekdays: List<Boolean>) {
        viewModelScope.launch { launcherRepository.setWeekdays(weekdays) }
    }

    private fun scheduleLauncherSwitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "Cannot schedule exact alarms. Please grant the permission.")
            return
        }

        val now = Calendar.getInstance()
        val currentState = uiState.value

        val startTime = currentState.startTime.split(":")
        val startHour = startTime[0].toInt()
        val startMinute = startTime[1].toInt()

        val endTime = currentState.endTime.split(":")
        val endHour = endTime[0].toInt()
        val endMinute = endTime[1].toInt()

        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!startCalendar.after(now)) {
            startCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!endCalendar.after(now)) {
            endCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val focusIntent = Intent(application, LauncherSwitchReceiver::class.java).apply {
            action = "com.skibidi.lifeupcatcher.SWITCH_TO_FOCUS_LAUNCHER"
        }
        val mainIntent = Intent(application, LauncherSwitchReceiver::class.java).apply {
            action = "com.skibidi.lifeupcatcher.SWITCH_TO_MAIN_LAUNCHER"
        }

        val focusPendingIntent = PendingIntent.getBroadcast(application, 0, focusIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val mainPendingIntent = PendingIntent.getBroadcast(application, 1, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        Log.d(TAG, "Scheduling EXACT FOCUS alarm for: ${startCalendar.time}")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            startCalendar.timeInMillis,
            focusPendingIntent
        )
        Log.d(TAG, "Scheduling EXACT MAIN alarm for: ${endCalendar.time}")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endCalendar.timeInMillis,
            mainPendingIntent
        )
    }

    private fun cancelLauncherSwitch() {
        val focusIntent = Intent(application, LauncherSwitchReceiver::class.java).apply {
            action = "com.skibidi.lifeupcatcher.SWITCH_TO_FOCUS_LAUNCHER"
        }
        val mainIntent = Intent(application, LauncherSwitchReceiver::class.java).apply {
            action = "com.skibidi.lifeupcatcher.SWITCH_TO_MAIN_LAUNCHER"
        }

        val focusPendingIntent = PendingIntent.getBroadcast(application, 0, focusIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val mainPendingIntent = PendingIntent.getBroadcast(application, 1, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        Log.d(TAG, "Canceling alarms.")
        alarmManager.cancel(focusPendingIntent)
        alarmManager.cancel(mainPendingIntent)
    }
}
