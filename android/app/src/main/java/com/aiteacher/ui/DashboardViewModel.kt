package com.aiteacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.XpEngine
import com.aiteacher.data.AppDatabase
import com.aiteacher.data.PlanRepository
import com.aiteacher.data.StudySessionEntity
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import com.aiteacher.onboarding.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime

enum class DashboardMode { STUDY, TEST, EXAM }

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PlanRepository(application.applicationContext)
    private val db   = AppDatabase.getInstance(application)

    private val _mode = MutableStateFlow(DashboardMode.STUDY)
    val mode: StateFlow<DashboardMode> = _mode

    private val _plan = MutableStateFlow<Plan?>(null)
    val plan: StateFlow<Plan?> = _plan

    private val _studentName = MutableStateFlow("Student")
    val studentName: StateFlow<String> = _studentName

    private val _studentProfile = MutableStateFlow<StudentProfile?>(null)
    val studentProfile: StateFlow<StudentProfile?> = _studentProfile

    private val _recentQuizzes = MutableStateFlow<List<com.aiteacher.data.QuizResultEntity>>(emptyList())
    val recentQuizzes: StateFlow<List<com.aiteacher.data.QuizResultEntity>> = _recentQuizzes

    private val _lastExam = MutableStateFlow<com.aiteacher.data.ExamResultEntity?>(null)
    val lastExam: StateFlow<com.aiteacher.data.ExamResultEntity?> = _lastExam

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    private val _totalXp = MutableStateFlow(0)
    val totalXp: StateFlow<Int> = _totalXp

    val level: Int get() = XpEngine.levelFromXp(_totalXp.value)
    val xpToNext: Int get() = XpEngine.xpToNextLevel(_totalXp.value)
    val levelProgress: Float get() = XpEngine.progressInLevel(_totalXp.value)

    private val _completedSessionsSet = MutableStateFlow<Set<String>>(emptySet())
    val completedSessionsSet: StateFlow<Set<String>> = _completedSessionsSet

    private val _subjectScores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val subjectScores: StateFlow<Map<String, Int>> = _subjectScores

    private val _todayFocus = MutableStateFlow<SessionItem?>(null)
    val todayFocus: StateFlow<SessionItem?> = _todayFocus

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Active study session timer
    private val _activeSession = MutableStateFlow<SessionItem?>(null)
    val activeSession: StateFlow<SessionItem?> = _activeSession

    private val _sessionElapsedSeconds = MutableStateFlow(0)
    val sessionElapsedSeconds: StateFlow<Int> = _sessionElapsedSeconds

    private var sessionTimerJob: kotlinx.coroutines.Job? = null
    private var sessionStartMs = 0L

    init { loadData() }

    fun setMode(mode: DashboardMode) {
        _mode.value = mode
        viewModelScope.launch {
            DataStoreUtils.saveDashboardMode(getApplication(), mode.name.lowercase())
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val ctx = getApplication<Application>().applicationContext
                val data = withContext(Dispatchers.IO) {
                    val p = repo.loadLatestPlan()
                    val student = repo.getLatestStudentProfile()
                    val name = student?.name ?: "Student"
                    val profile = student?.let {
                        StudentProfile(
                            name = it.name,
                            countryCode = "", countryName = "",
                            systemId = "", gradeLevelId = "",
                            gradeLevelLabel = "", curriculumBody = ""
                        )
                    }
                    val total = p?.sessions?.size ?: 0
                    val completed = p?.sessions?.count {
                        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isBefore(Instant.now()) }
                        catch (_: Exception) { false }
                    } ?: 0

                    // Subject scores from quiz results
                    val quizDao = db.quizResultDao()
                    val allQuizResults = quizDao.getAll()
                    val subjectMap = allQuizResults.groupBy { it.subject }
                        .mapValues { (_, results) -> results.map { it.scorePercent }.average().toInt() }

                    // Today's focus: highest-rank upcoming session
                    val focus = p?.sessions?.filter {
                        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isAfter(Instant.now()) }
                        catch (_: Exception) { true }
                    }?.minByOrNull { it.topicRank.takeIf { r -> r > 0 } ?: Int.MAX_VALUE }

                    val streak = DataStoreUtils.getStreak(ctx)
                    val xp = DataStoreUtils.getTotalXp(ctx)
                    val savedMode = DataStoreUtils.getDashboardMode(ctx)

                    val recentQuizzes = db.quizResultDao().getAll().take(5)
                    val lastExam = db.examResultDao().getLatest()
                    DashData(p, name, profile, completed, total, subjectMap, focus, streak, xp, savedMode, recentQuizzes, lastExam)
                }
                _plan.value = data.plan
                _studentName.value = data.studentName
                _studentProfile.value = data.profile
                _completedCount.value = data.completed
                _totalCount.value = data.total
                _subjectScores.value = data.subjectScores
                _todayFocus.value = data.todayFocus
                _streak.value = data.streak
                _totalXp.value = data.xp
                _recentQuizzes.value = data.recentQuizzes
                _lastExam.value = data.lastExam
                _mode.value = when (data.savedMode) {
                    "test" -> DashboardMode.TEST
                    "exam" -> DashboardMode.EXAM
                    else   -> DashboardMode.STUDY
                }
            } catch (e: Exception) {
                _error.value = "Couldn't load your data. Tap to retry."
            } finally {
                _loading.value = false
            }
        }
    }

    fun startStudySession(session: SessionItem) {
        _activeSession.value = session
        sessionStartMs = System.currentTimeMillis()
        _sessionElapsedSeconds.value = 0
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _sessionElapsedSeconds.value++
            }
        }
    }

    fun endStudySession() {
        sessionTimerJob?.cancel()
        val session = _activeSession.value ?: return
        val elapsed = _sessionElapsedSeconds.value
        val xp = XpEngine.XP_SESSION_COMPLETE

        viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            db.studySessionDao().insert(
                StudySessionEntity(
                    topic = session.topic,
                    subject = session.subject,
                    plannedDurationMinutes = session.duration,
                    actualDurationSeconds = elapsed,
                    startedAt = sessionStartMs,
                    endedAt = System.currentTimeMillis(),
                    xpEarned = xp
                )
            )
            DataStoreUtils.recordActivity(ctx, xp)
            _totalXp.value += xp
        }
        _activeSession.value = null
        _sessionElapsedSeconds.value = 0
    }
}

private data class DashData(
    val plan: Plan?,
    val studentName: String,
    val profile: StudentProfile?,
    val completed: Int,
    val total: Int,
    val subjectScores: Map<String, Int>,
    val todayFocus: SessionItem?,
    val streak: Int,
    val xp: Int,
    val savedMode: String,
    val recentQuizzes: List<com.aiteacher.data.QuizResultEntity>,
    val lastExam: com.aiteacher.data.ExamResultEntity?
)
