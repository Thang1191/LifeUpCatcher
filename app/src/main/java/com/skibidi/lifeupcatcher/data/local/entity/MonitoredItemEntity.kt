package com.skibidi.lifeupcatcher.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_items")
data class MonitoredItemEntity(
    @PrimaryKey
    val name: String,
    val isActive: Boolean = false,
    val linkedGroupId: String? = null,
    val startMessage: String? = null,
    val stopMessage: String? = null,
    val forceQuitMessage: String? = null,
    val blockingTechnique: String = "HOME",
    val weekdayLimit: Set<String> = setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
)
