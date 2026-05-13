package com.skibidi.lifeupcatcher.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_enabled")
        val DEBUGGING_ENABLED = booleanPreferencesKey("debugging_enabled")

        // Sleep Settings
        val SLEEP_SERVICE_ENABLED = booleanPreferencesKey("sleep_service_enabled")
        val SLEEP_CHECK_HOUR = intPreferencesKey("sleep_check_hour")
        val SLEEP_CHECK_MINUTE = intPreferencesKey("sleep_check_minute")
        val SLEEP_THRESHOLD_HOURS = intPreferencesKey("sleep_threshold_hours")
        val SLEEP_THRESHOLD_MINUTES = intPreferencesKey("sleep_threshold_minutes")
        val SLEEP_REWARD_AMOUNT = intPreferencesKey("sleep_reward_amount")
        val SLEEP_PUNISHMENT_AMOUNT = intPreferencesKey("sleep_punishment_amount")
        val SLEEP_SUCCESS_TITLE = stringPreferencesKey("sleep_success_title")
        val SLEEP_SUCCESS_MESSAGE = stringPreferencesKey("sleep_success_message")
        val SLEEP_FAILURE_TITLE = stringPreferencesKey("sleep_failure_title")
        val SLEEP_FAILURE_MESSAGE = stringPreferencesKey("sleep_failure_message")

        // Lock Settings
        val RANDOM_LOCK_ENABLED = booleanPreferencesKey("random_lock_enabled")
        val RANDOM_LOCK_CHAR_COUNT = intPreferencesKey("random_lock_char_count")
    }

    val isMonitoringEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.MONITORING_ENABLED] ?: false }
    val isShizukuEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.SHIZUKU_ENABLED] ?: false }
    val isDebuggingEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DEBUGGING_ENABLED] ?: false }

    val randomLockSettingsFlow: Flow<RandomLockSettings> = dataStore.data.map { preferences ->
        RandomLockSettings(
            isEnabled = preferences[PreferencesKeys.RANDOM_LOCK_ENABLED] ?: false,
            charCount = preferences[PreferencesKeys.RANDOM_LOCK_CHAR_COUNT] ?: 10
        )
    }

    val sleepSettingsFlow: Flow<SleepSettings> = dataStore.data.map { preferences ->
        SleepSettings(
            isServiceEnabled = preferences[PreferencesKeys.SLEEP_SERVICE_ENABLED] ?: true,
            checkHour = preferences[PreferencesKeys.SLEEP_CHECK_HOUR] ?: 22,
            checkMinute = preferences[PreferencesKeys.SLEEP_CHECK_MINUTE] ?: 0,
            thresholdHours = preferences[PreferencesKeys.SLEEP_THRESHOLD_HOURS] ?: 8,
            thresholdMinutes = preferences[PreferencesKeys.SLEEP_THRESHOLD_MINUTES] ?: 0,
            rewardAmount = preferences[PreferencesKeys.SLEEP_REWARD_AMOUNT] ?: 10,
            punishmentAmount = preferences[PreferencesKeys.SLEEP_PUNISHMENT_AMOUNT] ?: 5,
            successTitle = preferences[PreferencesKeys.SLEEP_SUCCESS_TITLE] ?: "Sleep Goal Achieved!",
            successMessage = preferences[PreferencesKeys.SLEEP_SUCCESS_MESSAGE] ?: "Good job! You got enough sleep.",
            failureTitle = preferences[PreferencesKeys.SLEEP_FAILURE_TITLE] ?: "Sleep Goal Missed",
            failureMessage = preferences[PreferencesKeys.SLEEP_FAILURE_MESSAGE] ?: "You should sleep more."
        )
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setShizukuEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SHIZUKU_ENABLED] = enabled }
    }

    suspend fun setDebuggingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DEBUGGING_ENABLED] = enabled }
    }

    suspend fun updateRandomLockSettings(settings: RandomLockSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RANDOM_LOCK_ENABLED] = settings.isEnabled
            preferences[PreferencesKeys.RANDOM_LOCK_CHAR_COUNT] = settings.charCount
        }
    }

    suspend fun updateSleepSettings(settings: SleepSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLEEP_SERVICE_ENABLED] = settings.isServiceEnabled
            preferences[PreferencesKeys.SLEEP_CHECK_HOUR] = settings.checkHour
            preferences[PreferencesKeys.SLEEP_CHECK_MINUTE] = settings.checkMinute
            preferences[PreferencesKeys.SLEEP_THRESHOLD_HOURS] = settings.thresholdHours
            preferences[PreferencesKeys.SLEEP_THRESHOLD_MINUTES] = settings.thresholdMinutes
            preferences[PreferencesKeys.SLEEP_REWARD_AMOUNT] = settings.rewardAmount
            preferences[PreferencesKeys.SLEEP_PUNISHMENT_AMOUNT] = settings.punishmentAmount
            preferences[PreferencesKeys.SLEEP_SUCCESS_TITLE] = settings.successTitle
            preferences[PreferencesKeys.SLEEP_SUCCESS_MESSAGE] = settings.successMessage
            preferences[PreferencesKeys.SLEEP_FAILURE_TITLE] = settings.failureTitle
            preferences[PreferencesKeys.SLEEP_FAILURE_MESSAGE] = settings.failureMessage
        }
    }
}

data class RandomLockSettings(
    val isEnabled: Boolean = false,
    val charCount: Int = 10
)

data class SleepSettings(
    val isServiceEnabled: Boolean = true,
    val checkHour: Int = 22,
    val checkMinute: Int = 0,
    val thresholdHours: Int = 8,
    val thresholdMinutes: Int = 0,
    val rewardAmount: Int = 10,
    val punishmentAmount: Int = 5,
    val successTitle: String = "Sleep Goal Achieved!",
    val successMessage: String = "Good job! You got enough sleep.",
    val failureTitle: String = "Sleep Goal Missed",
    val failureMessage: String = "You should sleep more."
)
