package com.skibidi.lifeupcatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.repository.RandomLockSettings
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class LockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val lockSettings = settingsRepository.randomLockSettingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RandomLockSettings()
    )

    private val _isTemporarilyUnlocked = MutableStateFlow(false)
    val isTemporarilyUnlocked = _isTemporarilyUnlocked.asStateFlow()

    private val _currentChallenge = MutableStateFlow("")
    val currentChallenge = _currentChallenge.asStateFlow()

    private val _userInput = MutableStateFlow("")
    val userInput = _userInput.asStateFlow()

    private val _isChallengeActive = MutableStateFlow(false)
    val isChallengeActive = _isChallengeActive.asStateFlow()

    val isLocked: StateFlow<Boolean> = combine(
        lockSettings,
        isTemporarilyUnlocked
    ) { settings, tempUnlocked ->
        settings.isEnabled && !tempUnlocked
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun enableLock(charCount: Int) {
        viewModelScope.launch {
            settingsRepository.updateRandomLockSettings(RandomLockSettings(true, charCount))
            _isTemporarilyUnlocked.value = false
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            settingsRepository.updateRandomLockSettings(RandomLockSettings(false, 10))
            _isTemporarilyUnlocked.value = false
        }
    }

    fun startChallenge() {
        // Exclude visually ambiguous characters: O, 0, I, l, 1
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val count = lockSettings.value.charCount
        val challenge = (1..count)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
        _currentChallenge.value = challenge
        _userInput.value = ""
        _isChallengeActive.value = true
    }

    fun updateUserInput(input: String) {
        _userInput.value = input
        if (input == _currentChallenge.value && _currentChallenge.value.isNotEmpty()) {
            _isTemporarilyUnlocked.value = true
            _isChallengeActive.value = false
            _currentChallenge.value = ""
            _userInput.value = ""
        }
    }

    fun resetChallenge() {
        _isChallengeActive.value = false
        _currentChallenge.value = ""
        _userInput.value = ""
    }

    fun onAppBackgrounded() {
        // Reset challenge if user leaves
        resetChallenge()
        // Re-lock if it was temporarily unlocked
        _isTemporarilyUnlocked.value = false
    }
}
