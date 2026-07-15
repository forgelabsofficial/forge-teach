package com.aiteacher.ai

import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

class GroqModelLoader(private val baseUrl: String = "https://api.groq.ai") : ModelLoader {
    private val client = OkHttpClient()
    private val gson = Gson()

    override suspend fun listModels(apiKey: String): List<ModelInfo> {
        try {
            val url = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) + "/models" else baseUrl + "/models"
            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return emptyList()
            if (!resp.isSuccessful) return emptyList()
            val json = gson.fromJson(body, com.google.gson.JsonElement::class.java)
            val out = mutableListOf<ModelInfo>()
            if (json.isJsonArray) {
                json.asJsonArray.forEach { el -> if (el.isJsonPrimitive) out.add(ModelInfo(el.asString)) }
            } else if (json.isJsonObject) {
                val obj = json.asJsonObject
                if (obj.has("models") && obj.get("models").isJsonArray) {
                    obj.getAsJsonArray("models").forEach { el -> if (el.isJsonPrimitive) out.add(ModelInfo(el.asString)) }
                }
            }
            return out
        } catch (e: Exception) {
            Log.w("GroqModelLoader", "error listing models: ${e.message}")
            return emptyList()
        }
    }
}
