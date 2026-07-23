package com.aiteacher.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks student learning style preferences and empirical effectiveness over time.
 * Updated passively by LearningStyleAgent after each completed session.
 */
@Entity(tableName = "learning_styles")
data class LearningStyleEntity(
    @PrimaryKey val studentId: String = "default_student",
    val narrativeScore: Int = 50,       // 0-100: learns best via stories/real-world context
    val proceduralScore: Int = 50,      // 0-100: learns best via step-by-step checklists
    val analogyScore: Int = 50,         // 0-100: learns best via visual/spatial analogies
    val socraticScore: Int = 50,        // 0-100: learns best via challenge-first questions
    val activeRecallScore: Int = 50,    // 0-100: learns best via doing exercises > reading
    val dominantStyle: String = "analogy", // "narrative", "procedural", "analogy", "socratic", "active_recall"
    val sessionsTracked: Int = 0,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
