package com.aiteacher.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Long-term persistent memory for the AI Mentor.
 * Remembers student goals, fears, breakthrough moments, and teaching preferences across weeks.
 */
@Entity(tableName = "mentor_memory")
data class MentorMemoryEntity(
    @PrimaryKey val memoryKey: String,         // e.g. "fear_fractions", "goal_jamb_exam"
    val category: String,                       // "fear", "goal", "breakthrough", "preference"
    val content: String,                        // e.g. "Struggles with negative denominator concepts"
    val importanceScore: Int = 50,              // 1-100 (higher = prioritized in mentor system prompt)
    val updatedTimestamp: Long = System.currentTimeMillis()
)
