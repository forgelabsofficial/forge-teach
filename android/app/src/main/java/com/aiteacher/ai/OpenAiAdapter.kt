package com.aiteacher.ai

import android.content.Context
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.AiClientMock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class OpenAiAdapter(
    private val apiKey: String,
    private val baseUrl: String? = null,
    private val provider: String = "openai",
    private val authHeaderName: String = "Authorization",
    private val authHeaderPrefix: String = "Bearer "
) {
    suspend fun generatePlan(context: Context, assessment: Assessment): Plan = withContext(Dispatchers.IO) {
        try {
            val prefixWithSpace = if (authHeaderPrefix.isNotEmpty() && !authHeaderPrefix.endsWith(" ")) "$authHeaderPrefix " else authHeaderPrefix
            val api = OpenAiApi.create(apiKey, baseUrl ?: "https://api.openai.com/", authHeaderName, prefixWithSpace)
            val inputMap = mapOf(
                "name" to assessment.user,
                "topics" to assessment.topics.map { it.name },
                "availability" to assessment.availability,
                "prefs" to mapOf("sessionLength" to assessment.prefs.sessionLength)
            )
            // Craft a Chat Completions request that asks the assistant to return a JSON object with a `plan` field.
            val messages = listOf(
                mapOf("role" to "system", "content" to "You are an assistant that outputs a JSON object with a top-level \"plan\" entry. The plan contains weeks (int) and sessions: [{date, topic, duration, dateTime?}]"),
                mapOf("role" to "user", "content" to "Generate a study plan JSON for the following assessment: ${inputMap}")
            )
            val selectedModel = com.aiteacher.security.SecureStorage.getApiModel(context)
            val modelToUse = if (!selectedModel.isNullOrBlank()) {
                selectedModel
            } else {
                when (provider.lowercase()) {
                    "deepseek" -> "deepseek-chat"
                    "mistral" -> "mistral-large-latest"
                    "groq" -> "mixtral-8x7b-32768"
                    else -> "gpt-4o-mini"
                }
            }

            val body = mapOf(
                "model" to modelToUse,
                "messages" to messages,
                "temperature" to 0.2,
                "max_tokens" to 800
            )

            val resp = api.chat(body)
            // First, try typed parsing
            val parsed = ResponseParser.parsePlanFromOpenAiResponse(resp)
            if (parsed != null) return@withContext parsed

            // Second, attempt to extract a raw JSON plan if the provider returned assistant-like content
            // Convert raw response object back to JSON string via Gson (fallback path)
            try {
                val gson = com.google.gson.Gson()
                val json = gson.toJson(resp)
                val parsed2 = ResponseParser.parsePlanFromJsonString(json)
                if (parsed2 != null) return@withContext parsed2
            } catch (_: Exception) {}

            // Fallback: use the mock deterministic plan
            AiClientMock.generatePlan(assessment)
        } catch (e: Exception) {
            // On any error, return the mock plan so the app remains usable offline/dev
            com.aiteacher.onboarding.AiClientMock.generatePlan(assessment)
        }
    }
}
