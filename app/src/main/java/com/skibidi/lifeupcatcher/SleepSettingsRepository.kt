package com.skibidi.lifeupcatcher

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SLEEP_SETTINGS_PREFERENCES = "sleep_settings_preferences"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SLEEP_SETTINGS_PREFERENCES)

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

class SleepSettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val IS_SERVICE_ENABLED = booleanPreferencesKey("is_service_enabled")
        val CHECK_HOUR = intPreferencesKey("check_hour")
        val CHECK_MINUTE = intPreferencesKey("check_minute")
        val THRESHOLD_HOURS = intPreferencesKey("threshold_hours")
        val THRESHOLD_MINUTES = intPreferencesKey("threshold_minutes")
        val REWARD_AMOUNT = intPreferencesKey("reward_amount")
        val PUNISHMENT_AMOUNT = intPreferencesKey("punishment_amount")
        val SUCCESS_TITLE = stringPreferencesKey("success_title")
        val SUCCESS_MESSAGE = stringPreferencesKey("success_message")
        val FAILURE_TITLE = stringPreferencesKey("failure_title")
        val FAILURE_MESSAGE = stringPreferencesKey("failure_message")
    }

    val sleepSettingsFlow: Flow<SleepSettings> = context.dataStore.data
        .map { preferences ->
            mapSleepSettings(preferences)
        }

    suspend fun updateSettings(settings: SleepSettings) {
        Log.d("SleepSettingsRepository", "Updating settings to: $settings")
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SERVICE_ENABLED] = settings.isServiceEnabled
            preferences[PreferencesKeys.CHECK_HOUR] = settings.checkHour
            preferences[PreferencesKeys.CHECK_MINUTE] = settings.checkMinute
            preferences[PreferencesKeys.THRESHOLD_HOURS] = settings.thresholdHours
            preferences[PreferencesKeys.THRESHOLD_MINUTES] = settings.thresholdMinutes
            preferences[PreferencesKeys.REWARD_AMOUNT] = settings.rewardAmount
            preferences[PreferencesKeys.PUNISHMENT_AMOUNT] = settings.punishmentAmount
            preferences[PreferencesKeys.SUCCESS_TITLE] = settings.successTitle
            preferences[PreferencesKeys.SUCCESS_MESSAGE] = settings.successMessage
            preferences[PreferencesKeys.FAILURE_TITLE] = settings.failureTitle
            preferences[PreferencesKeys.FAILURE_MESSAGE] = settings.failureMessage
        }
    }

    private fun mapSleepSettings(preferences: Preferences): SleepSettings {
        return SleepSettings(
            isServiceEnabled = preferences[PreferencesKeys.IS_SERVICE_ENABLED] ?: true,
            checkHour = preferences[PreferencesKeys.CHECK_HOUR] ?: 22,
            checkMinute = preferences[PreferencesKeys.CHECK_MINUTE] ?: 0,
            thresholdHours = preferences[PreferencesKeys.THRESHOLD_HOURS] ?: 8,
            thresholdMinutes = preferences[PreferencesKeys.THRESHOLD_MINUTES] ?: 0,
            rewardAmount = preferences[PreferencesKeys.REWARD_AMOUNT] ?: 10,
            punishmentAmount = preferences[PreferencesKeys.PUNISHMENT_AMOUNT] ?: 5,
            successTitle = preferences[PreferencesKeys.SUCCESS_TITLE] ?: "Sleep Goal Achieved!",
            successMessage = preferences[PreferencesKeys.SUCCESS_MESSAGE] ?: "Good job! You got enough sleep.",
            failureTitle = preferences[PreferencesKeys.FAILURE_TITLE] ?: "Sleep Goal Missed",
            failureMessage = preferences[PreferencesKeys.FAILURE_MESSAGE] ?: "You should sleep more."
        )
    }
}
