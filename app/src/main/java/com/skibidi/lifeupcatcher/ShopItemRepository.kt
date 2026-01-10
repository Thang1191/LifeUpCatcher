package com.skibidi.lifeupcatcher

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShopItemState(
    val name: String,
    val isActive: Boolean = false,
    val linkedGroupId: String? = null,
    val startMessage: String? = null,
    val stopMessage: String? = null,
    val forceQuitMessage: String? = null,
    val blockingTechnique: String = "HOME" // "HOME" or "DISABLE"
)

object ShopItemRepository {
    private val _items = MutableStateFlow<List<ShopItemState>>(emptyList())
    val items: StateFlow<List<ShopItemState>> = _items.asStateFlow()

    private val _isMonitoringEnabled = MutableStateFlow(false)
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    private val _isShizukuEnabled = MutableStateFlow(false)
    val isShizukuEnabled: StateFlow<Boolean> = _isShizukuEnabled.asStateFlow()

    fun setMonitoringEnabled(enabled: Boolean) {
        _isMonitoringEnabled.value = enabled
    }

    fun setShizukuEnabled(enabled: Boolean) {
        _isShizukuEnabled.value = enabled
    }

    fun addItem(name: String, linkedGroupId: String? = null, startMsg: String? = null, stopMsg: String? = null, forceQuitMsg: String? = null, blockingTechnique: String = "HOME") {
        if (_items.value.none { it.name == name }) {
            _items.update { it + ShopItemState(name, false, linkedGroupId, startMsg, stopMsg, forceQuitMsg, blockingTechnique) }
            Log.d("ShopItemRepository", "Added receiver for item: $name")
        }
    }

    fun updateItem(name: String, linkedGroupId: String?, startMsg: String?, stopMsg: String?, forceQuitMsg: String?, blockingTechnique: String) {
        _items.update { list ->
            list.map { item ->
                if (item.name == name) {
                    item.copy(
                        linkedGroupId = linkedGroupId,
                        startMessage = startMsg,
                        stopMessage = stopMsg,
                        forceQuitMessage = forceQuitMsg,
                        blockingTechnique = blockingTechnique
                    )
                } else {
                    item
                }
            }
        }
    }

    fun removeItem(name: String) {
        _items.update { list -> list.filter { it.name != name } }
    }

    fun updateItemState(name: String, isActive: Boolean) {
        _items.update { list ->
            list.map { item ->
                if (item.name == name) {
                    Log.d("ShopItemRepository", "Updating item '$name' state to: $isActive")
                    item.copy(isActive = isActive)
                } else {
                    item
                }
            }
        }
    }

    fun updateItemLinkedGroup(name: String, groupId: String?) {
        _items.update { list ->
            list.map { item ->
                if (item.name == name) {
                    item.copy(linkedGroupId = groupId)
                } else {
                    item
                }
            }
        }
    }
}
