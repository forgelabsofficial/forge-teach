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

class StudentProgressTest {
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
    fun saveStudentAndLinkPlan() = runBlocking {
        val studentId = repo.saveStudentProfile("Alice", "UTC")
        assertTrue(studentId > 0)
        val plan = Plan(weeks = 1, sessions = listOf(SessionItem("2026-07-20", "Math", 30)))
        val planId = repo.savePlan(plan, studentId)
        assertTrue(planId > 0)
        val loaded = repo.loadLatestPlan()
        assertNotNull(loaded)
        // ensure latest plan persisted
        assertEquals(plan.weeks, loaded?.weeks)
    }
}
