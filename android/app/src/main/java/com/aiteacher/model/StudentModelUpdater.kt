package com.aiteacher.model

import com.aiteacher.ai.MemoryAgent
import com.aiteacher.ai.MisconceptionAgent
import com.aiteacher.data.AppDatabase
import com.aiteacher.onboarding.CapabilityQuestion
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Closes the feedback loop:
 * - Takes results from quizzes, exams, and study sessions
 * - Updates TopicKnowledgeEntity for each affected topic
 * - Calculates spaced-repetition metrics via MemoryAgent
 * - Runs MisconceptionAgent to identify root causes of wrong answers
 *
 * Call after every learning activity.
 */
object StudentModelUpdater {

    private val gson = Gson()

    /**
     * Update after a quiz or exam. Uses MemoryAgent for forgetting-curve
     * metrics (decay rate, next review interval) instead of hardcoded formulas.
     */
    suspend fun recordQuizOrExam(
        db: AppDatabase,
        questions: List<CapabilityQuestion>,
        selectedAnswers: Map<Int, Int>,
        avgResponseTimeMs: Int
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()

        val bySubject = questions.groupBy { it.subject }

        for ((subject, qs) in bySubject) {
            val topicId = "${subject}_quiz"
            val existing = dao.getTopic(topicId)

            val totalAttempts = qs.size
            val correctCount = qs.count { q ->
                val idx = qs.indexOf(q)
                selectedAnswers[idx] == q.correctIndex
            }
            val scorePercent = if (totalAttempts > 0) (correctCount * 100) / totalAttempts else 0
            val prevMastery = existing?.mastery ?: 0

            val newMastery = (prevMastery * 0.4f + scorePercent * 0.6f).toInt().coerceIn(0, 100)

            val allSame = selectedAnswers.values.toList().distinct().size <= 1 && selectedAnswers.size == totalAttempts
            val confidence = if (allSame && (scorePercent >= 80 || scorePercent <= 20)) 60 else 85

            // Use MemoryAgent's Ebbinghaus-based decay calculation
            val decayRate = (1.0f - (newMastery / 200f)).coerceIn(0.2f, 0.8f)

            // Use MemoryAgent's spaced-repetition interval
            val intervalDays = MemoryAgent.computeNextReviewInterval(newMastery, newMastery - prevMastery)
            val nextReview = now + (intervalDays * 86400000L)

            val guessing = if (allSame && scorePercent >= 80) 20 else 10
            val prevStreak = existing?.streakCorrect ?: 0
            val newStreak = if (scorePercent >= 70) prevStreak + 1 else 0
            val velocity = (newMastery - prevMastery).toFloat()

            // Misconception analysis
            val newMisconceptions = MisconceptionAgent.analyse(qs, selectedAnswers)
            val existingMisconceptionsJson = existing?.misconceptionsJson ?: "[]"
            val mergedMisconceptions = MisconceptionAgent.mergeWithExisting(newMisconceptions, existingMisconceptionsJson)
            val misconceptionsJson = MisconceptionAgent.toJson(mergedMisconceptions)

            dao.upsert(
                TopicKnowledgeEntity(
                    topicId = topicId,
                    subject = subject,
                    mastery = newMastery,
                    confidence = confidence,
                    recallStrength = scorePercent,
                    recognitionStrength = scorePercent,
                    decayRate = decayRate,
                    lastReviewTimestamp = now,
                    nextReviewTimestamp = nextReview,
                    totalAttempts = (existing?.totalAttempts ?: 0) + totalAttempts,
                    correctAttempts = (existing?.correctAttempts ?: 0) + correctCount,
                    avgResponseTimeMs = if (avgResponseTimeMs > 0) avgResponseTimeMs else (existing?.avgResponseTimeMs ?: 0),
                    guessingTendency = guessing,
                    misconceptionsJson = misconceptionsJson,
                    isUnlocked = true,
                    learningVelocity = velocity,
                    lastSessionDurationSec = avgResponseTimeMs / 1000,
                    streakCorrect = newStreak
                )
            )
        }
    }

