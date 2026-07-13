package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAdapter(private val apiKey: String, private val baseUrl: String? = null) {
    suspend fun generatePlan(context: Context, assessment: Assessment): Plan = withContext(Dispatchers.IO) {
        try {
            val api = GoogleApi.create(apiKey, baseUrl ?: "https://generativelanguage.googleapis.com/")
            val payload = OpenAiRequest(model = "google-palm", input = mapOf("assessment" to assessment))
            val resp = api.generate(payload)
            val parsed = ResponseParser.parsePlanFromOpenAiResponse(resp)
            if (parsed != null) return@withContext parsed

            try {
                val gson = com.google.gson.Gson()
                val json = gson.toJson(resp)
                val p2 = ResponseParser.parsePlanFromJsonString(json)
                if (p2 != null) return@withContext p2
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w("GoogleAdapter", "Error generating plan: ${e.message}")
        }
        // fallback
        AiClientMock.generatePlan(assessment)
    }
}
