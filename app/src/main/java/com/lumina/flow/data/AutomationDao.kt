package com.lumina.flow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation")
    fun getAll(): Flow<List<AutomationEntity>>

    @Insert
    suspend fun insert(automation: AutomationEntity)

    @Delete
    suspend fun delete(automation: AutomationEntity)
}