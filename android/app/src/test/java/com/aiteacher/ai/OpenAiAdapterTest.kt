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

class OpenAiAdapterTest {
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
            "weeks": 2,
            "sessions": [
              { "date": "2026-07-20", "topic": "Math", "duration": 30 }
            ]
          }
        }
        """
        server.enqueue(MockResponse().setResponseCode(200).setBody(planJson))

        val baseUrl = server.url("/").toString()
        val adapter = OpenAiAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","Math",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(30))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(2, plan.weeks)
        assertEquals(1, plan.sessions.size)
        assertEquals("Math", plan.sessions[0].topic)
    }

    @Test
    fun retriesOn429_thenSucceeds() = runBlocking {
        val planJson = """
        {
          "plan": {
            "weeks": 1,
            "sessions": [ { "date": "2026-07-21", "topic": "Physics", "duration": 20 } ]
          }
        }
        """
        // enqueue a 429 then a successful response
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(planJson))

        val baseUrl = server.url("/").toString()
        val adapter = OpenAiAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","Physics",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(20))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        assertEquals(1, plan.weeks)
        assertEquals(1, plan.sessions.size)
        assertEquals("Physics", plan.sessions[0].topic)
    }

    @Test
    fun malformedJsonFallsbackToMock() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{ not valid json }"))
        val baseUrl = server.url("/").toString()
        val adapter = OpenAiAdapter(apiKey = "test", baseUrl = baseUrl)
        val assessment = Assessment(user = "Student", topics = listOf(Topic("t1","X",true)), availability = emptyMap(), prefs = com.aiteacher.onboarding.Preferences(30))
        val plan = adapter.generatePlan(ApplicationProvider.getApplicationContext(), assessment)
        // AiClientMock returns 4 weeks by default
        assertEquals(4, plan.weeks)
    }
}
