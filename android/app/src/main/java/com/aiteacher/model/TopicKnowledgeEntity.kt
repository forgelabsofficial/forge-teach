package com.aiteacher.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-topic knowledge state — the core of the Student Model.
 * Updated by every agent after any learning activity.
 */
@Entity(tableName = "topic_knowledge")
data class TopicKnowledgeEntity(
    @PrimaryKey val topicId: String,           // e.g. "math_fractions"
    val subject: String,
    val mastery: Int = 0,                     // 0-100
    val confidence: Int = 0,                  // 0-100 (how sure we are about mastery)
    val recallStrength: Int = 0,              // 0-100 (Ebbinghaus recall)
    val recognitionStrength: Int = 0,         // 0-100 (multiple-choice recognition)
    val decayRate: Float = 0.5f,              // per day (0=never forget, 1=forget instantly)
    val lastReviewTimestamp: Long = 0,
    val nextReviewTimestamp: Long = 0,        // when MemoryAgent says to re-test
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0,
    val avgResponseTimeMs: Int = 0,
    val guessingTendency: Int = 0,            // 0-100 (higher = more lucky guesses)
    val misconceptionsJson: String = "[]",    // JSON array of misconception IDs
    val isUnlocked: Boolean = true,
    val isBlocked: Boolean = false,
    val transferScore: Int = 0,               // 0-100 (knowledge transfer to other subjects)
    val learningVelocity: Float = 0f,         // mastery change per session
    val stressScore: Int = 0,                 // 0-100
    val lastSessionDurationSec: Int = 0,
    val streakCorrect: Int = 0               // consecutive correct answers
)
