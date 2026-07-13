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

class AnthropicAdapterTest {
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
            "weeks": 1,
            "sessions": [
              { "date": "2026-07-21", "topic": "Science", "duration": 20 }
            ]
          }
        }
        """
        server.enqueue(MockResponse().setResponseCode(200).setBody(planJson))

        val baseUrl = server.url("/").toString()
        val adapter = AnthropicAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","Science",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(20))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(1, plan.weeks)
        assertEquals(1, plan.sessions.size)
        assertEquals("Science", plan.sessions[0].topic)
    }

    @Test
    fun retriesOn429_thenSucceeds() = runBlocking {
        val planJson = """
        {
          "plan": {
            "weeks": 1,
            "sessions": [ { "date": "2026-07-22", "topic": "Biology", "duration": 25 } ]
          }
        }
        """
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(planJson))

        val baseUrl = server.url("/").toString()
        val adapter = AnthropicAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","Biology",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(25))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(1, plan.weeks)
        assertEquals(1, plan.sessions.size)
        assertEquals("Biology", plan.sessions[0].topic)
    }

    @Test
    fun malformedJsonFallsbackToMock() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{ oops }"))
        val baseUrl = server.url("/").toString()
        val adapter = AnthropicAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","X",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(30))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(4, plan.weeks)
    }
}
