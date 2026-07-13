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
    @POST("v1/assist/generate")
    suspend fun generate(@Body body: OpenAiRequest): OpenAiResponse

    companion object {
        fun create(apiKey: String, baseUrl: String = "https://generativelanguage.googleapis.com/"): GoogleApi {
            val authInterceptor = Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
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
                .create(GoogleApi::class.java)
        }
    }
}
