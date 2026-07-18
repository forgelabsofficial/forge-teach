package com.aiteacher.ai

object XpEngine {
    const val XP_SESSION_COMPLETE = 50
    const val XP_QUIZ_PASS        = 30   // score >= 70%
    const val XP_QUIZ_ATTEMPT     = 10
    const val XP_EXAM_COMPLETE    = 100
    const val XP_STREAK_BONUS     = 20   // per day on a streak >= 3

    fun xpForQuiz(scorePercent: Int): Int =
        if (scorePercent >= 70) XP_QUIZ_PASS else XP_QUIZ_ATTEMPT

    fun xpForExam(scorePercent: Int): Int =
        XP_EXAM_COMPLETE + if (scorePercent >= 80) 50 else 0

    fun levelFromXp(xp: Int): Int = (xp / 200) + 1

    fun xpToNextLevel(xp: Int): Int {
        val level = levelFromXp(xp)
        return level * 200 - xp
    }

    fun progressInLevel(xp: Int): Float {
        val level = levelFromXp(xp)
        val base = (level - 1) * 200
        return (xp - base) / 200f
    }
}
