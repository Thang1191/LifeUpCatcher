package com.skibidi.lifeupcatcher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.skibidi.lifeupcatcher.data.DataMigrationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LifeUpCatcherApp : Application(), Configuration.Provider {

    @Inject
    lateinit var dataMigrationManager: DataMigrationManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        dataMigrationManager.migrateIfNeeded()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
