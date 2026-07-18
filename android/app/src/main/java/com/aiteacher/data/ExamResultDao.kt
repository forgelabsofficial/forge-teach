package com.aiteacher.data

import androidx.room.*

@Dao
interface ExamResultDao {
    @Insert
    suspend fun insert(result: ExamResultEntity): Long

    @Query("SELECT * FROM exam_results ORDER BY createdAt DESC")
    suspend fun getAll(): List<ExamResultEntity>

    @Query("SELECT * FROM exam_results ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): ExamResultEntity?
}
