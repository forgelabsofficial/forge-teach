package com.aiteacher.ai

import androidx.test.core.app.ApplicationProvider
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Topic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoogleAdapterTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun generatePlan_parsesPlanDto() = runBlocking {
        val planJson = """
        {
          "plan": {
            "weeks": 3,
            "sessions": [
              { "date": "2026-07-22", "topic": "History", "duration": 25 }
            ]
          }
        }
        """
        server.enqueue(MockResponse().setResponseCode(200).setBody(planJson))

        val baseUrl = server.url("/").toString()
        val adapter = GoogleAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","History",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(25))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(3, plan.weeks)
        assertEquals(1, plan.sessions.size)
        assertEquals("History", plan.sessions[0].topic)
    }
}
