package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnthropicAdapter(private val apiKey: String, private val baseUrl: String? = null) {
    suspend fun generatePlan(context: Context, assessment: Assessment): Plan = withContext(Dispatchers.IO) {
        try {
            val api = AnthropicApi.create(apiKey, baseUrl ?: "https://api.anthropic.com/")
            val payload = OpenAiRequest(model = "anthropic-1", input = mapOf("assessment" to assessment))
            val resp = api.generate(payload)
            val planDto = resp.plan
            if (planDto != null) {
                val sessions = planDto.sessions.map { SessionItem(date = it.date, topic = it.topic, duration = it.duration, isoDateTime = it.dateTime) }
                return@withContext Plan(weeks = planDto.weeks, sessions = sessions)
            }
        } catch (e: Exception) {
            Log.w("AnthropicAdapter", "Error generating plan: ${e.message}")
        }
        // fallback
        AiClientMock.generatePlan(assessment)
    }
}
