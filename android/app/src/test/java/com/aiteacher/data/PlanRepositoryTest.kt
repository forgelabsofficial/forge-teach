package com.aiteacher.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlanRepositoryTest {
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
    fun saveAndLoadPlan_roundTrip() = runBlocking {
        val plan = Plan(weeks = 2, sessions = listOf(SessionItem("2026-07-20", "Math", 30, "2026-07-20T09:00:00Z")))
        val id = repo.savePlan(plan)
        val loaded = repo.loadLatestPlan()
        assertEquals(plan.weeks, loaded?.weeks)
        assertEquals(plan.sessions.size, loaded?.sessions?.size)
        assertEquals(plan.sessions[0].topic, loaded?.sessions?.get(0)?.topic)
        assertEquals(plan.sessions[0].isoDateTime, loaded?.sessions?.get(0)?.isoDateTime)
    }
}
