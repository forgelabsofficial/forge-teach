package com.aiteacher.ai

import kotlin.random.Random

object Backoff {
    fun compute(attempt: Int, baseMs: Long = 300, maxMs: Long = 10_000L): Long {
        if (attempt <= 0) return baseMs
        val exp = 1 shl (attempt - 1)
        val delay = baseMs * exp
        val capped = if (delay > maxMs) maxMs else delay
        val jitter = (capped * 0.3).toLong()
        return capped - jitter + Random.nextLong(0, jitter + 1)
    }
}
