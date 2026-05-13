package com.skibidi.lifeupcatcher

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.repository.DnsRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DnsViewModel"

data class DnsUiState(
    val isDnsLockingEnabled: Boolean = false,
    val isShizukuAvailable: Boolean = false
)

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val application: Application,
    private val dnsRepository: DnsRepository,
    private val shizukuRepository: ShizukuRepository
) : AndroidViewModel(application) {

    var dnsHostname by mutableStateOf("")
        private set

    val uiState: StateFlow<DnsUiState> = combine(
        dnsRepository.isDnsLockingEnabled,
        dnsRepository.dnsHostname,
        shizukuRepository.state
    ) { isEnabled, hostname, shizukuState ->
        // Sync the property with repository on first load or if changed elsewhere
        if (dnsHostname.isEmpty() && hostname.isNotEmpty()) {
            dnsHostname = hostname
        }
        DnsUiState(
            isDnsLockingEnabled = isEnabled,
            isShizukuAvailable = shizukuState.isAvailable && shizukuState.isPermissionGranted
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DnsUiState())

    fun updateDnsHostname(hostname: String) {
        dnsHostname = hostname
        viewModelScope.launch {
            dnsRepository.setDnsHostname(hostname)
        }
    }

    fun setDnsLockingEnabled(enabled: Boolean) {
        if (enabled && !ShizukuUtils.isShizukuAvailable()) {
            Log.w(TAG, "Cannot enable DNS locking, Shizuku is not available.")
            return
        }

        viewModelScope.launch {
            dnsRepository.setDnsLockingEnabled(enabled)
            if (enabled) {
                enforceDnsSettings(dnsHostname)
            }
        }
    }

    private fun enforceDnsSettings(hostname: String) {

        if (hostname.isBlank()) return
        viewModelScope.launch {
            Log.d(TAG, "Enforcing DNS settings: hostname=$hostname")
            ShizukuUtils.executeCommand("settings put global private_dns_mode hostname")
            ShizukuUtils.executeCommand("settings put global private_dns_specifier $hostname")
        }
    }
}
