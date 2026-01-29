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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

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

class LauncherViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = LauncherSettingsRepository(application)
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _uiState = MutableStateFlow(LauncherSettingsUiState())
    val uiState: StateFlow<LauncherSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        checkShizukuStatus()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isServiceEnabled = repository.isServiceEnabled,
                    mainLauncher = repository.mainLauncher ?: "",
                    focusLauncher = repository.focusLauncher ?: "",
                    startTime = repository.startTime,
                    endTime = repository.endTime,
                    weekdays = repository.weekdays
                )
            }
        }
    }

    private fun checkShizukuStatus() {
        _uiState.update { it.copy(isShizukuAvailable = ShizukuUtils.isShizukuAvailable()) }
    }

    fun setServiceEnabled(isEnabled: Boolean) {
        if (!ShizukuUtils.isShizukuAvailable()) {
            Log.w(TAG, "Cannot enable service, Shizuku is not available.")
            return
        }

        repository.isServiceEnabled = isEnabled
        _uiState.update { it.copy(isServiceEnabled = isEnabled) }

        if (isEnabled) {
            Log.d(TAG, "Enabling launcher service and scheduling switch.")
            scheduleLauncherSwitch()
            checkTimeWindowAndSwitchIfNeeded()
        } else {
            Log.d(TAG, "Disabling launcher service and canceling switch.")
            cancelLauncherSwitch()
            switchToMainLauncher() // Revert to main launcher when service is disabled
        }
    }

    private fun switchToMainLauncher() {
        viewModelScope.launch {
            val mainLauncher = repository.mainLauncher
            if (!mainLauncher.isNullOrBlank()) {
                ShizukuUtils.executeCommand("pm set-home-activity $mainLauncher")
            }
        }
    }

    private fun checkTimeWindowAndSwitchIfNeeded() {
        val weekdays = repository.weekdays
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayIndex = today - 1

        if (weekdays.getOrNull(todayIndex) != true) {
            Log.d(TAG, "Immediate check: Not an active day.")
            switchToMainLauncher()
            return
        }

        val startTime = repository.startTime.split(":")
        val startHour = startTime[0].toInt()
        val startMinute = startTime[1].toInt()

        val endTime = repository.endTime.split(":")
        val endHour = endTime[0].toInt()
        val endMinute = endTime[1].toInt()

        val now = Calendar.getInstance()
        val startCalendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, startMinute) }
        val endCalendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, endHour); set(Calendar.MINUTE, endMinute) }

        val isInTimeWindow = if (startCalendar.after(endCalendar)) { // Overnight case
            now.after(startCalendar) || now.before(endCalendar)
        } else { // Same day case
            now.after(startCalendar) && now.before(endCalendar)
        }

        viewModelScope.launch {
            if (isInTimeWindow) {
                val focusLauncher = repository.focusLauncher
                if (!focusLauncher.isNullOrBlank()) {
                    Log.d(TAG, "Immediate check: Inside time window. Switching to FOCUS launcher.")
                    ShizukuUtils.executeCommand("pm set-home-activity $focusLauncher")
                }
            } else {
                val mainLauncher = repository.mainLauncher
                if (!mainLauncher.isNullOrBlank()) {
                    Log.d(TAG, "Immediate check: Outside time window. Switching to MAIN launcher.")
                    ShizukuUtils.executeCommand("pm set-home-activity $mainLauncher")
                }
            }
        }
    }

    fun setMainLauncher(packageName: String) {
        repository.mainLauncher = packageName
        _uiState.update { it.copy(mainLauncher = packageName) }
    }

    fun setFocusLauncher(packageName: String) {
        repository.focusLauncher = packageName
        _uiState.update { it.copy(focusLauncher = packageName) }
    }

    fun setStartTime(time: String) {
        repository.startTime = time
        _uiState.update { it.copy(startTime = time) }
    }

    fun setEndTime(time: String) {
        repository.endTime = time
        _uiState.update { it.copy(endTime = time) }
    }

    fun setWeekdays(weekdays: List<Boolean>) {
        repository.weekdays = weekdays
        _uiState.update { it.copy(weekdays = weekdays) }
    }

    private fun scheduleLauncherSwitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.e(TAG, "Cannot schedule exact alarms. Please grant the permission.")
            return
        }

        val startTime = repository.startTime.split(":")
        val startHour = startTime[0].toInt()
        val startMinute = startTime[1].toInt()

        val endTime = repository.endTime.split(":")
        val endHour = endTime[0].toInt()
        val endMinute = endTime[1].toInt()

        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
        }
        if (startCalendar.before(Calendar.getInstance())) {
            startCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val endCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
        }
        if (endCalendar.before(Calendar.getInstance())) {
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