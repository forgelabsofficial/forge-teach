package com.aiteacher.data

import android.content.Context
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem

/**
 * PlanRepository
 * - In production, construct with a Context: PlanRepository(context)
 * - For tests, pass an in-memory AppDatabase via the optional dbOverride parameter.
 */
class PlanRepository(private val context: Context? = null, private val dbOverride: AppDatabase? = null) {
    private val db: AppDatabase by lazy {
        dbOverride ?: (context?.let { AppDatabase.getInstance(it) } ?: throw IllegalStateException("Context required when no dbOverride provided"))
    }
    private val dao by lazy { db.planDao() }
    private val studentDao by lazy { db.studentDao() }
    private val progressDao by lazy { db.progressDao() }

    suspend fun savePlan(plan: Plan, studentId: Long? = null): Long {
        val planEntity = PlanEntity(weeks = plan.weeks, studentId = studentId)
        val planId = dao.insertPlan(planEntity)
        val sessions = plan.sessions.map { s ->
            SessionEntity(planId = planId, date = s.date, isoDateTime = s.isoDateTime, topic = s.topic, duration = s.duration)
        }
        dao.insertSessions(sessions)
        return planId
    }

    suspend fun saveStudentProfile(name: String, timezone: String? = null): Long {
        val now = System.currentTimeMillis()
        val student = StudentProfileEntity(name = name, timezone = timezone, createdAt = now, updatedAt = now)
        return studentDao.insertStudent(student)
    }

    suspend fun getLatestStudentProfile(): StudentProfileEntity? {
        return studentDao.getLatestStudent()
    }

    suspend fun updateStudentProfile(id: Long, name: String, timezone: String?): Int {
        val existing = studentDao.getStudentById(id) ?: return 0
        val updated = existing.copy(name = name, timezone = timezone, updatedAt = System.currentTimeMillis())
        return studentDao.updateStudent(updated)
    }

    suspend fun getPlansForStudent(studentId: Long): List<Plan> {
        val list = dao.getPlansForStudent(studentId)
        return list.map { pw ->
            val sessions = pw.sessions.map { SessionItem(date = it.date, topic = it.topic, duration = it.duration, isoDateTime = it.isoDateTime) }
            Plan(weeks = pw.plan.weeks, sessions = sessions)
        }
    }

    suspend fun addProgress(sessionId: Long, status: String = "pending", notes: String? = null, score: Int? = null): Long {
        val p = ProgressEntity(sessionId = sessionId, status = status, notes = notes, score = score)
        return progressDao.insertProgress(p)
    }

    suspend fun getProgressForSession(sessionId: Long): List<ProgressEntity> {
        return progressDao.getProgressForSession(sessionId)
    }

    suspend fun loadLatestPlan(): Plan? {
        val pw = dao.getLatestPlan() ?: return null
        val sessions = pw.sessions.map { SessionItem(date = it.date, topic = it.topic, duration = it.duration, isoDateTime = it.isoDateTime) }
        return Plan(weeks = pw.plan.weeks, sessions = sessions)
    }
}

