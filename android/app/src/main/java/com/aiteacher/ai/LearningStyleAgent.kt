package com.aiteacher.ai

import com.aiteacher.data.AppDatabase
import com.aiteacher.model.LearningStyleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LearningStyleAgent
 *
 * Passively tracks how a student absorbs information best (narrative, procedural,
 * analogy, socratic, activeRecall).
 *
 * Updates scores based on empirical session performance:
 * - High exercise score after story examples → boosts narrative
 * - High score after step-by-step checklists → boosts procedural
 * - High score after spatial analogies → boosts analogy
 * - Fast completion with high accuracy → boosts activeRecall
 */
object LearningStyleAgent {

    suspend fun getDominantStyle(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val existing = db.learningStyleDao().getStyleForStudent()
        existing?.dominantStyle ?: "analogy"
    }

    suspend fun processSessionBehavior(
        db: AppDatabase,
        lessonStyleUsed: String,
        lessonReadTimeSec: Int,
        exerciseScorePercent: Int,
        avgResponseTimeMs: Int,
        masteryDelta: Int
    ) = withContext(Dispatchers.IO) {
        val current = db.learningStyleDao().getStyleForStudent() ?: LearningStyleEntity()

        val isHighPerform = exerciseScorePercent >= 75 || masteryDelta > 5
        val weight = if (isHighPerform) 5 else -2

        var narrative = current.narrativeScore
        var procedural = current.proceduralScore
        var analogy = current.analogyScore
        var socratic = current.socraticScore
        var activeRecall = current.activeRecallScore

        when (lessonStyleUsed) {
            "narrative" -> narrative = (narrative + weight).coerceIn(0, 100)
            "procedural" -> procedural = (procedural + weight).coerceIn(0, 100)
            "analogy" -> analogy = (analogy + weight).coerceIn(0, 100)
            "socratic" -> socratic = (socratic + weight).coerceIn(0, 100)
            "active_recall" -> activeRecall = (activeRecall + weight).coerceIn(0, 100)
        }

        // Additional signal: fast response time + high performance = strong active recall affinity
        if (avgResponseTimeMs in 1000..5000 && isHighPerform) {
            activeRecall = (activeRecall + 3).coerceIn(0, 100)
        }

        // Additional signal: thorough read time = procedural/narrative affinity
        if (lessonReadTimeSec > 60) {
            procedural = (procedural + 2).coerceIn(0, 100)
        }

        // Determine new dominant style
        val scores = mapOf(
            "narrative" to narrative,
            "procedural" to procedural,
            "analogy" to analogy,
            "socratic" to socratic,
            "active_recall" to activeRecall
        )
        val newDominant = scores.maxByOrNull { it.value }?.key ?: "analogy"

        val updated = current.copy(
            narrativeScore = narrative,
            proceduralScore = procedural,
            analogyScore = analogy,
            socraticScore = socratic,
            activeRecallScore = activeRecall,
            dominantStyle = newDominant,
            sessionsTracked = current.sessionsTracked + 1,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        db.learningStyleDao().upsertStyle(updated)
    }
}
