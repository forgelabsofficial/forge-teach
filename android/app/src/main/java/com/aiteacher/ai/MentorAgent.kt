package com.aiteacher.ai

import com.aiteacher.data.AppDatabase
import com.aiteacher.model.MentorMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Mentor Agent
 *
 * Implements the persistent tutor persona. Unlike stateless chatbots,
 * the Mentor remembers the student's past struggles, breakthroughs, fears,
 * goals, and preferred learning styles across weeks and months.
 */
object MentorAgent {

    /**
     * Build the personalized system prompt prefix for any teaching or interactive agent.
     */
    suspend fun getMentorSystemPrompt(db: AppDatabase, studentName: String): String = withContext(Dispatchers.IO) {
        val memories = db.mentorMemoryDao().getAllMemories()
        
        val fears = memories.filter { it.category == "fear" }.joinToString("; ") { it.content }
        val goals = memories.filter { it.category == "goal" }.joinToString("; ") { it.content }
        val breakthroughs = memories.filter { it.category == "breakthrough" }.joinToString("; ") { it.content }
        val preferences = memories.filter { it.category == "preference" }.joinToString("; ") { it.content }

        buildString {
            append("You are $studentName's dedicated lifelong AI Learning Companion.\n")
            append("You are NOT a generic search bot. You have been teaching $studentName for years.\n")
            append("Maintain an encouraging, warm, growth-mindset tone. Use analogies and active recall.\n\n")

            if (goals.isNotBlank()) append("• Student Goals: $goals\n")
            if (fears.isNotBlank()) append("• Key Areas of Past Struggle: $fears\n")
            if (breakthroughs.isNotBlank()) append("• Major Breakthroughs Celebrated: $breakthroughs\n")
            if (preferences.isNotBlank()) append("• Learning Style Preferences: $preferences\n")
        }
    }

    /**
     * Record a new memory observation (e.g. breakthrough, fear, or goal).
     */
    suspend fun recordMemory(
        db: AppDatabase,
        key: String,
        category: String,
        content: String,
        importance: Int = 50
    ) = withContext(Dispatchers.IO) {
        db.mentorMemoryDao().upsertMemory(
            MentorMemoryEntity(
                memoryKey = key,
                category = category,
                content = content,
                importanceScore = importance,
                updatedTimestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Generate a daily greeting message from the AI mentor.
     */
    suspend fun getDailyGreeting(db: AppDatabase, studentName: String): String = withContext(Dispatchers.IO) {
        val memories = db.mentorMemoryDao().getAllMemories()
        val recentBreakthrough = memories.firstOrNull { it.category == "breakthrough" }

        if (recentBreakthrough != null) {
            "Welcome back, $studentName! Remember how you mastered ${recentBreakthrough.content}? Let's keep that momentum going today!"
        } else {
            "Ready for today's adventure, $studentName? I've tailored today's mission specifically for your growth!"
        }
    }
}
