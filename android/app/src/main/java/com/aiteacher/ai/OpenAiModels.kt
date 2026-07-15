package com.aiteacher.ai

import com.google.gson.annotations.SerializedName

// OpenAI-style models
data class OpenAiRequest(
    val model: String,
    val input: Any
)

data class OpenAiResponse(
    @SerializedName("plan") val plan: PlanDto? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("object") val obj: String? = null,
    @SerializedName("choices") val choices: List<OpenAiChoice>? = null
)

data class OpenAiChoice(
    val message: OpenAiChatMessage? = null
)

data class OpenAiChatMessage(
    val role: String? = null,
    val content: String? = null
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

// Anthropic Claude models
data class AnthropicRequest(
    val model: String,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    val messages: List<AnthropicMessage>
)

data class AnthropicMessage(
    val role: String,
    val content: String
)

data class AnthropicResponse(
    val id: String? = null,
    val content: List<AnthropicContent>? = null
)

data class AnthropicContent(
    val type: String? = null,
    val text: String? = null
)

// Google Gemini models
data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

data class GeminiCandidate(
    val content: GeminiCandidateContent? = null
)

data class GeminiCandidateContent(
    val parts: List<GeminiPart>? = null
)
