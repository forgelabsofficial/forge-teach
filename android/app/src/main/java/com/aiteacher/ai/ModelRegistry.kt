package com.aiteacher.ai

/**
 * Registry mapping provider ids to ModelLoader instances and metadata.
 * Add providers here as needed.
 */
object ModelRegistry {
    // Map provider id -> baseUrl and auth header type
    private val providers = mapOf(
        "openai" to Pair("https://api.openai.com", Pair("Authorization", "Bearer")),
        "anthropic" to Pair("https://api.anthropic.com", Pair("x-api-key", "")),
        "google" to Pair("https://generativelanguage.googleapis.com", Pair("Authorization", "Bearer")),
        "mistral" to Pair("https://api.mistral.ai", Pair("Authorization", "Bearer")),
        "deepseek" to Pair("https://api.deepseek.ai", Pair("Authorization", "Bearer")),
        "groq" to Pair("https://api.groq.com/openai", Pair("Authorization", "Bearer")),
        "github" to Pair("https://api.github.com", Pair("Authorization", "token")),
        // More providers -- these may require special headers or endpoints
        "cohere" to Pair("https://api.cohere.ai", Pair("Authorization", "Bearer")),
        "huggingface" to Pair("https://api.huggingface.co", Pair("Authorization", "Bearer")),
        "alephalpha" to Pair("https://api.aleph-alpha.com", Pair("Authorization", "Bearer")),
        "replit" to Pair("https://api.replit.com", Pair("Authorization", "Bearer")),
        "azure" to Pair("https://YOUR_AZURE_ENDPOINT", Pair("api-key", "")),
        "baidu" to Pair("https://aip.baidubce.com", Pair("Authorization", "Bearer")),
        "stability" to Pair("https://api.stability.ai", Pair("Authorization", "Bearer"))
    )

    /** Ordered list of provider IDs for display in the UI dropdown. */
    val providerIds: List<String> = listOf(
        "openai", "anthropic", "google", "mistral", "groq", "deepseek",
        "cohere", "huggingface", "github", "azure", "alephalpha", "replit", "baidu", "stability"
    )

    /** Human-readable display names for providers. */
    val providerDisplayNames: Map<String, String> = mapOf(
        "openai" to "OpenAI",
        "anthropic" to "Anthropic",
        "google" to "Google (Gemini)",
        "mistral" to "Mistral AI",
        "groq" to "Groq",
        "deepseek" to "DeepSeek",
        "cohere" to "Cohere",
        "huggingface" to "Hugging Face",
        "github" to "GitHub Models",
        "azure" to "Azure OpenAI",
        "alephalpha" to "Aleph Alpha",
        "replit" to "Replit",
        "baidu" to "Baidu ERNIE",
        "stability" to "Stability AI"
    )

    fun getLoader(providerId: String): ModelLoader {
        val lower = providerId.lowercase()
        return when (lower) {
            "openai" -> OpenAiModelLoader(providers["openai"]!!.first)
            "anthropic" -> AnthropicModelLoader(providers["anthropic"]!!.first)
            "google" -> GoogleModelLoader(providers["google"]!!.first)
            else -> {
                val pair = providers[lower]
                if (pair != null) GenericModelLoader(pair.first, pair.second) else GenericModelLoader("https://api.example.com", Pair("Authorization", "Bearer"))
            }
        }
    }

    // Expose additional provider-specific loaders for extended providers
    fun getExtendedLoader(providerId: String): ModelLoader {
        return when (providerId.lowercase()) {
            "mistral" -> MistralModelLoader(providers["mistral"]!!.first)
            "groq" -> GroqModelLoader(providers["groq"]!!.first)
            "deepseek" -> DeepseekModelLoader(providers["deepseek"]!!.first)
            else -> getLoader(providerId)
        }
    }

    /**
     * Expose provider configuration for providers that need custom base URLs (eg Azure, GitHub Enterprise).
     */
    fun getProviderConfig(providerId: String): Pair<String, Pair<String, String>>? {
        return providers[providerId.lowercase()]
    }
}
