package com.aiteacher.ai

import android.content.Context
import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.AiClientMock
import com.aiteacher.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AiClient {
    // Chooses between real provider adapter and mock based on stored API key and provider
    suspend fun generatePlan(context: Context, assessment: Assessment): Plan = withContext(Dispatchers.IO) {
        val apiKey = SecureStorage.getApiKey(context)
        val provider = SecureStorage.getApiProvider(context)?.lowercase()
        if (apiKey.isNullOrBlank() || provider.isNullOrBlank()) {
            // fallback to local mock
            AiClientMock.generatePlan(assessment)
        } else {
            try {
                return@withContext when (provider) {
                    "openai" -> OpenAiAdapter(apiKey).generatePlan(context, assessment)
                    "anthropic" -> AnthropicAdapter(apiKey).generatePlan(context, assessment)
                    "google" -> GoogleAdapter(apiKey).generatePlan(context, assessment)
                    else -> {
                        val config = ModelRegistry.getProviderConfig(provider)
                        if (config != null) {
                            OpenAiAdapter(
                                apiKey = apiKey,
                                baseUrl = config.first,
                                provider = provider,
                                authHeaderName = config.second.first,
                                authHeaderPrefix = config.second.second
                            ).generatePlan(context, assessment)
                        } else {
                            AiClientMock.generatePlan(assessment)
                        }
                    }
                }
            } catch (e: Exception) {
                // keep app resilient: fallback to mock on any error
                AiClientMock.generatePlan(assessment)
            }
        }
    }
}
