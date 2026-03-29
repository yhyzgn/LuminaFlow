package com.lumina.flow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automation ORDER BY enabled DESC, updatedAt DESC")
    fun getAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automation WHERE enabled = 1")
    suspend fun getEnabled(): List<AutomationEntity>

    @Query("SELECT * FROM automation WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AutomationEntity?

    @Upsert
    suspend fun upsert(automation: AutomationEntity): Long

    @Delete
    suspend fun delete(automation: AutomationEntity)
}
