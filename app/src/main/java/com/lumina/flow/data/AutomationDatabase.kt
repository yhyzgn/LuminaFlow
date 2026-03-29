package com.lumina.flow.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AutomationEntity::class], version = 1, exportSchema = false)
abstract class AutomationDatabase : RoomDatabase() {
    abstract fun automationDao(): AutomationDao
}