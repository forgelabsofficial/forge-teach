package com.aiteacher.ai

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google model loader — attempts common Google generative language list endpoints.
 */
class GoogleModelLoader(private val baseUrl: String = "https://generativelanguage.googleapis.com") : ModelLoader {
    private val client = OkHttpClient()
    private val gson = Gson()

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        try {
            // Google may require projects/.. path; try /v1/models
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
            // Google may return {"models": ["...", ...]} or complex object
            if (json.has("models") && json.get("models").isJsonArray) {
                val arr = json.getAsJsonArray("models")
                arr.forEach { el ->
                    if (el.isJsonPrimitive) out.add(ModelInfo(el.asString))
                    else if (el.isJsonObject && el.asJsonObject.has("name")) out.add(ModelInfo(el.asJsonObject.get("name").asString))
                }
            }
            return out
        } catch (e: Exception) {
            Log.w("GoogleModelLoader", "error listing models: ${e.message}")
            return emptyList()
        }
    }
}
