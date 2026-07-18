package com.aiteacher.ai

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenAI-specific model loader. Delegates to `GenericModelLoader` for broad coverage
 * and falls back to classic /v1/models parsing if needed.
 */
class OpenAiModelLoader(private val baseUrl: String = "https://api.openai.com") : ModelLoader {
    private val client = OkHttpClient()
    private val gson = Gson()

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        try {
            val g = GenericModelLoader(baseUrl, Pair("Authorization", "Bearer"))
            val found = g.listModels(apiKey)
            if (found.isNotEmpty()) return found
        } catch (e: Exception) {
            Log.w("OpenAiModelLoader", "generic loader failed: ${e.message}")
        }

        // fallback to original parsing
        try {
            val url = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + "/v1/models" else baseUrl + "/v1/models"
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            if (!resp.isSuccessful) return emptyList()
            val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val out = mutableListOf<ModelInfo>()
            if (json.has("data") && json.get("data").isJsonArray) {
                val arr = json.getAsJsonArray("data")
                arr.forEach { el ->
                    if (el.isJsonObject && el.asJsonObject.has("id")) {
                        out.add(ModelInfo(el.asJsonObject.get("id").asString))
                    }
                }
            }
            return out
        } catch (e: Exception) {
            Log.w("OpenAiModelLoader", "error listing models: ${e.message}")
            return emptyList()
        }
    }
}
