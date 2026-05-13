package com.skibidi.lifeupcatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isDebuggingEnabled: StateFlow<Boolean> = settingsRepository.isDebuggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDebuggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDebuggingEnabled(enabled)
        }
    }

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        if (command.isBlank() || !Shizuku.pingBinder()) return@withContext "Shizuku not available or command empty."
        try {
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

            val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            "Exit Code: ${process.exitValue()}\n\nSTDOUT:\n$stdOut\n\nSTDERR:\n$stdErr"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
