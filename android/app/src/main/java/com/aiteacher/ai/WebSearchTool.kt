package com.aiteacher.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Zero-API-key web search backed by DuckDuckGo.
 *
 * Strategy:
 * 1. DuckDuckGo Instant Answer API (json) — fast, structured, no key needed.
 * 2. DuckDuckGo HTML search scrape — fallback, extracts result snippets.
 *
 * Returns a short text summary (≤ 2000 chars) suitable for injecting into an AI prompt.
 */
object WebSearchTool {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (compatible; ForgeTeach/1.0)")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    /**
     * Search and return a text summary. Never throws — returns empty string on any failure.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val instant = tryInstantAnswer(query)
            if (instant.isNotBlank()) return@withContext instant.take(2000)
            val html = tryHtmlSearch(query)
            html.take(2000)
        } catch (e: Exception) {
            Log.w("WebSearchTool", "search failed: ${e.message}")
            ""
        }
    }

    // ─── DuckDuckGo Instant Answer API ────────────────────────────────────────

    private fun tryInstantAnswer(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        val req = Request.Builder().url(url).get().build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return ""
        if (!resp.isSuccessful) return ""

        return try {
            val gson = com.google.gson.Gson()
            val json = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val parts = mutableListOf<String>()

            json.get("Abstract")?.asString?.takeIf { it.isNotBlank() }?.let { parts += it }
            json.get("Answer")?.asString?.takeIf { it.isNotBlank() }?.let { parts += it }
            json.get("Definition")?.asString?.takeIf { it.isNotBlank() }?.let { parts += it }

            // Related topics (first 5)
            json.getAsJsonArray("RelatedTopics")?.take(5)?.forEach { el ->
                if (el.isJsonObject) {
                    el.asJsonObject.get("Text")?.asString?.takeIf { it.isNotBlank() }?.let { parts += it }
                }
            }

            parts.joinToString("\n").trim()
        } catch (e: Exception) { "" }
    }

    // ─── DuckDuckGo HTML scrape fallback ─────────────────────────────────────

    private fun tryHtmlSearch(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encoded"
        val req = Request.Builder().url(url).get().build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: return ""
        if (!resp.isSuccessful) return ""

        // Very simple extraction: grab <a class="result__snippet"> content
        val snippets = mutableListOf<String>()
        val snippetRegex = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        snippetRegex.findAll(body).take(6).forEach { match ->
            val text = match.groupValues[1]
                .replace(Regex("<[^>]+>"), "") // strip inner tags
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#x27;", "'").trim()
            if (text.isNotBlank()) snippets += text
        }
        return snippets.joinToString("\n").trim()
    }
}
