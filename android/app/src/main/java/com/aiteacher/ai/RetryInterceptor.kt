package com.aiteacher.ai

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min

class RetryInterceptor(private val maxRetries: Int = 3, private val baseDelayMs: Long = 300) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var lastEx: IOException? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                if (response.isSuccessful) return response
                // Retry on server errors
                if (response.code in 500..599) {
                    response.close()
                    attempt++
                    val backoff = computeBackoff(attempt)
                    Log.w("RetryInterceptor", "Server error ${response.code}. Retrying in ${backoff}ms (attempt=$attempt)")
                    Thread.sleep(backoff)
                    continue
                }
                return response
            } catch (ioe: IOException) {
                lastEx = ioe
                attempt++
                val backoff = computeBackoff(attempt)
                Log.w("RetryInterceptor", "Network error: ${ioe.message}. Retrying in ${backoff}ms (attempt=$attempt)")
                try { Thread.sleep(backoff) } catch (_: InterruptedException) {}
            }
        }
        throw lastEx ?: IOException("Unknown network error after retries")
    }

    private fun computeBackoff(attempt: Int): Long {
        val exp = 1 shl (attempt - 1)
        val delay = baseDelayMs * exp
        return min(delay, 10_000L)
    }
}
