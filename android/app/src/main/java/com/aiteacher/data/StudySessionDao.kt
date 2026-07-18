package com.aiteacher.data

import androidx.room.*

@Dao
interface StudySessionDao {
    @Insert
    suspend fun insert(session: StudySessionEntity): Long

    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<StudySessionEntity>

    @Query("SELECT SUM(actualDurationSeconds) FROM study_sessions")
    suspend fun totalSecondsStudied(): Long?

    @Query("SELECT * FROM study_sessions WHERE startedAt >= :fromEpoch ORDER BY startedAt DESC")
    suspend fun getSince(fromEpoch: Long): List<StudySessionEntity>
}
