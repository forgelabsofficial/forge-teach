package com.aiteacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.ReflectionAgent
import com.aiteacher.ai.XpEngine
import com.aiteacher.data.AppDatabase
import com.aiteacher.data.PlanRepository
import com.aiteacher.data.StudySessionEntity
import com.aiteacher.model.StudentModelUpdater
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import com.aiteacher.onboarding.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class DashboardMode { STUDY, TEST, EXAM }

/**
 * Week-over-week stats for growth comparison on the dashboard.
 */
data class WeekComparison(
    val thisWeekSessions: Int = 0,
    val lastWeekSessions: Int = 0,
    val thisWeekMinutes: Int = 0,
    val lastWeekMinutes: Int = 0,
    val thisWeekXp: Int = 0,
    val lastWeekXp: Int = 0
) {
    val sessionChange: Float get() = if (lastWeekSessions > 0) (thisWeekSessions - lastWeekSessions).toFloat() / lastWeekSessions else 0f
    val minuteChange: Float get() = if (lastWeekMinutes > 0) (thisWeekMinutes - lastWeekMinutes).toFloat() / lastWeekMinutes else 0f
    val xpChange: Float get() = if (lastWeekXp > 0) (thisWeekXp - lastWeekXp).toFloat() / lastWeekXp else 0f
}

