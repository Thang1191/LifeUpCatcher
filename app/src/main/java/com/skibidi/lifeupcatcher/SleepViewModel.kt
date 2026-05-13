package com.skibidi.lifeupcatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import com.skibidi.lifeupcatcher.data.repository.SleepSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val sleepSettings: StateFlow<SleepSettings> = settingsRepository.sleepSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepSettings())

    fun updateSettings(settings: SleepSettings) {
        viewModelScope.launch {
            settingsRepository.updateSleepSettings(settings)
        }
    }
}
