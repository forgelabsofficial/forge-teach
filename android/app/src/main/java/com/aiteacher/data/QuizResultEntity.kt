package com.aiteacher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_results")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val topic: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercent: Int,
    val timeTakenSeconds: Int,
    val createdAt: Long = System.currentTimeMillis()
)
