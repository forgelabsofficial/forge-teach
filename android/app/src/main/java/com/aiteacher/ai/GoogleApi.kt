package com.aiteacher.ai

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient

// Minimal scaffold for Google PaLM-style API. Adapt request/response to real API.
interface GoogleApi {
    @Headers("Content-Type: application/json")
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generate(@Body body: GeminiRequest): GeminiResponse

    companion object {
        fun create(apiKey: String, baseUrl: String = "https://generativelanguage.googleapis.com/"): GoogleApi {
            val queryKeyInterceptor = Interceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("key", apiKey)
                    .build()
                val req = original.newBuilder().url(url).build()
                chain.proceed(req)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(queryKeyInterceptor)
                .addInterceptor(RetryInterceptor())
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(GoogleApi::class.java)
        }
    }
}
