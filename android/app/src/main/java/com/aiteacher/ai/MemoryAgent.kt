package com.aiteacher.ai

import com.aiteacher.data.AppDatabase
import com.aiteacher.model.TopicKnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * MemoryAgent
 *
 * The vision says: "Instead of revision, it predicts forgetting."
 *
 * This agent:
 * 1. Queries all topics due for review (nextReviewTimestamp < now)
 * 2. Computes a review priority score based on:
 *    - How overdue the topic is (urgency)
 *    - Decay rate (faster decay = sooner review)
 *    - Current recall strength vs predicted recall
 *    - Mastery level (lower mastery = more urgent)
 * 3. Returns ranked "Memory Boost" suggestions for the plan
 * 4. Updates memory metrics after each review session
 */
object MemoryAgent {

    private const val MS_PER_DAY = 86400000L

    /**
     * Get topics due for review, ranked by urgency.
     * Returns a list ordered from most urgent to least.
     */
    suspend fun getDueReviews(db: AppDatabase): List<ReviewCandidate> = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()
        val due = dao.getDueReviews(now)

        due.mapNotNull { topic ->
            computePriority(topic, now)?.let { priority ->
                ReviewCandidate(
                    topicId = topic.topicId,
                    subject = topic.subject,
                    mastery = topic.mastery,
                    recallStrength = topic.recallStrength,
                    decayRate = topic.decayRate,
                    lastReviewTimestamp = topic.lastReviewTimestamp,
                    nextReviewTimestamp = topic.nextReviewTimestamp,
                    priorityScore = priority
                )
            }
        }.sortedByDescending { it.priorityScore }
    }

    /**
     * Predict the student's current recall strength using a simplified forgetting curve.
     *
     * Ebbinghaus-like: R = e^(-t / S)
     *   where R = recall probability (0-1)
     *         t = time since last review (hours)
     *         S = stability (inverse of decayRate, scaled)
     *
     * Returns 0-100 recall prediction.
     */
    fun predictRecallStrength(topic: TopicKnowledgeEntity): Int {
        val now = Instant.now().toEpochMilli()
        val hoursSinceReview = if (topic.lastReviewTimestamp > 0) {
            Duration.ofMillis(now - topic.lastReviewTimestamp).toHours().coerceAtLeast(0)
        } else 168L // 1 week if never reviewed

        // Stability: lower decay = more stable. Map decayRate (0.1-0.8) to stability (50-10 hours)
        val stability = ((1.0f - topic.decayRate) * 55f + 5f).coerceIn(5f, 60f)

        // Forgetting curve: R = e^(-hours/stability)
        val recallProb = Math.exp(-hoursSinceReview.toDouble() / stability.toDouble()).toFloat()

        return (recallProb * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Determine the optimal spacing interval for the next review based on
     * current mastery and performance trend (learning velocity).
     *
     * Spacing effect: intervals grow as mastery increases.
     * - mastery < 40: 1 day  (struggling, review soon)
     * - mastery < 60: 2 days (developing)
     * - mastery < 80: 4 days (competent)
     * - mastery >= 80: 7 days (strong, longer spacing)
     *
     * Positive learningVelocity → extend interval (learning faster)
     * Negative learningVelocity → shorten interval (struggling)
     */
    fun computeNextReviewInterval(mastery: Int, learningVelocity: Float): Long {
        val baseDays = when {
            mastery < 40 -> 1L
            mastery < 60 -> 2L
            mastery < 80 -> 4L
            else -> 7L
        }
        // Adjust: positive velocity = learning well, stretch interval up to 1.5x
        // Negative velocity = struggling, shorten by up to half
        val velocityMultiplier = (1.0f + (learningVelocity / 20f)).coerceIn(0.5f, 1.5f)
        return (baseDays * velocityMultiplier).toLong().coerceAtLeast(1L)
    }

    /**
     * Mark a topic as reviewed. Updates memory metrics to reflect the review session.
     * Called after a study session that was a Memory Boost / review.
     */
    suspend fun recordReview(
        db: AppDatabase,
        topicId: String,
        scorePercent: Int,
        responseTimeMs: Int
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val existing = dao.getTopic(topicId) ?: return@withContext
        val now = Instant.now().toEpochMilli()

        // Calculate new recall strength based on review performance
        val newRecall = (existing.recallStrength * 0.4f + scorePercent * 0.6f).toInt().coerceIn(0, 100)

        // Recognition: typically slightly higher than recall for same content
        val newRecognition = (newRecall + 5).coerceIn(0, 100)

        // Decay rate decreases after a successful review (memory gets stronger)
        val reviewQuality = scorePercent / 100f
        val decayReduction = reviewQuality * 0.15f // up to 15% reduction on perfect review
        val newDecay = (existing.decayRate * (1f - decayReduction)).coerceIn(0.1f, 0.8f)

        // Next review: spaced repetition with growing interval
        val intervalDays = computeNextReviewInterval(newRecall, existing.learningVelocity)
        val nextReview = now + (intervalDays * MS_PER_DAY)

        // Update streak
        val newStreak = if (scorePercent >= 60) existing.streakCorrect + 1 else 0

        dao.updateMemoryMetrics(
            topicId = topicId,
            recall = newRecall,
            recog = newRecognition,
            decay = newDecay,
            nextReview = nextReview
        )

        // Also update mastery slightly to reflect the review boost
        val newMastery = (existing.mastery + 5).coerceIn(0, 100)
        dao.updateMastery(
            topicId = topicId,
            mastery = newMastery,
            confidence = (existing.confidence + 3).coerceIn(0, 100),
            avgResponseMs = responseTimeMs,
            now = now
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private data class PriorityFactors(
        val overdueUrgency: Float,    // 0-1 how overdue
        val decayUrgency: Float,      // 0-1 how fast it decays
        val masteryUrgency: Float,    // 0-1 how low mastery is
        val recallGap: Float          // 0-1 gap between predicted and last known recall
    )

    private fun computePriority(topic: TopicKnowledgeEntity, now: Long): Float? {
        val predictedRecall = predictRecallStrength(topic)
        val recallGap = (topic.recallStrength - predictedRecall).coerceIn(0, 100) / 100f

        // How overdue (hours past the scheduled review time)
        val overdueHours = if (topic.nextReviewTimestamp > 0 && topic.nextReviewTimestamp < now) {
            Duration.ofMillis(now - topic.nextReviewTimestamp).toHours().coerceAtLeast(0)
        } else 0L
        val overdueUrgency = (overdueHours / 72f).coerceIn(0f, 1f) // max urgency at 3 days overdue

        // Decay urgency: higher decay = more urgent
        val decayUrgency = topic.decayRate

        // Mastery urgency: lower mastery = more urgent
        val masteryUrgency = 1f - (topic.mastery / 100f)

        // Composite score: weighted sum
        val score = (
            overdueUrgency * 0.35f +
            decayUrgency * 0.20f +
            masteryUrgency * 0.25f +
            recallGap * 0.20f
        )

        // Only return if there's meaningful urgency
        return if (score > 0.05f) score else null
    }
}

/**
 * A topic that is due or nearly due for review, with a computed priority score.
 */
data class ReviewCandidate(
    val topicId: String,
    val subject: String,
    val mastery: Int,
    val recallStrength: Int,
    val decayRate: Float,
    val lastReviewTimestamp: Long,
    val nextReviewTimestamp: Long,
    val priorityScore: Float
)
