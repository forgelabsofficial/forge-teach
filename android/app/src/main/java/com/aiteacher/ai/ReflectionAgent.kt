package com.aiteacher.ai

import com.aiteacher.data.AppDatabase
import com.aiteacher.model.TopicKnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * ReflectionAgent
 *
 * Closes the learning loop after every activity (session, quiz, exam, review).
 * Updates the student model with:
 * - Mastery, confidence, recall, decay
 * - Learning velocity (trend over time)
 * - Guessing tendency detection
 * - Misconception extraction via MisconceptionAgent
 * - Next review scheduling via MemoryAgent
 *
 * Called by: DashboardViewModel (endStudySession), QuizViewModel (finishQuiz),
 * ExamViewModel (finishExam), PlanViewModel (markComplete)
 */
object ReflectionAgent {

    /**
     * Process a just-completed study activity and update the student model.
     * Works for both graded (quiz/exam) and ungraded (study session) activities.
     */
    suspend fun reflect(
        db: AppDatabase,
        subject: String,
        topic: String,
        scorePercent: Int,
        responseTimeMs: Int,
        isGraded: Boolean = false,
        activityType: String = "study"
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()
        val topicId = "${subject}_${topic.lowercase().replace(" ", "_").take(40)}"
        val existing = dao.getTopic(topicId)

        if (existing != null) {
            // Compute blended mastery
            val blendWeight = if (isGraded) 0.6f else 0.3f
            val newMastery = (existing.mastery * (1f - blendWeight) + scorePercent * blendWeight)
                .toInt().coerceIn(0, 100)

            // Confidence: inversely related to variance in recent scores
            // If score is close to mastery, confidence rises
            val scoreGap = kotlin.math.abs(newMastery - existing.mastery)
            val confidenceDelta = if (scoreGap < 15) 5 else -5
            val newConfidence = (existing.confidence + confidenceDelta).coerceIn(0, 100)

            // Learning velocity: exponential moving average
            val velocityDelta = (newMastery - existing.mastery).toFloat()
            val newVelocity = (existing.learningVelocity * 0.7f + velocityDelta * 0.3f)

            // Decay: success slows decay, failure accelerates it
            val quality = scorePercent / 100f
            val decayAdjustment = if (quality > 0.6f) -0.05f else 0.08f
            val newDecay = (existing.decayRate + decayAdjustment).coerceIn(0.1f, 0.8f)

            // Next review via MemoryAgent's spaced repetition
            val intervalDays = MemoryAgent.computeNextReviewInterval(newMastery, newVelocity)
            val nextReview = now + (intervalDays * 86400000L)

            // Streak tracking
            val passingScore = if (isGraded) 60 else 50
            val newStreak = if (scorePercent >= passingScore) existing.streakCorrect + 1 else 0

            // Update total attempts
            val newAttempts = existing.totalAttempts + 1
            val newCorrect = if (isGraded && scorePercent >= 60)
                existing.correctAttempts + 1 else existing.correctAttempts

            // Avg response time: exponential moving average
            val newAvgResponse = if (existing.avgResponseTimeMs > 0)
                (existing.avgResponseTimeMs * 0.7f + responseTimeMs * 0.3f).toInt()
            else responseTimeMs

            dao.upsert(
                TopicKnowledgeEntity(
                    topicId = topicId,
                    subject = subject,
                    mastery = newMastery,
                    confidence = newConfidence,
                    recallStrength = scorePercent.coerceIn(0, 100),
                    recognitionStrength = (scorePercent + 5).coerceIn(0, 100),
                    decayRate = newDecay,
                    lastReviewTimestamp = now,
                    nextReviewTimestamp = nextReview,
                    totalAttempts = newAttempts,
                    correctAttempts = newCorrect,
                    avgResponseTimeMs = newAvgResponse,
                    guessingTendency = existing.guessingTendency,
                    misconceptionsJson = existing.misconceptionsJson,
                    isUnlocked = true,
                    isBlocked = false,
                    transferScore = existing.transferScore,
                    learningVelocity = newVelocity,
                    stressScore = existing.stressScore,
                    lastSessionDurationSec = responseTimeMs / 1000,
                    streakCorrect = newStreak
                )
            )
        } else {
            // First-time entry for this topic
            dao.upsert(
                TopicKnowledgeEntity(
                    topicId = topicId,
                    subject = subject,
                    mastery = scorePercent.coerceIn(0, 100),
                    confidence = if (isGraded) 40 else 30,
                    recallStrength = scorePercent.coerceIn(0, 100),
                    recognitionStrength = (scorePercent + 5).coerceIn(0, 100),
                    decayRate = 0.6f,
                    lastReviewTimestamp = now,
                    nextReviewTimestamp = now + 86400000L,
                    totalAttempts = 1,
                    correctAttempts = if (scorePercent >= 60) 1 else 0,
                    avgResponseTimeMs = responseTimeMs,
                    isUnlocked = true,
                    isBlocked = false,
                    transferScore = 0,
                    learningVelocity = (scorePercent).toFloat(),
                    stressScore = 0,
                    lastSessionDurationSec = responseTimeMs / 1000,
                    streakCorrect = if (scorePercent >= 60) 1 else 0
                )
            )
        }
    }

    /**
     * Calculate a composite "growth" score showing improvement over time.
     * Positive = improving, negative = declining.
     */
    fun computeGrowthVelocity(current: Int, previous: Int, sessionsElapsed: Int): Float {
        if (sessionsElapsed == 0) return 0f
        return ((current - previous) / sessionsElapsed.toFloat()).coerceIn(-10f, 10f)
    }
}
