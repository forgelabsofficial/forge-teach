package com.aiteacher.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelLoaderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun openAiModelLoader_parsesDataArray() {
        val body = "{\"data\":[{\"id\":\"gpt-4o-mini\"},{\"id\":\"gpt-4o-small\"}]}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val base = server.url("/").toString()
        val loader = OpenAiModelLoader(base)
        val res = runCatching { loader.listModels("test") }.getOrElse { emptyList() }
        assertTrue(res.any { it.id == "gpt-4o-mini" })
    }

    @Test
    fun anthropicModelLoader_parsesModelsArray() {
        val body = "{\"models\":[{\"id\":\"claude-2\"}]}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val base = server.url("/").toString()
        val loader = AnthropicModelLoader(base)
        val res = runCatching { loader.listModels("test") }.getOrElse { emptyList() }
        assertTrue(res.any { it.id == "claude-2" })
    }
}
