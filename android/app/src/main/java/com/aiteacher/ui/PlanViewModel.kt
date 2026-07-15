package com.aiteacher.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.ai.AiClient
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

class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PlanRepository(application.applicationContext)
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
            val existing = withContext(Dispatchers.IO) { repo.loadLatestPlan() }
            if (existing != null) {
                _plan.value = existing
                _sessions.value = existing.sessions
            } else {
                val a = withContext(Dispatchers.IO) { DataStoreUtils.loadAssessment(ctx) }
                _assessment.value = a
                if (a != null) {
                    val p = withContext(Dispatchers.IO) { AiClient.generatePlan(ctx, a) }
                    _plan.value = p
                    _sessions.value = p?.sessions ?: emptyList()
                }
            }
            _loading.value = false
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
    }

    fun regenerate() {
        viewModelScope.launch {
            _loading.value = true
            _assessment.value?.let { a ->
                val p = withContext(Dispatchers.IO) { AiClient.generatePlan(ctx, a) }
                _plan.value = p
                _sessions.value = p?.sessions ?: emptyList()
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
            kotlinx.coroutines.delay(1500) // 1.5s debounce
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
