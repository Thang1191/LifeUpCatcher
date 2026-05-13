package com.skibidi.lifeupcatcher.data.repository

import com.skibidi.lifeupcatcher.data.local.dao.MonitoredItemDao
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoredItemRepository @Inject constructor(
    private val monitoredItemDao: MonitoredItemDao
) {
    val allItems: Flow<List<MonitoredItemEntity>> = monitoredItemDao.getAllItems()

    suspend fun addItem(item: MonitoredItemEntity) {
        monitoredItemDao.insertItem(item)
    }

    suspend fun updateItem(item: MonitoredItemEntity) {
        monitoredItemDao.updateItem(item)
    }

    suspend fun deleteItem(item: MonitoredItemEntity) {
        monitoredItemDao.deleteItem(item)
    }

    suspend fun updateItemState(name: String, isActive: Boolean) {
        monitoredItemDao.updateItemState(name, isActive)
    }

    suspend fun getItemByName(name: String): MonitoredItemEntity? {
        return monitoredItemDao.getItemByName(name)
    }
}
