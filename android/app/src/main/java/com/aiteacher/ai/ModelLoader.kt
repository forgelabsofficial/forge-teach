package com.aiteacher.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

interface ModelLoader {
    suspend fun listModels(apiKey: String): List<ModelInfo>
}

/**
 * Generic ModelLoader that attempts common endpoints and parses common response shapes.
 * Works for many providers that expose a models or engines endpoint.
 */
class GenericModelLoader(private val baseUrl: String, private val authHeader: Pair<String, String> = Pair("Authorization", "Bearer")) : ModelLoader {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val candidatePaths = listOf("/v1/models", "/models", "/v1/engines", "/v1/models/list", "/models/list")

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        for (path in candidatePaths) {
            try {
                val url = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + path else baseUrl + path
                val req = Request.Builder().url(url)
                    .addHeader(authHeader.first, "${authHeader.second} $apiKey")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: continue
                if (!resp.isSuccessful) continue
                val json = gson.fromJson(body, JsonElement::class.java)
                val models = parseModelsFromJson(json)
                if (models.isNotEmpty()) return models
            } catch (e: Exception) {
                Log.w("GenericModelLoader", "failed path $path: ${e.message}")
            }
        }
        // fallback: return a small set of heuristic defaults
        return listOf(ModelInfo("default"), ModelInfo("small"), ModelInfo("large"))
    }

    private fun parseModelsFromJson(json: JsonElement): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            // common shapes: {"data":[{"id":"..."}]}
            if (obj.has("data") && obj.get("data").isJsonArray) {
                val arr = obj.getAsJsonArray("data")
                arr.forEach { el ->
                    if (el.isJsonObject && el.asJsonObject.has("id")) {
                        out.add(ModelInfo(el.asJsonObject.get("id").asString))
                    }
                }
            }
            // {"models":[{"id":"..."}]}
            if (obj.has("models") && obj.get("models").isJsonArray) {
                val arr = obj.getAsJsonArray("models")
                arr.forEach { el ->
                    if (el.isJsonObject && el.asJsonObject.has("id")) {
                        out.add(ModelInfo(el.asJsonObject.get("id").asString))
                    } else if (el.isJsonPrimitive) {
                        out.add(ModelInfo(el.asString))
                    }
                }
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            arr.forEach { el ->
                if (el.isJsonObject && el.asJsonObject.has("id")) {
                    out.add(ModelInfo(el.asJsonObject.get("id").asString))
                } else if (el.isJsonPrimitive) {
                    out.add(ModelInfo(el.asString))
                }
            }
        }
        return out
    }
}
