package com.aiteacher.model

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
 * - Re-weights weakness scores via WeaknessReweighter
 * - Calculates spaced-repetition metrics (decay rate, next review)
 * - Runs MisconceptionAgent to identify root causes of wrong answers
 *
 * Call after every learning activity.
 */
object StudentModelUpdater {

    private val gson = Gson()

    /**
     * Update after a quiz or exam. Each question answered correctly/incorrectly
     * updates the corresponding topic's knowledge state.
     */
    suspend fun recordQuizOrExam(
        db: AppDatabase,
        questions: List<CapabilityQuestion>,
        selectedAnswers: Map<Int, Int>,
        avgResponseTimeMs: Int
    ) = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()

        // Group questions by subject
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

            // Blend: new result weighted 60%, existing 40%
            val newMastery = (prevMastery * 0.4f + scorePercent * 0.6f).toInt().coerceIn(0, 100)

            // Confidence: inverse of variance between answers
            val allSame = selectedAnswers.values.toList().distinct().size <= 1 && selectedAnswers.size == totalAttempts
            val confidence = if (allSame && (scorePercent >= 80 || scorePercent <= 20)) 60 else 85

            // Decay rate: lower mastery = faster decay
            val decayRate = (1.0f - (newMastery / 200f)).coerceIn(0.2f, 0.8f)

            // Next review: mastery < 60 -> review soon (1 day), else 3-7 days out
            val daysUntilReview = if (newMastery < 60) 1L else if (newMastery < 80) 3L else 7L
            val nextReview = now + (daysUntilReview * 86400000L)

            // Calculate guessing tendency: if all answers same pattern, higher guess chance
            val guessing = if (allSame && scorePercent >= 80) 20 else 10

            // Update streak
            val prevStreak = existing?.streakCorrect ?: 0
            val newStreak = if (scorePercent >= 70) prevStreak + 1 else 0

            // Learning velocity: change in mastery per session
            val velocity = (newMastery - prevMastery).toFloat()

            // Run MisconceptionAgent to analyse wrong answers
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
     * Update after a study session completes. Boosts mastery slightly, refreshes recall.
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

        // Study sessions boost recall + mastery modestly
        val prevMastery = existing?.mastery ?: 0
        val newMastery = (prevMastery + 3).coerceIn(0, 100)        // +3% per session
        val prevRecall = existing?.recallStrength ?: 0
        val newRecall = (prevRecall + 5).coerceIn(0, 100)           // +5% recall boost

        // Decay rate drops slightly because we just reviewed
        val prevDecay = existing?.decayRate ?: 0.5f
        val newDecay = (prevDecay * 0.9f).coerceIn(0.1f, 0.8f)

        // Next review: 2 days out (sooner than quiz because lighter engagement)
        val nextReview = now + (2L * 86400000L)

        dao.upsert(
            TopicKnowledgeEntity(
                topicId = topicId,
                subject = subject,
                mastery = newMastery,
                confidence = existing?.confidence ?: 40,
                recallStrength = newRecall,
                recognitionStrength = existing?.recognitionStrength ?: 0,
                decayRate = newDecay,
                lastReviewTimestamp = now,
                nextReviewTimestamp = nextReview,
                totalAttempts = existing?.totalAttempts ?: 0,
                correctAttempts = existing?.correctAttempts ?: 0,
                avgResponseTimeMs = existing?.avgResponseTimeMs ?: 0,
                guessingTendency = existing?.guessingTendency ?: 0,
                isUnlocked = true,
                learningVelocity = (newMastery - prevMastery).toFloat(),
                lastSessionDurationSec = durationSeconds,
                streakCorrect = existing?.streakCorrect ?: 0
            )
        )
    }

    /**
     * Seed initial TopicKnowledge entries after the capability test.
     * Creates entries for each tested subject with the baseline score.
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
                    nextReviewTimestamp = now + 86400000L, // review in 1 day
                    totalAttempts = qs.size,
                    correctAttempts = correctCount,
                    avgResponseTimeMs = 0,
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
     * Returns null if no data exists yet.
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
     * Queries known topic keys for all subjects, returns highest mastery per subject.
     */
    suspend fun getAllSubjectMasteries(db: AppDatabase): Map<String, Int> = withContext(Dispatchers.IO) {
        val dao = db.topicKnowledgeDao()
        val now = Instant.now().toEpochMilli()

        // Use getDueReviews with a far-future timestamp to get all entries
        val allEntries = dao.getDueReviews(now + 86400000L * 365 * 10)
        allEntries.groupBy { it.subject }
            .mapValues { (_, entries) -> entries.maxOf { it.mastery } }
    }

    /**
     * Convert misconceptions JSON to list of strings.
     */
    fun parseMisconceptions(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { emptyList() }
    }
}
