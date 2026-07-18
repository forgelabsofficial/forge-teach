package com.aiteacher.ai

import com.aiteacher.onboarding.Assessment
import com.aiteacher.onboarding.CapabilityResult
import com.aiteacher.onboarding.CapabilityTest
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.StudentProfile

/**
 * Shared memory passed through the agent pipeline.
 * Each agent reads what it needs and writes its output back.
 * Immutable — agents return a copy with their additions.
 */
data class AgentMemory(
    // ── Input (set before pipeline starts) ───────────────────────────────────
    val profile: StudentProfile? = null,
    val subjects: List<String> = emptyList(),
    val availability: Map<String, List<String>> = emptyMap(),
    val sessionLengthMinutes: Int = 30,
    val weeksToSchedule: Int = 4,

    // ── CurriculumAgent output ────────────────────────────────────────────────
    val curriculumContext: String = "",

    // ── AssessmentAgent output ────────────────────────────────────────────────
    val capabilityTest: CapabilityTest? = null,
    val capabilityResult: CapabilityResult? = null,
    val testRawResponse: String? = null,
    val testSource: CapabilityTestClient.TestSource? = null,
    val testFailureReason: String? = null,

    // ── PlanAgent output ──────────────────────────────────────────────────────
    val plan: Plan? = null,
    val planRawResponse: String? = null,

    // ── TopicRankerAgent output ───────────────────────────────────────────────
    val rankedTopics: List<com.aiteacher.onboarding.RankedTopic> = emptyList(),

    // ── Conversation history (for multi-turn memory) ──────────────────────────
    // Each entry: Pair(role, content) — "system" | "user" | "assistant"
    val conversationHistory: List<Pair<String, String>> = emptyList()
) {
    fun withMessage(role: String, content: String) = copy(
        conversationHistory = conversationHistory + (role to content)
    )

    fun historyAsMessages(): List<Map<String, String>> =
        conversationHistory.map { (role, content) -> mapOf("role" to role, "content" to content) }

    /** Summarise what we know about the student for injection into any prompt */
    fun studentContext(): String {
        val p = profile ?: return "Unknown student"
        val subjectStr = subjects.joinToString(", ").ifBlank { "general subjects" }
        val examStr = p.keyExams.joinToString(", ").ifBlank { "standard exams" }
        return """
Student: ${p.name}
Country: ${p.countryName} | Curriculum: ${p.curriculumBody} | Grade: ${p.gradeLevelLabel}
Key exams: $examStr
Subjects: $subjectStr
        """.trimIndent()
    }

    /** Summarise test results for injection into the plan prompt */
    fun testResultContext(): String {
        val result = capabilityResult ?: return "No test results available."
        val pct = if (result.maxScore > 0) (result.totalScore * 100) / result.maxScore else 0
        val subjectLines = result.subjectScores.entries
            .sortedBy { it.value }
            .joinToString("\n") { (subj, score) ->
                val level = when {
                    score >= 80 -> "strong"
                    score >= 50 -> "moderate"
                    else -> "needs work"
                }
                "  - $subj: $score% ($level)"
            }
        return """
Overall score: $pct% (${result.totalScore}/${result.maxScore})
Subject breakdown:
$subjectLines
        """.trimIndent()
    }
}
