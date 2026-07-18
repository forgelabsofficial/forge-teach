package com.aiteacher.ai

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// NOTE: Minimal scaffold. This adds an Authorization interceptor to include the API key.
interface OpenAiApi {
    // Use Chat Completions-like endpoint for a structured response containing a JSON plan in the content
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun chat(@Body body: Any): OpenAiResponse

    companion object {
        fun create(
            apiKey: String,
            baseUrl: String = "https://api.openai.com/",
            headerName: String = "Authorization",
            headerValuePrefix: String = "Bearer "
        ): OpenAiApi {
            val authInterceptor = Interceptor { chain ->
                val headerValue = if (headerValuePrefix.isEmpty()) apiKey else "$headerValuePrefix$apiKey"
                val req = chain.request().newBuilder()
                    .addHeader(headerName, headerValue)
                    .build()
                chain.proceed(req)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(RetryInterceptor())
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApi::class.java)
        }
    }
}
