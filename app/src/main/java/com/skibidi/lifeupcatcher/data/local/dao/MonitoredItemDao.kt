package com.skibidi.lifeupcatcher.data.local.dao

import androidx.room.*
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredItemDao {
    @Query("SELECT * FROM monitored_items")
    fun getAllItems(): Flow<List<MonitoredItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MonitoredItemEntity)

    @Update
    suspend fun updateItem(item: MonitoredItemEntity)

    @Delete
    suspend fun deleteItem(item: MonitoredItemEntity)

    @Query("SELECT * FROM monitored_items WHERE name = :name")
    suspend fun getItemByName(name: String): MonitoredItemEntity?

    @Query("UPDATE monitored_items SET isActive = :isActive WHERE name = :name")
    suspend fun updateItemState(name: String, isActive: Boolean)
}
