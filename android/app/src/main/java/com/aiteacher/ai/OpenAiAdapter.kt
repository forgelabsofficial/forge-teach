package com.aiteacher.ai

import android.content.Context
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.AiClientMock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class OpenAiAdapter(private val apiKey: String, private val baseUrl: String? = null) {
    suspend fun generatePlan(context: Context, assessment: Assessment): Plan = withContext(Dispatchers.IO) {
        try {
            val api = OpenAiApi.create(apiKey, baseUrl ?: "https://api.openai.com/")
            val inputMap = mapOf(
                "name" to assessment.name,
                "age" to assessment.age,
                "topics" to assessment.topics.map { it.name },
                "prefs" to mapOf("sessionLength" to assessment.prefs.sessionLength)
            )
            // Craft a Chat Completions request that asks the assistant to return a JSON object with a `plan` field.
            val messages = listOf(
                mapOf("role" to "system", "content" to "You are an assistant that outputs a JSON object with a top-level \"plan\" entry. The plan contains weeks (int) and sessions: [{date, topic, duration, dateTime?}]"),
                mapOf("role" to "user", "content" to "Generate a study plan JSON for the following assessment: ${inputMap}")
            )
            val body = mapOf(
                "model" to "gpt-4o-mini",
                "messages" to messages,
                "temperature" to 0.2,
                "max_tokens" to 800
            )

            val resp = api.chat(body)

            // resp should follow OpenAI chat completion shape; extract assistant message content and parse plan
            val assistantContent = resp.choices?.firstOrNull()?.message?.content
            if (!assistantContent.isNullOrBlank()) {
                try {
                    val gson = com.google.gson.Gson()
                    val root = gson.fromJson(assistantContent, com.google.gson.JsonObject::class.java)
                    if (root.has("plan")) {
                        val planObj = root.getAsJsonObject("plan")
                        val weeks = planObj.getAsJsonPrimitive("weeks")?.asInt ?: 4
                        val sessionsJson = planObj.getAsJsonArray("sessions")
                        val sessions = sessionsJson.map { el ->
                            val o = el.asJsonObject
                            val date = if (o.has("date")) o.get("date").asString else ""
                            val topic = if (o.has("topic")) o.get("topic").asString else "Lesson"
                            val duration = if (o.has("duration")) o.get("duration").asInt else assessment.prefs.sessionLength
                            val dateTime = if (o.has("dateTime")) o.get("dateTime").asString else null
                            SessionItem(date = date, topic = topic, duration = duration, isoDateTime = dateTime)
                        }
                        return@withContext Plan(weeks = weeks, sessions = sessions)
                    }
                } catch (e: Exception) {
                    // fall through to mock
                }
            }

            // Fallback: use the mock deterministic plan
            AiClientMock.generatePlan(assessment)
        } catch (e: Exception) {
            // On any error, return the mock plan so the app remains usable offline/dev
            com.aiteacher.onboarding.AiClientMock.generatePlan(assessment)
        }
    }
}
