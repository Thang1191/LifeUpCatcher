package com.skibidi.lifeupcatcher

import android.content.Context
import android.content.SharedPreferences

class LauncherSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("is_service_enabled", false)
        set(value) = prefs.edit().putBoolean("is_service_enabled", value).apply()

    var mainLauncher: String?
        get() = prefs.getString("main_launcher", null)
        set(value) = prefs.edit().putString("main_launcher", value).apply()

    var focusLauncher: String?
        get() = prefs.getString("focus_launcher", null)
        set(value) = prefs.edit().putString("focus_launcher", value).apply()

    var startTime: String
        get() = prefs.getString("start_time", "22:00") ?: "22:00"
        set(value) = prefs.edit().putString("start_time", value).apply()

    var endTime: String
        get() = prefs.getString("end_time", "08:00") ?: "08:00"
        set(value) = prefs.edit().putString("end_time", value).apply()

    var weekdays: List<Boolean>
        get() {
            val savedString = prefs.getString("weekdays", "false,false,false,false,false,false,false")
            return savedString?.split(',')?.map { it.toBoolean() } ?: List(7) { false }
        }
        set(value) {
            val stringValue = value.joinToString(",")
            prefs.edit().putString("weekdays", stringValue).apply()
        }
}