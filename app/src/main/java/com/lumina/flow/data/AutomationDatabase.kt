package com.lumina.flow.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AutomationEntity::class], version = 3, exportSchema = false)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
}
