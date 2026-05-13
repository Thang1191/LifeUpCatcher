package com.skibidi.lifeupcatcher.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LauncherRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val IS_SERVICE_ENABLED = booleanPreferencesKey("launcher_service_enabled")
        val MAIN_LAUNCHER = stringPreferencesKey("main_launcher")
        val FOCUS_LAUNCHER = stringPreferencesKey("focus_launcher")
        val START_TIME = stringPreferencesKey("launcher_start_time")
        val END_TIME = stringPreferencesKey("launcher_end_time")
        val WEEKDAYS = stringPreferencesKey("launcher_weekdays")
    }

    val isServiceEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.IS_SERVICE_ENABLED] ?: false }
    val mainLauncher: Flow<String?> = dataStore.data.map { it[PreferencesKeys.MAIN_LAUNCHER] }
    val focusLauncher: Flow<String?> = dataStore.data.map { it[PreferencesKeys.FOCUS_LAUNCHER] }
    val startTime: Flow<String> = dataStore.data.map { it[PreferencesKeys.START_TIME] ?: "22:00" }
    val endTime: Flow<String> = dataStore.data.map { it[PreferencesKeys.END_TIME] ?: "08:00" }
    val weekdays: Flow<List<Boolean>> = dataStore.data.map { preferences ->
        val savedString = preferences[PreferencesKeys.WEEKDAYS] ?: "false,false,false,false,false,false,false"
        savedString.split(',').map { it.toBoolean() }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_SERVICE_ENABLED] = enabled }
    }

    suspend fun setMainLauncher(packageName: String?) {
        dataStore.edit { preferences ->
            if (packageName == null) preferences.remove(PreferencesKeys.MAIN_LAUNCHER)
            else preferences[PreferencesKeys.MAIN_LAUNCHER] = packageName
        }
    }

    suspend fun setFocusLauncher(packageName: String?) {
        dataStore.edit { preferences ->
            if (packageName == null) preferences.remove(PreferencesKeys.FOCUS_LAUNCHER)
            else preferences[PreferencesKeys.FOCUS_LAUNCHER] = packageName
        }
    }

    suspend fun setStartTime(time: String) {
        dataStore.edit { it[PreferencesKeys.START_TIME] = time }
    }

    suspend fun setEndTime(time: String) {
        dataStore.edit { it[PreferencesKeys.END_TIME] = time }
    }

    suspend fun setWeekdays(weekdays: List<Boolean>) {
        dataStore.edit { it[PreferencesKeys.WEEKDAYS] = weekdays.joinToString(",") }
    }
}
