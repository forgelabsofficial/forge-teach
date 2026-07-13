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
            val el: JsonElement = gson.fromJson(json, JsonElement::class.java)
            val obj = if (el.isJsonObject) el.asJsonObject else return null
            if (obj.has("plan")) {
                val planObj = obj.getAsJsonObject("plan")
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
            }
        } catch (e: Exception) {
            // malformed
        }
        return null
    }
}
