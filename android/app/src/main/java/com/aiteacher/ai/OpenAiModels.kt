package com.aiteacher.ai

import com.google.gson.annotations.SerializedName

// Minimal typed models for OpenAI-style provider responses
data class OpenAiRequest(
    val model: String,
    val input: Any
)

data class OpenAiResponse(
    @SerializedName("plan") val plan: PlanDto? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("object") val obj: String? = null
)

data class PlanDto(
    val weeks: Int = 4,
    val sessions: List<SessionDto> = emptyList()
)

data class SessionDto(
    val date: String,
    val topic: String,
    val duration: Int,
    val dateTime: String? = null
)
