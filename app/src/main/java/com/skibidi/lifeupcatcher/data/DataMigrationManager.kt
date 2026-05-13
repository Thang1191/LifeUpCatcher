package com.skibidi.lifeupcatcher.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.skibidi.lifeupcatcher.data.local.entity.AppGroupEntity
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import com.skibidi.lifeupcatcher.data.repository.AppGroupRepository
import com.skibidi.lifeupcatcher.data.repository.LauncherRepository
import com.skibidi.lifeupcatcher.data.repository.MonitoredItemRepository
import com.skibidi.lifeupcatcher.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataMigrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val appGroupRepository: AppGroupRepository,
    private val monitoredItemRepository: MonitoredItemRepository,
    private val launcherRepository: LauncherRepository
) {
    private val gson = Gson()

    fun migrateIfNeeded() {
        val prefs = context.getSharedPreferences("lifeup_catcher_prefs", Context.MODE_PRIVATE)
        val appPickerPrefs = context.getSharedPreferences("app_picker_prefs", Context.MODE_PRIVATE)
        val launcherPrefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

        if (!prefs.contains("migration_done")) {
            Log.d("DataMigrationManager", "Starting migration...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Migrate Settings
                    val monitoringEnabled = prefs.getBoolean("monitoring_enabled", false)
                    val shizukuEnabled = prefs.getBoolean("shizuku_enabled", false)
                    val debuggingEnabled = prefs.getBoolean("debugging_enabled", false)

                    settingsRepository.setMonitoringEnabled(monitoringEnabled)
                    settingsRepository.setShizukuEnabled(shizukuEnabled)
                    settingsRepository.setDebuggingEnabled(debuggingEnabled)

                    // Migrate Launcher Settings
                    if (launcherPrefs.all.isNotEmpty()) {
                        launcherRepository.setServiceEnabled(launcherPrefs.getBoolean("is_service_enabled", false))
                        launcherRepository.setMainLauncher(launcherPrefs.getString("main_launcher", null))
                        launcherRepository.setFocusLauncher(launcherPrefs.getString("focus_launcher", null))
                        launcherRepository.setStartTime(launcherPrefs.getString("start_time", "22:00") ?: "22:00")
                        launcherRepository.setEndTime(launcherPrefs.getString("end_time", "08:00") ?: "08:00")
                        val weekdaysStr = launcherPrefs.getString("weekdays", "false,false,false,false,false,false,false")
                        val weekdays = weekdaysStr?.split(',')?.map { it.toBoolean() } ?: List(7) { false }
                        launcherRepository.setWeekdays(weekdays)
                    }

                    // Migrate App Groups
                    val groupsJson = appPickerPrefs.getString("app_groups", "[]") ?: "[]"
                    val jsonArray = JSONArray(groupsJson)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val name = obj.getString("name")
                        val packagesArray = obj.getJSONArray("packages")
                        val packages = mutableSetOf<String>()
                        for (j in 0 until packagesArray.length()) {
                            packages.add(packagesArray.getString(j))
                        }
                        appGroupRepository.addGroup(AppGroupEntity(id, name, packages))
                    }

                    // Migrate Monitored Items
                    val itemsJson = prefs.getString("shop_items", null)
                    if (itemsJson != null) {
                        val type = object : TypeToken<List<OldShopItemState>>() {}.type
                        val oldItems: List<OldShopItemState> = gson.fromJson(itemsJson, type)
                        oldItems.forEach { old ->
                            monitoredItemRepository.addItem(
                                MonitoredItemEntity(
                                    name = old.name,
                                    isActive = old.isActive,
                                    linkedGroupId = old.linkedGroupId,
                                    startMessage = old.startMessage,
                                    stopMessage = old.stopMessage,
                                    forceQuitMessage = old.forceQuitMessage,
                                    blockingTechnique = old.blockingTechnique,
                                    weekdayLimit = old.weekdayLimit ?: setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                                )
                            )
                        }
                    }

                    prefs.edit().putBoolean("migration_done", true).apply()
                    Log.d("DataMigrationManager", "Migration completed successfully.")
                } catch (e: Exception) {
                    Log.e("DataMigrationManager", "Migration failed", e)
                }
            }
        }
    }

    private data class OldShopItemState(
        val name: String,
        val isActive: Boolean = false,
        val linkedGroupId: String? = null,
        val startMessage: String? = null,
        val stopMessage: String? = null,
        val forceQuitMessage: String? = null,
        val blockingTechnique: String = "HOME",
        val weekdayLimit: Set<String>? = null
    )
}
