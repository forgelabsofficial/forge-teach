package com.aiteacher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exam_results")
data class ExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examName: String,
    val subjects: String,           // comma-separated
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercent: Int,
    val durationSeconds: Int,
    val subjectScoresJson: String = "{}",  // JSON map subject->percent
    val createdAt: Long = System.currentTimeMillis()
)
