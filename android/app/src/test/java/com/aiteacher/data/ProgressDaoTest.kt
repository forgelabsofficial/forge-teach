package com.aiteacher.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem

class ProgressDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PlanRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = PlanRepository(null, db)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun addAndQueryProgress() = runBlocking {
        val plan = Plan(weeks = 1, sessions = listOf(SessionItem("2026-07-20", "Math", 30)))
        val planId = repo.savePlan(plan)
        assertTrue(planId > 0)
        val sessions = db.planDao().getSessionsForPlan(planId)
        assertTrue(sessions.isNotEmpty())
        val sessionId = sessions[0].id
        val progId = repo.addProgress(sessionId = sessionId, status = "completed", notes = "Good", score = 90)
        assertTrue(progId > 0)
        val fetched = repo.getProgressForSession(sessionId)
        assertEquals(1, fetched.size)
        assertEquals("completed", fetched[0].status)
    }
}
