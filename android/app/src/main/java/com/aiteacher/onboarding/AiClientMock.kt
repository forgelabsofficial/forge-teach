package com.aiteacher.onboarding

import kotlinx.coroutines.delay

object AiClientMock {
    // Simple mock that returns a deterministic 4-week plan for a given assessment
    suspend fun generatePlan(assessment: Assessment): Plan {
        // simulate network / compute delay
        delay(600)
        val sessions = mutableListOf<SessionItem>()
        var day = 15
        assessment.topics.forEachIndexed { idx, topic ->
            sessions.add(SessionItem(date = "2026-07-${day + idx}", topic = topic.name, duration = assessment.prefs.sessionLength))
        }
        return Plan(weeks = 4, sessions = sessions)
    }
}
