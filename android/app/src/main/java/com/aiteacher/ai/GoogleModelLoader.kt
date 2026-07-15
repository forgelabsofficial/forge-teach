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
        // delegate to GenericModelLoader which tries multiple common paths and shapes
        try {
            val g = GenericModelLoader(baseUrl, Pair("Authorization", "Bearer"))
            val found = g.listModels(apiKey)
            if (found.isNotEmpty()) return found
        } catch (e: Exception) {
            Log.w("GoogleModelLoader", "generic loader failed: ${e.message}")
        }

        // fallback: try a few Google-specific variants
        try {
            val candidate = listOf("/v1/models", "/models", "/v1/projects/-/models")
            for (path in candidate) {
                try {
                    val url = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + path else baseUrl + path
                    val req = Request.Builder().url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: continue
                    if (!resp.isSuccessful) continue
                    val json = gson.fromJson(body, com.google.gson.JsonElement::class.java)
                    val out = mutableListOf<ModelInfo>()
                    if (json.isJsonObject) {
                        val obj = json.asJsonObject
                        if (obj.has("models") && obj.get("models").isJsonArray) {
                            obj.getAsJsonArray("models").forEach { el ->
                                if (el.isJsonPrimitive) out.add(ModelInfo(el.asString))
                                else if (el.isJsonObject && el.asJsonObject.has("name")) out.add(ModelInfo(el.asJsonObject.get("name").asString))
                            }
                        }
                    } else if (json.isJsonArray) {
                        json.asJsonArray.forEach { el ->
                            if (el.isJsonPrimitive) out.add(ModelInfo(el.asString))
                        }
                    }
                    if (out.isNotEmpty()) return out
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("GoogleModelLoader", "error listing models: ${e.message}")
        }
        return emptyList()
    }
}
