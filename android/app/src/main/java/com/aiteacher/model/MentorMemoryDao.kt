package com.aiteacher.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MentorMemoryDao {

    @Query("SELECT * FROM mentor_memory ORDER BY importanceScore DESC, updatedTimestamp DESC")
    suspend fun getAllMemories(): List<MentorMemoryEntity>

    @Query("SELECT * FROM mentor_memory WHERE category = :category ORDER BY importanceScore DESC")
    suspend fun getMemoriesByCategory(category: String): List<MentorMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MentorMemoryEntity)

    @Query("DELETE FROM mentor_memory WHERE memoryKey = :key")
    suspend fun deleteMemory(key: String)
}
