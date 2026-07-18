package com.aiteacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.AiClient
import com.aiteacher.ai.MemoryAgent
import com.aiteacher.data.AppDatabase
import com.aiteacher.data.PlanRepository
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import com.aiteacher.work.ScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PlanRepository(application.applicationContext)
    private val db = AppDatabase.getInstance(application)
    private val ctx = application.applicationContext

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _plan = MutableStateFlow<Plan?>(null)
    val plan: StateFlow<Plan?> = _plan

    private val _sessions = MutableStateFlow<List<SessionItem>>(emptyList())
    val sessions: StateFlow<List<SessionItem>> = _sessions

    private val _completedKeys = MutableStateFlow<Set<String>>(emptySet())
    val completedKeys: StateFlow<Set<String>> = _completedKeys

    private val _assessment = MutableStateFlow<Assessment?>(null)

    private val _savedToast = MutableStateFlow(false)
    val savedToast: StateFlow<Boolean> = _savedToast

    private var autoSaveJob: kotlinx.coroutines.Job? = null

    init {
        loadPlan()
    }

    private fun loadPlan() {
        viewModelScope.launch {
            _loading.value = true
            _completedKeys.value = withContext(Dispatchers.IO) { DataStoreUtils.loadCompletedKeys(ctx) }

            val existing = withContext(Dispatchers.IO) { repo.loadLatestPlan() }
            if (existing != null) {
                _plan.value = existing
                // Inject Memory Boost sessions for due reviews
                val boosted = injectMemoryBoostSessions(existing.sessions)
                _sessions.value = boosted
            } else {
                val a = withContext(Dispatchers.IO) { DataStoreUtils.loadAssessment(ctx) }
                _assessment.value = a
                if (a != null) {
                    val p = withContext(Dispatchers.IO) { AiClient.generatePlan(ctx, a) }
                    _plan.value = p
                    _sessions.value = injectMemoryBoostSessions(p?.sessions ?: emptyList())
                }
            }
            _loading.value = false
        }
    }

    /**
     * Query MemoryAgent for topics due for review and inject them as
     * "Memory Boost" sessions at the top of the session list.
     */
    private suspend fun injectMemoryBoostSessions(originalSessions: List<SessionItem>): List<SessionItem> {
        return withContext(Dispatchers.IO) {
            val dueReviews = MemoryAgent.getDueReviews(db)
            if (dueReviews.isEmpty()) return@withContext originalSessions

            val today = LocalDate.now()
            val reviewSessions = dueReviews.mapIndexed { idx, candidate ->
                val dayOffset = idx / 3 // max 3 reviews per day
                val reviewDate = today.plusDays(dayOffset.toLong())
                val topicName = candidate.topicId
                    .replace("_quiz", "")
                    .replace("_baseline", "")
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                SessionItem(
                    date = reviewDate.toString(),
                    topic = "Memory Boost — $topicName",
                    subject = candidate.subject,
                    duration = 15, // shorter than normal sessions
                    topicRank = 0,
                    isRevision = true,
                    weaknessScore = 100 - candidate.mastery
                )
            }

            // Prepend review sessions to the original plan
            reviewSessions + originalSessions
        }
    }

    fun updateSessionDateTime(index: Int, newVal: String) {
        val list = _sessions.value.toMutableList()
        if (index < list.size) {
            list[index] = list[index].copy(isoDateTime = newVal)
            _sessions.value = list
            debouncedAutoSave()
        }
    }

    fun updateSessionDuration(index: Int, newDuration: Int) {
        val list = _sessions.value.toMutableList()
        if (index < list.size) {
            list[index] = list[index].copy(duration = newDuration.coerceIn(5, 180))
            _sessions.value = list
            debouncedAutoSave()
        }
    }

    fun deleteSession(index: Int) {
        val list = _sessions.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _sessions.value = list
            debouncedAutoSave()
        }
    }

    fun toggleComplete(key: String) {
        val current = _completedKeys.value.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _completedKeys.value = current
        viewModelScope.launch {
            DataStoreUtils.saveCompletedKeys(ctx, current)
        }
    }

    fun regenerate() {
        viewModelScope.launch {
            _loading.value = true
            _assessment.value?.let { a ->
                val p = withContext(Dispatchers.IO) { AiClient.generatePlan(ctx, a) }
                _plan.value = p
                _sessions.value = injectMemoryBoostSessions(p?.sessions ?: emptyList())
            }
            _loading.value = false
        }
    }

    fun acceptPlan(onAccepted: () -> Unit) {
        viewModelScope.launch {
            saveCurrentPlan()
            onAccepted()
        }
    }

    private suspend fun saveCurrentPlan() {
        val pw = _plan.value ?: return
        val pid = withContext(Dispatchers.IO) {
            repo.savePlan(Plan(pw.weeks, _sessions.value))
        }
        ScheduleManager.schedulePlanNotifications(ctx, Plan(pw.weeks, _sessions.value), planId = pid)
    }

    private fun debouncedAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            saveCurrentPlan()
            _savedToast.value = true
            _savedToast.value = false
        }
    }

    fun loadAssessmentFromStore() {
        viewModelScope.launch {
            _assessment.value = withContext(Dispatchers.IO) { DataStoreUtils.loadAssessment(ctx) }
        }
    }
}
