package com.aiteacher.ai

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Anthropic-specific model loader. Delegates to `GenericModelLoader` for broader coverage
 * and falls back to classic /v1/models parsing if needed.
 */
class AnthropicModelLoader(private val baseUrl: String = "https://api.anthropic.com") : ModelLoader {
    private val client = OkHttpClient()
    private val gson = Gson()

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        try {
            val g = GenericModelLoader(baseUrl, Pair("x-api-key", ""))
            val found = g.listModels(apiKey)
            if (found.isNotEmpty()) return found
        } catch (e: Exception) {
            Log.w("AnthropicModelLoader", "generic loader failed: ${e.message}")
        }

        try {
            val url = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + "/v1/models" else baseUrl + "/v1/models"
            val req = Request.Builder().url(url)
                .addHeader("x-api-key", apiKey)
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            if (!resp.isSuccessful) return emptyList()
            val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val out = mutableListOf<ModelInfo>()
            if (json.has("models") && json.get("models").isJsonArray) {
                val arr = json.getAsJsonArray("models")
                arr.forEach { el ->
                    if (el.isJsonObject && el.asJsonObject.has("id")) {
                        out.add(ModelInfo(el.asJsonObject.get("id").asString))
                    }
                }
            }
            // fallback to data array too
            if (out.isEmpty() && json.has("data") && json.get("data").isJsonArray) {
                val arr = json.getAsJsonArray("data")
                arr.forEach { el ->
                    if (el.isJsonObject && el.asJsonObject.has("id")) {
                        out.add(ModelInfo(el.asJsonObject.get("id").asString))
                    }
                }
            }
            return out
        } catch (e: Exception) {
            Log.w("AnthropicModelLoader", "error listing models: ${e.message}")
            return emptyList()
        }
    }
}
