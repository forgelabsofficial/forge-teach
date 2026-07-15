package com.aiteacher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ProgressDao {
    @Insert
    suspend fun insertProgress(progress: ProgressEntity): Long

    @Query("SELECT * FROM progress WHERE sessionId = :sessionId")
    suspend fun getProgressForSession(sessionId: Long): List<ProgressEntity>

    @androidx.room.Update
    suspend fun updateProgress(progress: ProgressEntity): Int
}
