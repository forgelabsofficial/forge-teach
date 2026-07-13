package com.aiteacher.ai

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient

// Minimal scaffold for Anthropic-style API. Request/response shapes must be adapted to real provider docs.
interface AnthropicApi {
    @Headers("Content-Type: application/json")
    @POST("v1/assistant/generate")
    suspend fun generate(@Body body: OpenAiRequest): OpenAiResponse

    companion object {
        fun create(apiKey: String, baseUrl: String = "https://api.anthropic.com/"): AnthropicApi {
            val authInterceptor = Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("x-api-key", apiKey)
                    .build()
                chain.proceed(req)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(RetryInterceptor())
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AnthropicApi::class.java)
        }
    }
}
