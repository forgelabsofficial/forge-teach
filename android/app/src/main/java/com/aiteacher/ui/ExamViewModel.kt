package com.aiteacher.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.CapabilityTestClient
import com.aiteacher.ai.ReflectionAgent
import com.aiteacher.ai.XpEngine
import com.aiteacher.data.AppDatabase
import com.aiteacher.data.ExamResultEntity
import com.aiteacher.model.StudentModelUpdater
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.StudentProfile
import com.aiteacher.ui.DataStoreUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExamState { CONFIG, LOADING, IN_PROGRESS, PAUSED, FINISHED }

data class ExamConfig(
    val examName: String,
    val subjects: List<String>,
    val durationMinutes: Int,
    val questionCount: Int
)

class ExamViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)

    private val _state = MutableStateFlow(ExamState.CONFIG)
    val state: StateFlow<ExamState> = _state

    private val _config = MutableStateFlow<ExamConfig?>(null)
    val config: StateFlow<ExamConfig?> = _config

    private val _questions = MutableStateFlow<List<CapabilityQuestion>>(emptyList())
    val questions: StateFlow<List<CapabilityQuestion>> = _questions

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    private val _scorePercent = MutableStateFlow(0)
    val scorePercent: StateFlow<Int> = _scorePercent

    private val _subjectScores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val subjectScores: StateFlow<Map<String, Int>> = _subjectScores

    private val _xpEarned = MutableStateFlow(0)
    val xpEarned: StateFlow<Int> = _xpEarned

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var timerJob: Job? = null

    fun startExam(ctx: Context, cfg: ExamConfig, profile: StudentProfile) {
        _config.value = cfg
        _state.value = ExamState.LOADING
        _error.value = null
        _selectedAnswers.value = emptyMap()
        _currentIndex.value = 0

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CapabilityTestClient.generateTestWithSource(ctx, profile, cfg.subjects)
                }
                _questions.value = result.test.questions.take(cfg.questionCount)
                _remainingSeconds.value = cfg.durationMinutes * 60
                _state.value = ExamState.IN_PROGRESS
                startTimer()
            } catch (e: Exception) {
                _error.value = "Couldn't load exam: ${e.message}"
                _state.value = ExamState.CONFIG
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0 && _state.value == ExamState.IN_PROGRESS) {
                delay(1000)
                _remainingSeconds.value--
            }
            if (_remainingSeconds.value == 0 && _state.value == ExamState.IN_PROGRESS) {
                finishExam()
            }
        }
    }

    fun pauseResume() {
        if (_state.value == ExamState.IN_PROGRESS) {
            timerJob?.cancel()
            _state.value = ExamState.PAUSED
        } else if (_state.value == ExamState.PAUSED) {
            _state.value = ExamState.IN_PROGRESS
            startTimer()
        }
    }

    fun selectAnswer(questionIndex: Int, answerIndex: Int) {
        _selectedAnswers.value = _selectedAnswers.value + (questionIndex to answerIndex)
    }

    fun navigateTo(index: Int) {
        _currentIndex.value = index.coerceIn(0, _questions.value.lastIndex)
    }

    fun submitExam() { finishExam() }

    private fun finishExam() {
        timerJob?.cancel()
        val qs = _questions.value
        val answers = _selectedAnswers.value
        val cfg = _config.value ?: return

        val correct = qs.count { q -> answers[qs.indexOf(q)] == q.correctIndex }
        val scorePercent = if (qs.isNotEmpty()) (correct * 100) / qs.size else 0

        // Per-subject scores
        val subjectMap = qs.groupBy { it.subject }
        val subjectScores = subjectMap.mapValues { (_, subQs) ->
            val subCorrect = subQs.count { q -> answers[qs.indexOf(q)] == q.correctIndex }
            if (subQs.isNotEmpty()) (subCorrect * 100) / subQs.size else 0
        }

        val xp = XpEngine.xpForExam(scorePercent)
        _scorePercent.value = scorePercent
        _subjectScores.value = subjectScores
        _xpEarned.value = xp
        _state.value = ExamState.FINISHED

        viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            db.examResultDao().insert(
                ExamResultEntity(
                    examName = cfg.examName,
                    subjects = cfg.subjects.joinToString(","),
                    totalQuestions = qs.size,
                    correctAnswers = correct,
                    scorePercent = scorePercent,
                    durationSeconds = cfg.durationMinutes * 60 - _remainingSeconds.value,
                    subjectScoresJson = Gson().toJson(subjectScores)
                )
            )
            DataStoreUtils.recordActivity(ctx, xp)

            // Update student model with exam results per subject
            StudentModelUpdater.recordQuizOrExam(
                db = db,
                questions = qs,
                selectedAnswers = answers,
                avgResponseTimeMs = (cfg.durationMinutes * 60 - _remainingSeconds.value) * 1000
            )

            // ReflectionAgent: update per-subject student model
            subjectScores.forEach { (subject, subScore) ->
                ReflectionAgent.reflect(
                    db = db,
                    subject = subject,
                    topic = "${subject}_exam",
                    scorePercent = subScore,
                    responseTimeMs = if (subjectScores.isNotEmpty()) (cfg.durationMinutes * 60 - _remainingSeconds.value) * 1000 / subjectScores.size else 0,
                    isGraded = true,
                    activityType = "exam"
                )
            }
        }
    }

    fun reset() {
        timerJob?.cancel()
        _state.value = ExamState.CONFIG
        _questions.value = emptyList()
        _selectedAnswers.value = emptyMap()
        _currentIndex.value = 0
        _remainingSeconds.value = 0
        _scorePercent.value = 0
        _subjectScores.value = emptyMap()
        _xpEarned.value = 0
        _error.value = null
    }
}
