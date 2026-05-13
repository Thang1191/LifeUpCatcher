package com.skibidi.lifeupcatcher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.skibidi.lifeupcatcher.data.local.converter.SetConverter
import com.skibidi.lifeupcatcher.data.local.dao.AppGroupDao
import com.skibidi.lifeupcatcher.data.local.dao.MonitoredItemDao
import com.skibidi.lifeupcatcher.data.local.entity.AppGroupEntity
import com.skibidi.lifeupcatcher.data.local.entity.MonitoredItemEntity

@Database(
    entities = [AppGroupEntity::class, MonitoredItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SetConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appGroupDao(): AppGroupDao
    abstract fun monitoredItemDao(): MonitoredItemDao
}
