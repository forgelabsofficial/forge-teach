package com.aiteacher.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.CapabilityTestClient
import com.aiteacher.ai.WeaknessReweighter
import com.aiteacher.ai.XpEngine
import com.aiteacher.data.AppDatabase
import com.aiteacher.data.PlanRepository
import com.aiteacher.data.QuizResultEntity
import com.aiteacher.model.StudentModelUpdater
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.RankedTopic
import com.aiteacher.onboarding.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class QuizState { IDLE, LOADING, IN_PROGRESS, FINISHED }

class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(QuizState.IDLE)
    val state: StateFlow<QuizState> = _state

    private val _questions = MutableStateFlow<List<CapabilityQuestion>>(emptyList())
    val questions: StateFlow<List<CapabilityQuestion>> = _questions

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score

    private val _xpEarned = MutableStateFlow(0)
    val xpEarned: StateFlow<Int> = _xpEarned

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var startTimeMs = 0L
    private var currentSubject = ""
    private var currentTopic = ""

    fun startQuiz(ctx: Context, subject: String, topic: String, profile: StudentProfile) {
        currentSubject = subject
        currentTopic = topic
        _state.value = QuizState.LOADING
        _error.value = null
        _selectedAnswers.value = emptyMap()
        _currentIndex.value = 0

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CapabilityTestClient.generateTestWithSource(ctx, profile, listOf(subject))
                }
                _questions.value = result.test.questions.take(10)
                startTimeMs = System.currentTimeMillis()
                _state.value = QuizState.IN_PROGRESS
            } catch (e: Exception) {
                _error.value = "Couldn't load quiz: ${e.message}"
                _state.value = QuizState.IDLE
            }
        }
    }

    fun selectAnswer(questionIndex: Int, answerIndex: Int) {
        _selectedAnswers.value = _selectedAnswers.value + (questionIndex to answerIndex)
    }

    fun nextQuestion() {
        if (_currentIndex.value < _questions.value.lastIndex) {
            _currentIndex.value++
        } else {
            finishQuiz()
        }
    }

    private fun finishQuiz() {
        val qs = _questions.value
        val answers = _selectedAnswers.value
        val correct = qs.count { q ->
            val idx = qs.indexOf(q)
            answers[idx] == q.correctIndex
        }
        val scorePercent = if (qs.isNotEmpty()) (correct * 100) / qs.size else 0
        val timeSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
        val xp = XpEngine.xpForQuiz(scorePercent)

        _score.value = scorePercent
        _xpEarned.value = xp
        _state.value = QuizState.FINISHED

        viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            db.quizResultDao().insert(
                QuizResultEntity(
                    subject = currentSubject,
                    topic = currentTopic,
                    totalQuestions = qs.size,
                    correctAnswers = correct,
                    scorePercent = scorePercent,
                    timeTakenSeconds = timeSec
                )
            )
            DataStoreUtils.recordActivity(ctx, xp)

            // Update student model with quiz results
            StudentModelUpdater.recordQuizOrExam(
                db = db,
                questions = qs,
                selectedAnswers = answers,
                avgResponseTimeMs = timeSec * 1000
            )

            // Re-weight topic rankings based on quiz performance
            val repo = PlanRepository(ctx)
            val latestPlan = repo.loadLatestPlan()
            if (latestPlan != null) {
                // Build ranked topics from the plan's sessions
                val rankedTopics = latestPlan.sessions.mapIndexed { idx, s ->
                    RankedTopic(
                        id = s.topic,
                        title = s.topic,
                        subject = s.subject,
                        rank = idx + 1,
                        weaknessScore = 100 - (db.topicKnowledgeDao().getTopic("${s.subject}_quiz")?.mastery ?: 50),
                        importanceScore = 50,
                        dependencyScore = 50,
                        currentTermScore = 50,
                        suggestedSessionCount = 1,
                        dependsOn = emptyList(),
                        isCurrentTerm = false
                    )
                }
                WeaknessReweighter.reweight(rankedTopics, currentSubject, scorePercent)
                // Note: Reweighted topics should be fed back into PlanAgent on next regeneration.
                // Currently they are computed but not persisted — will be integrated with agent pipeline.
            }
        }
    }

    fun reset() {
        _state.value = QuizState.IDLE
        _questions.value = emptyList()
        _selectedAnswers.value = emptyMap()
        _currentIndex.value = 0
        _score.value = 0
        _xpEarned.value = 0
        _error.value = null
    }
}
