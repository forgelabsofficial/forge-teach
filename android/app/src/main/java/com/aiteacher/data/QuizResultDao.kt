package com.aiteacher.data

import androidx.room.*

@Dao
interface QuizResultDao {
    @Insert
    suspend fun insert(result: QuizResultEntity): Long

    @Query("SELECT * FROM quiz_results ORDER BY createdAt DESC")
    suspend fun getAll(): List<QuizResultEntity>

    @Query("SELECT * FROM quiz_results WHERE subject = :subject ORDER BY createdAt DESC")
    suspend fun getBySubject(subject: String): List<QuizResultEntity>

    @Query("SELECT AVG(scorePercent) FROM quiz_results WHERE subject = :subject")
    suspend fun avgScoreForSubject(subject: String): Float?
}
