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

    suspend fun savePlan(plan: Plan): Long {
        val planEntity = PlanEntity(weeks = plan.weeks)
        val planId = dao.insertPlan(planEntity)
        val sessions = plan.sessions.map { s ->
            SessionEntity(planId = planId, date = s.date, isoDateTime = s.isoDateTime, topic = s.topic, duration = s.duration)
        }
        dao.insertSessions(sessions)
        return planId
    }

    suspend fun loadLatestPlan(): Plan? {
        val pw = dao.getLatestPlan() ?: return null
        val sessions = pw.sessions.map { SessionItem(date = it.date, topic = it.topic, duration = it.duration, isoDateTime = it.isoDateTime) }
        return Plan(weeks = pw.plan.weeks, sessions = sessions)
    }
}

