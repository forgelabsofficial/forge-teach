package com.aiteacher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val subject: String,
    val plannedDurationMinutes: Int,
    val actualDurationSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val xpEarned: Int = 0
)
