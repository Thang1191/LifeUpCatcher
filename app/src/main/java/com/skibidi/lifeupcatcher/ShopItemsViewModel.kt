package com.skibidi.lifeupcatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import com.skibidi.lifeupcatcher.data.repository.MonitoredItemRepository
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuRepository
import com.skibidi.lifeupcatcher.data.repository.ShizukuState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShopItemsViewModel @Inject constructor(
    private val monitoredItemRepository: MonitoredItemRepository,
    private val settingsRepository: SettingsRepository,
    private val shizukuRepository: ShizukuRepository
) : ViewModel() {

    val items: StateFlow<List<MonitoredItemEntity>> = monitoredItemRepository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isMonitoringEnabled: StateFlow<Boolean> = settingsRepository.isMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isShizukuEnabled: StateFlow<Boolean> = settingsRepository.isShizukuEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val shizukuState: StateFlow<ShizukuState> = shizukuRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShizukuState())

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMonitoringEnabled(enabled) }
    }

    fun setShizukuEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShizukuEnabled(enabled) }
    }

    fun addItem(
        name: String,
        linkedGroupId: String?,
        startMsg: String?,
        stopMsg: String?,
        forceQuitMsg: String?,
        blockingTechnique: String,
        weekdayLimit: Set<String>
    ) {
        viewModelScope.launch {
            monitoredItemRepository.addItem(
                MonitoredItemEntity(
                    name = name,
                    linkedGroupId = linkedGroupId,
                    startMessage = startMsg,
                    stopMessage = stopMsg,
                    forceQuitMessage = forceQuitMsg,
                    blockingTechnique = blockingTechnique,
                    weekdayLimit = weekdayLimit
                )
            )
        }
    }

    fun updateItem(
        name: String,
        linkedGroupId: String?,
        startMsg: String?,
        stopMsg: String?,
        forceQuitMsg: String?,
        blockingTechnique: String,
        weekdayLimit: Set<String>
    ) {
        viewModelScope.launch {
            val item = monitoredItemRepository.getItemByName(name)
            if (item != null) {
                monitoredItemRepository.updateItem(
                    item.copy(
                        linkedGroupId = linkedGroupId,
                        startMessage = startMsg,
                        stopMessage = stopMsg,
                        forceQuitMessage = forceQuitMsg,
                        blockingTechnique = blockingTechnique,
                        weekdayLimit = weekdayLimit
                    )
                )
            }
        }
    }

    fun removeItem(name: String) {
        viewModelScope.launch {
            val item = monitoredItemRepository.getItemByName(name)
            if (item != null) {
                monitoredItemRepository.deleteItem(item)
            }
        }
    }
}
