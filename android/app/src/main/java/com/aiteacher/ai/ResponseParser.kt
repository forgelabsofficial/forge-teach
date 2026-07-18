package com.aiteacher.ai

import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.SessionItem
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

object ResponseParser {
    private val gson = Gson()

    fun parsePlanFromOpenAiResponse(resp: OpenAiResponse?): Plan? {
        if (resp == null) return null
        
        // 1. Try choice-based nested completions structure first
        resp.choices?.firstOrNull()?.message?.content?.let { contentText ->
            val plan = parsePlanFromJsonString(contentText)
            if (plan != null) return plan
        }

        // 2. Try direct plan dto fallback (mock tests usage)
        val planDto = resp.plan
        if (planDto != null) {
            val sessions = planDto.sessions.map { s ->
                SessionItem(date = s.date, topic = s.topic, duration = s.duration, isoDateTime = s.dateTime)
            }
            return Plan(weeks = planDto.weeks, sessions = sessions)
        }
        return null
    }

    fun parsePlanFromJsonString(json: String): Plan? {
        try {
            var cleanedJson = json.trim()
            if (cleanedJson.contains("```json")) {
                cleanedJson = cleanedJson.substringAfter("```json").substringBefore("```").trim()
            } else if (cleanedJson.contains("```")) {
                cleanedJson = cleanedJson.substringAfter("```").substringBefore("```").trim()
            }

            val el: JsonElement = gson.fromJson(cleanedJson, JsonElement::class.java)
            val obj = if (el.isJsonObject) el.asJsonObject else return null
            
            // Handle if the root object has a nested "plan" object or represents the plan directly
            val planObj = if (obj.has("plan")) obj.getAsJsonObject("plan") else obj
            
            val weeks = planObj.getAsJsonPrimitive("weeks")?.asInt ?: 4
            val sessionsArr = planObj.getAsJsonArray("sessions")
            val sessions = sessionsArr.map { e ->
                val o = e.asJsonObject
                val date = o.getAsJsonPrimitive("date")?.asString ?: ""
                val topic = o.getAsJsonPrimitive("topic")?.asString ?: "Lesson"
                val duration = o.getAsJsonPrimitive("duration")?.asInt ?: 30
                val dateTime = o.getAsJsonPrimitive("dateTime")?.asString ?: o.getAsJsonPrimitive("isoDateTime")?.asString
                SessionItem(date = date, topic = topic, duration = duration, isoDateTime = dateTime)
            }
            return Plan(weeks = weeks, sessions = sessions)
        } catch (e: Exception) {
            // malformed
        }
        return null
    }
}
