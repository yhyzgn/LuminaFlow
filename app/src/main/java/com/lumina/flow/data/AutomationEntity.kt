package com.lumina.flow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String, // "TIME" or "LOCATION"
    val hour: Int? = null,
    val minute: Int? = null,
    val daysOfWeek: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radius: Float = 100f,
    val actionsJson: String,
    val conditionJson: String = "{}"
)