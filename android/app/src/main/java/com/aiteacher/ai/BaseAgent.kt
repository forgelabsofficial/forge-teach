package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.security.SecureStorage

/**
 * Base class for all agents in the pipeline.
 *
 * Each agent has:
 *  - a fixed system prompt defining its role
 *  - access to shared AgentMemory
 *  - a list of tools it can use
 *  - a run() function that returns updated memory + emits step events
 */
abstract class BaseAgent(
    val agentName: String,
    val tools: List<AgentTool> = emptyList()
) {
    abstract val systemPrompt: String

    /**
     * Run the agent. Reads from memory, does its work, returns updated memory.
     * onStep emits UI-visible progress strings.
     */
    abstract suspend fun run(
        context: Context,
        memory: AgentMemory,
        onStep: (String) -> Unit
    ): AgentMemory

    // ── Shared AI call helpers ────────────────────────────────────────────────

    protected suspend fun callAi(
        context: Context,
        memory: AgentMemory,
        userMessage: String,
        maxTokens: Int = 4000
    ): Pair<String, AgentMemory> {
        val apiKey = SecureStorage.getApiKey(context) ?: return "" to memory
        val provider = SecureStorage.getApiProvider(context)?.lowercase() ?: return "" to memory
        val model = SecureStorage.getApiModel(context)

        // Build messages: system + full conversation history + new user message
        val updatedMemory = memory
            .withMessage("user", userMessage)

        val messages = buildList {
            add(mapOf("role" to "system", "content" to systemPrompt + toolsDescription()))
            addAll(updatedMemory.historyAsMessages())
        }

        val raw = try {
            when (provider) {
                "anthropic" -> callAnthropic(apiKey, model, messages, maxTokens)
                "google"    -> callGoogle(apiKey, messages, maxTokens)
                else        -> callOpenAiCompat(provider, apiKey, model, messages, maxTokens)
            }
        } catch (e: Exception) {
            Log.w(agentName, "AI call failed: ${e.message}")
            ""
        }

        val memoryWithReply = if (raw.isNotBlank())
            updatedMemory.withMessage("assistant", raw)
        else updatedMemory

        return raw to memoryWithReply
    }

    private fun toolsDescription(): String {
        if (tools.isEmpty()) return ""
        val list = tools.joinToString("\n") { "- ${it.name}: ${it.description}" }
        return "\n\nAvailable tools:\n$list"
    }

    private suspend fun callOpenAiCompat(
        provider: String, apiKey: String, model: String?,
        messages: List<Map<String, String>>, maxTokens: Int
    ): String {
        val config = ModelRegistry.getProviderConfig(provider)
        val baseUrl = (config?.first ?: "https://api.openai.com").trimEnd('/') + "/"
        val authHeader = config?.second?.first ?: "Authorization"
        val authPrefix = (config?.second?.second ?: "Bearer").let {
            if (it.isNotEmpty() && !it.endsWith(" ")) "$it " else it
        }
        val api = OpenAiApi.create(apiKey, baseUrl, authHeader, authPrefix)
        val chosenModel = model ?: when (provider) {
            "deepseek" -> "deepseek-chat"
            "mistral"  -> "mistral-large-latest"
            "groq"     -> "mixtral-8x7b-32768"
            else       -> "gpt-4o-mini"
        }
        val body: Map<String, Any> = mapOf(
            "model" to chosenModel,
            "messages" to messages,
            "temperature" to 0.4f,
            "max_tokens" to maxTokens
        )
        return api.chat(body).choices?.firstOrNull()?.message?.content ?: ""
    }

    private suspend fun callAnthropic(
        apiKey: String, model: String?,
        messages: List<Map<String, String>>, maxTokens: Int
    ): String {
        val api = AnthropicApi.create(apiKey, "https://api.anthropic.com/")
        // Anthropic: system is separate, messages are user/assistant only
        val anthropicMessages = messages
            .filter { it["role"] != "system" }
            .map { AnthropicMessage(it["role"] ?: "user", it["content"] ?: "") }
        val resp = api.generate(
            AnthropicRequest(
                model = model ?: "claude-3-5-sonnet-20241022",
                maxTokens = maxTokens,
                messages = anthropicMessages
            )
        )
        return resp.content?.firstOrNull()?.text ?: ""
    }

    private suspend fun callGoogle(
        apiKey: String,
        messages: List<Map<String, String>>, maxTokens: Int
    ): String {
        val api = GoogleApi.create(apiKey, "https://generativelanguage.googleapis.com/")
        // Flatten all messages into a single prompt for Gemini
        val combined = messages.joinToString("\n\n") { (it["role"] ?: "").uppercase() + ": " + (it["content"] ?: "") }
        val resp = api.generate(GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(combined))))))
        return resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
    }
}
