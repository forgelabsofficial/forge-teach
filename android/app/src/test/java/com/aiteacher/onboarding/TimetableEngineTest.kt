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
}
