package com.lumina.flow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val triggerType: String, // "TIME", "INTERVAL", "LOCATION"
    val hour: Int? = null,
    val minute: Int? = null,
    val daysOfWeek: String = "",
    val intervalMinutes: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float = 100f,
    val enabled: Boolean = true,
    val actionsJson: String,
    val conditionsJson: String = "{}",
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
    val lastResult: String = "未执行",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
