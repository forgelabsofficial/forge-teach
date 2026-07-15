package com.aiteacher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiteacher.data.PlanRepository
import com.aiteacher.onboarding.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PlanRepository(application.applicationContext)

    private val _plan = MutableStateFlow<Plan?>(null)
    val plan: StateFlow<Plan?> = _plan

    private val _studentName = MutableStateFlow("Student")
    val studentName: StateFlow<String> = _studentName

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val data = withContext(Dispatchers.IO) {
                    val p = repo.loadLatestPlan()
                    val n = repo.getLatestStudentProfile()?.name ?: "Student"
                    val total = p?.sessions?.size ?: 0
                    val completed = p?.sessions?.count {
                        try {
                            OffsetDateTime.parse(it.isoDateTime ?: it.date)
                                .toInstant().isBefore(Instant.now())
                        } catch (_: Exception) { false }
                    } ?: 0
                    PlanData(p, n, completed, total)
                }
                _plan.value = data.plan
                _studentName.value = data.studentName
                _completedCount.value = data.completed
                _totalCount.value = data.total
            } catch (e: Exception) {
                _error.value = "Couldn't load your data. Tap to retry."
            } finally {
                _loading.value = false
            }
        }
    }
}

private data class PlanData(
    val plan: Plan?,
    val studentName: String,
    val completed: Int,
    val total: Int
)
