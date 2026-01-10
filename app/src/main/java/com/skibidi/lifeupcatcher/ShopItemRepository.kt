package com.skibidi.lifeupcatcher

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private var applicationContext: Context? = null
    private const val PREFS_NAME = "lifeup_catcher_prefs"
    private const val KEY_ITEMS = "shop_items"
    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_SHIZUKU = "shizuku_enabled"

    fun initialize(context: Context) {
        if (applicationContext != null) return // Already initialized
        applicationContext = context.applicationContext
        load()
    }

    private fun load() {
        val context = applicationContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        _isMonitoringEnabled.value = prefs.getBoolean(KEY_MONITORING, false)
        _isShizukuEnabled.value = prefs.getBoolean(KEY_SHIZUKU, false)

        val itemsJson = prefs.getString(KEY_ITEMS, null)
        if (itemsJson != null) {
            try {
                val type = object : TypeToken<List<ShopItemState>>() {}.type
                val list: List<ShopItemState> = Gson().fromJson(itemsJson, type)
                _items.value = list
            } catch (e: Exception) {
                Log.e("ShopItemRepository", "Error loading items", e)
            }
        }
    }

    private fun save() {
        val context = applicationContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean(KEY_MONITORING, _isMonitoringEnabled.value)
        editor.putBoolean(KEY_SHIZUKU, _isShizukuEnabled.value)

        val itemsJson = Gson().toJson(_items.value)
        editor.putString(KEY_ITEMS, itemsJson)

        editor.apply()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        _isMonitoringEnabled.value = enabled
        save()
    }

    fun setShizukuEnabled(enabled: Boolean) {
        _isShizukuEnabled.value = enabled
        save()
    }

    fun addItem(name: String, linkedGroupId: String? = null, startMsg: String? = null, stopMsg: String? = null, forceQuitMsg: String? = null, blockingTechnique: String = "HOME") {
        if (_items.value.none { it.name == name }) {
            _items.update { it + ShopItemState(name, false, linkedGroupId, startMsg, stopMsg, forceQuitMsg, blockingTechnique) }
            Log.d("ShopItemRepository", "Added receiver for item: $name")
            save()
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
        save()
    }

    fun removeItem(name: String) {
        _items.update { list -> list.filter { it.name != name } }
        save()
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
        save()
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
        save()
    }
}