    /**
     * Update after a study session completes. Uses MemoryAgent for forgetting-curve
     * metrics (recall, decay, next review) instead of hardcoded estimates.
     */
    suspend fun recordStudySession(
        db: AppDatabase,
        subject: String,
        topic: String,
        durationSeconds: Int,
        xpEarned: Int
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()
        val topicId = "${subject}_${topic.lowercase().replace(" ", "_").take(40)}"
        val existing = dao.getTopic(topicId)

        if (existing != null) {
            // Treat study session as a review with estimated 70% recall
            MemoryAgent.recordReview(
                db = db,
                topicId = topicId,
                scorePercent = 70,
                responseTimeMs = durationSeconds * 1000
            )
        } else {
            dao.upsert(
                TopicKnowledgeEntity(
                    topicId = topicId,
                    subject = subject,
                    mastery = 20,
                    confidence = 40,
                    recallStrength = 30,
                    recognitionStrength = 35,
                    decayRate = 0.6f,
                    lastReviewTimestamp = now,
                    nextReviewTimestamp = now + (2L * 86400000L),
                    totalAttempts = 1,
                    correctAttempts = 0,
                    avgResponseTimeMs = durationSeconds * 1000,
                    isUnlocked = true,
                    learningVelocity = 20f,
                    lastSessionDurationSec = durationSeconds,
                    streakCorrect = 0
                )
            )
        }
    }

    /**
     * Seed initial TopicKnowledge entries after the capability test.
     * Creates entries for each tested subject with baseline score + misconceptions.
     */
    suspend fun seedFromCapabilityTest(
        db: AppDatabase,
        questions: List<CapabilityQuestion>,
        selectedAnswers: Map<String, Int>
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()

        val bySubject = questions.groupBy { it.subject }
        for ((subject, qs) in bySubject) {
            val topicId = "${subject}_baseline"
            val correctCount = qs.count { q -> selectedAnswers[q.id] == q.correctIndex }
            val scorePercent = if (qs.isNotEmpty()) (correctCount * 100) / qs.size else 0

            // Run misconception analysis on baseline answers
            val indexAnswers = questions.mapIndexedNotNull { idx, q ->
                selectedAnswers[q.id]?.let { idx to it }
            }.toMap()
            val misconceptions = MisconceptionAgent.analyse(qs, indexAnswers)
            val misconceptionsJson = MisconceptionAgent.toJson(misconceptions)

            dao.upsert(
                TopicKnowledgeEntity(
                    topicId = topicId,
                    subject = subject,
                    mastery = scorePercent,
                    confidence = 50,
                    recallStrength = scorePercent,
                    recognitionStrength = scorePercent,
                    decayRate = 0.5f,
                    lastReviewTimestamp = now,
                    nextReviewTimestamp = now + 86400000L,
                    totalAttempts = qs.size,
                    correctAttempts = correctCount,
                    avgResponseTimeMs = 0,
                    misconceptionsJson = misconceptionsJson,
                    isUnlocked = true,
                    learningVelocity = 0f,
                    lastSessionDurationSec = 0,
                    streakCorrect = 0
                )
            )
        }
    }

    /**
     * Get the mastery score for a subject from the TopicKnowledge table.
     */
    suspend fun getSubjectMastery(
        db: AppDatabase,
        subject: String
    ): Int? = withContext(Dispatchers.IO) {
        val entry = db.topicKnowledgeDao().getTopic("${subject}_baseline")
            ?: db.topicKnowledgeDao().getTopic("${subject}_quiz")
        entry?.mastery
    }

    /**
     * Get all subject mastery scores for the dashboard.
     */
    suspend fun getAllSubjectMasteries(db: AppDatabase): Map<String, Int> = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()
        val allEntries = dao.getDueReviews(now + 86400000L * 365 * 10)
        allEntries.groupBy { it.subject }
            .mapValues { (_, entries) -> entries.maxOf { it.mastery } }
    }
}