data class DashboardUiState(
    val plan: Plan? = null,
    val studentName: String = "Student",
    val profile: StudentProfile? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val subjectScores: Map<String, Int> = emptyMap(),
    val todayFocus: SessionItem? = null,
    val streak: Int = 0,
    val totalXp: Int = 0,
    val weekComparison: WeekComparison = WeekComparison(),
    val mentorMessage: String = "",
    val recentQuizzes: List<com.aiteacher.data.QuizResultEntity> = emptyList(),
    val lastExam: com.aiteacher.data.ExamResultEntity? = null,
    val mode: DashboardMode = DashboardMode.STUDY
)

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

    private val _weekComparison = MutableStateFlow(WeekComparison())
    val weekComparison: StateFlow<WeekComparison> = _weekComparison

    private val _mentorMessage = MutableStateFlow("")
    val mentorMessage: StateFlow<String> = _mentorMessage

    val uiState: StateFlow<DashboardUiState> get() = MutableStateFlow(
        DashboardUiState(
            plan = _plan.value,
            studentName = _studentName.value,
            profile = _studentProfile.value,
            completedCount = _completedCount.value,
            totalCount = _totalCount.value,
            subjectScores = _subjectScores.value,
            todayFocus = _todayFocus.value,
            streak = _streak.value,
            totalXp = _totalXp.value,
            weekComparison = _weekComparison.value,
            mentorMessage = _mentorMessage.value,
            recentQuizzes = _recentQuizzes.value,
            lastExam = _lastExam.value,
            mode = _mode.value
        )
    )

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

                    // Subject scores
                    val quizDao = db.quizResultDao()
                    val allQuizResults = quizDao.getAll()
                    val quizSubjectMap = allQuizResults.groupBy { it.subject }
                        .mapValues { (_, results) -> results.map { it.scorePercent }.average().toInt() }
                    val modelSubjectMap = StudentModelUpdater.getAllSubjectMasteries(db)
                    val allSubjects = (quizSubjectMap.keys + modelSubjectMap.keys).toSet()
                    val subjectMap = allSubjects.mapNotNull { subject ->
                        val modelScore = modelSubjectMap[subject]
                        val quizScore = quizSubjectMap[subject]
                        val blended = when {
                            modelScore != null && quizScore != null -> (modelScore * 0.6f + quizScore * 0.4f).toInt()
                            modelScore != null -> modelScore
                            quizScore != null -> quizScore
                            else -> null
                        }
                        blended?.let { subject to it }
                    }.toMap()

                    // Today's focus
                    val focus = p?.sessions?.filter {
                        try { OffsetDateTime.parse(it.isoDateTime ?: it.date).toInstant().isAfter(Instant.now()) }
                        catch (_: Exception) { true }
                    }?.minByOrNull { it.topicRank.takeIf { r -> r > 0 } ?: Int.MAX_VALUE }

                    val streak = DataStoreUtils.getStreak(ctx)
                    val xp = DataStoreUtils.getTotalXp(ctx)
                    val savedMode = DataStoreUtils.getDashboardMode(ctx)

                    val recentQuizzes = db.quizResultDao().getAll().take(5)
                    val lastExam = db.examResultDao().getLatest()

                    // ── Week-over-week comparison ───────────────────────────
                    val now = Instant.now()
                    val thisWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay().toInstant(ZoneOffset.UTC)
                    val lastWeekStart = thisWeekStart.minusSeconds(7 * 86400)
                    val twoWeeksAgo = thisWeekStart.minusSeconds(14 * 86400)

                    val sessions = db.studySessionDao().getSince(twoWeeksAgo.toEpochMilli())
                    val thisWeekSessions = sessions.filter { it.startedAt >= thisWeekStart.toEpochMilli() }
                    val lastWeekSessionsList = sessions.filter { it.startedAt in lastWeekStart.toEpochMilli() until thisWeekStart.toEpochMilli() }

                    val comparison = WeekComparison(
                        thisWeekSessions = thisWeekSessions.size,
                        lastWeekSessions = lastWeekSessionsList.size,
                        thisWeekMinutes = thisWeekSessions.sumOf { it.actualDurationSeconds / 60 },
                        lastWeekMinutes = lastWeekSessionsList.sumOf { it.actualDurationSeconds / 60 },
                        thisWeekXp = thisWeekSessions.sumOf { it.xpEarned },
                        lastWeekXp = lastWeekSessionsList.sumOf { it.xpEarned }
                    )

                    // ── AI Mentor message ───────────────────────────────────
                    val mentorMsg = generateMentorMessage(
                        name = name,
                        weekComparison = comparison,
                        streak = streak,
                        xp = xp
                    )

                    DashData(p, name, profile, completed, total, subjectMap, focus, streak, xp, savedMode, recentQuizzes, lastExam, comparison, mentorMsg)
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
                _weekComparison.value = data.weekComparison
                _mentorMessage.value = data.mentorMessage
            } catch (e: Exception) {
                _error.value = "Couldn't load your data. Tap to retry."
            } finally {
                _loading.value = false
            }
        }
    }

    private fun generateMentorMessage(
        name: String,
        weekComparison: WeekComparison,
        streak: Int,
        xp: Int
    ): String {
        val userName = name.ifBlank { "Scholar" }

        if (weekComparison.thisWeekSessions == 0 && weekComparison.lastWeekSessions == 0) {
            // New user or no history
            val openers = listOf(
                "Welcome to your forge, $userName. Every master was once a beginner.",
                "The journey of a thousand lessons begins with a single session, $userName.",
                "Your mind is a forge, $userName. Let's start shaping it."
            )
            return openers.random()
        }

        if (weekComparison.sessionChange > 0.3f) {
            return listOf(
                "Your consistency this week is remarkable, $userName. This is how mastery is built.",
                "More sessions this week than last — that's real discipline, $userName.",
                "You're forging new habits, $userName. Keep the chain unbroken."
            ).random()
        }

        if (streak >= 5) {
            return listOf(
                "A $streak-day streak! You're in the flow state, $userName. Ride this wave.",
                "$streak days strong. Each one compounds into the next. You're building a fortress of knowledge.",
                "Streak: $streak. Momentum is everything in learning. Don't stop now, $userName."
            ).random()
        }

        if (weekComparison.thisWeekSessions < weekComparison.lastWeekSessions && weekComparison.lastWeekSessions > 0) {
            return listOf(
                "This week was quieter than last. Rest is part of growth, $userName — but tomorrow's a new session.",
                "A lighter week, $userName. Sometimes we need to recharge. The forge stays hot.",
                "You did ${weekComparison.thisWeekSessions} sessions this week vs ${weekComparison.lastWeekSessions} last. Every session counts, even the small ones."
            ).random()
        }

        return listOf(
            "Keep forging ahead, $userName. Knowledge built slowly is knowledge that lasts.",
            "You've earned $xp XP so far. Every point is proof of growth.",
            "The secret to getting ahead is getting started. You're $streak days in, $userName."
        ).random()
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

            ReflectionAgent.reflect(
                db = db,
                subject = session.subject,
                topic = session.topic,
                scorePercent = 70,
                responseTimeMs = elapsed * 1000,
                isGraded = false,
                activityType = "study"
            )
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
    val lastExam: com.aiteacher.data.ExamResultEntity?,
    val weekComparison: WeekComparison = WeekComparison(),
    val mentorMessage: String = ""
)
