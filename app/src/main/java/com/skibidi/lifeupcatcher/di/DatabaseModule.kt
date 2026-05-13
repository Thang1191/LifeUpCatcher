package com.skibidi.lifeupcatcher.di

import android.content.Context
import androidx.room.Room
import com.skibidi.lifeupcatcher.data.local.AppDatabase
import com.skibidi.lifeupcatcher.data.local.dao.AppGroupDao
import com.skibidi.lifeupcatcher.data.local.dao.MonitoredItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lifeup_catcher_db"
        ).build()
    }

    @Provides
    fun provideAppGroupDao(database: AppDatabase): AppGroupDao {
        return database.appGroupDao()
    }

    @Provides
    fun provideMonitoredItemDao(database: AppDatabase): MonitoredItemDao {
        return database.monitoredItemDao()
    }
}
