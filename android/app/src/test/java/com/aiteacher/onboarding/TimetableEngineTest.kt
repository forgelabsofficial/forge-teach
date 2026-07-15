package com.aiteacher.onboarding

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

class TimetableEngineTest {

    @Test
    fun generatesScheduleFromAvailability() {
        val assessment = Assessment(
            user = "Test",
            topics = listOf(Topic("vars", "Variables", true), Topic("control", "Control Flow", true)),
            availability = mapOf("mon" to listOf("09:00-10:00"), "tue" to listOf("09:00-10:00")),
            prefs = Preferences(sessionLength = 30)
        )

        val plan = TimetableEngine.generateSchedule(assessment, weeks = 1, startDate = LocalDate.now(), zone = ZoneId.of("UTC"))
        assertNotNull(plan)
        assertTrue(plan.sessions.isNotEmpty())
        // sessions should include isoDateTime values
        assertNotNull(plan.sessions.first().isoDateTime)
    }

    @Test
    fun fallbackWhenNoAvailability() {
        val assessment = Assessment(
            user = "Test",
            topics = listOf(Topic("vars", "Variables", true)),
            availability = emptyMap(),
            prefs = Preferences(sessionLength = 20)
        )
        val plan = TimetableEngine.generateSchedule(assessment, weeks = 1, startDate = LocalDate.now(), zone = ZoneId.of("UTC"))
        assertNotNull(plan)
        assertTrue(plan.sessions.size >= 1)
    }

    @Test
    fun obeysSpacingRulesAndAvailabilitySplitting() {
        val assessment = Assessment(
            user = "Test",
            topics = listOf(Topic("vars", "Variables", true), Topic("control", "Control Flow", true)),
            // mon has 2 slots, tue has 1 slot
            availability = mapOf("mon" to listOf("09:00-10:00, 14:00-15:00"), "tue" to listOf("10:00-11:00")),
            prefs = Preferences(sessionLength = 30)
        )
        val plan = TimetableEngine.generateSchedule(assessment, weeks = 1, startDate = LocalDate.of(2026, 7, 13), zone = ZoneId.of("UTC"))
        assertNotNull(plan)
        // With 2 topics, we want 1 * 2 = 2 sessions.
        // There are 2 days available (Monday and Tuesday).
        // Spacing rule: Pass 1 should schedule one session on Mon (e.g. 09:00) and one on Tue (10:00).
        // Let's verify that they are scheduled on separate days.
        assertEquals(2, plan.sessions.size)
        val days = plan.sessions.map { it.date }.toSet()
        assertEquals(2, days.size)
    }
}
