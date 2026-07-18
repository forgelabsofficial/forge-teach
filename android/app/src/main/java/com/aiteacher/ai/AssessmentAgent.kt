package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.CapabilityQuestion
import com.aiteacher.onboarding.CapabilityTest
import com.google.gson.Gson
import com.google.gson.JsonElement

/**
 * AssessmentAgent
 * Role: Generate a calibrated capability test grounded in the curriculum context
 * produced by CurriculumAgent.
 *
 * Tools: WebSearchAgentTool (for subject-specific question grounding)
 * Memory in:  profile, subjects, curriculumContext, conversationHistory
 * Memory out: capabilityTest, testRawResponse, testSource, testFailureReason
 */
class AssessmentAgent : BaseAgent(
    agentName = "AssessmentAgent",
    tools = listOf(WebSearchAgentTool)
) {
    private val gson = Gson()

    override val systemPrompt = """
You are an expert school examiner. Your job is to generate multiple-choice 
capability assessment questions that are:
- Precisely calibrated to the student's grade level and curriculum
- Grounded in the curriculum context you have been given
- Varied in difficulty (easy 30%, medium 50%, hard 20%)
- Distributed evenly across all subjects

You ALWAYS output ONLY a valid JSON array. No explanation, no markdown, no preamble.
JSON format: [{"id":"q1","subject":"math","question":"...","options":["A","B","C","D"],"correctIndex":2,"difficulty":2}]
correctIndex is 0-based. difficulty: 1=easy, 3=medium, 5=hard.
    """.trimIndent()

    override suspend fun run(
        context: Context,
        memory: AgentMemory,
        onStep: (String) -> Unit
    ): AgentMemory {
        val profile = memory.profile ?: return memory
        val subjects = memory.subjects

        val subjectCount = subjects.size.coerceAtLeast(1)
        val gradeNum = extractGradeNumber(profile.gradeLevelLabel)
        val questionsPerSubject = when {
            gradeNum <= 3 -> 3
            gradeNum <= 6 -> 4
            gradeNum <= 9 -> 5
            else          -> 6
        }
        val questionCount = (subjectCount * questionsPerSubject).coerceIn(8, 36)
        val perSubject = (questionCount / subjectCount).coerceAtLeast(3)
        val subjectList = subjects.joinToString(", ").ifBlank { "all core subjects" }

        val userMessage = """
${memory.studentContext()}

${memory.curriculumContext.ifBlank { "No curriculum context available — use your training knowledge." }}

Generate exactly $questionCount multiple-choice questions.
Subjects: $subjectList (~$perSubject per subject)
Requirements:
1. Align strictly with ${profile.curriculumBody} for ${profile.gradeLevelLabel}
2. Easy 30% / Medium 50% / Hard 20%
3. Exactly 4 options per question, one correct
4. Return ONLY the JSON array — nothing else
        """.trimIndent()

        onStep("🤖 AssessmentAgent generating $questionCount questions…")
        val (raw, updatedMemory) = callAi(context, memory, userMessage, maxTokens = 4000)

        val parsed = parseQuestions(raw)

        if (parsed.size >= 5) {
            onStep("✅ AssessmentAgent got ${parsed.size} questions")
            return updatedMemory.copy(
                capabilityTest = CapabilityTest(parsed),
                testRawResponse = raw,
                testSource = CapabilityTestClient.TestSource.AI_GENERATED
            )
        }

        // Retry with simpler prompt, keeping conversation history for context
        onStep("🔄 Retrying with simplified prompt…")
        val retryMessage = "Generate $questionCount school exam questions for a ${profile.gradeLevelLabel} student in ${profile.countryName} studying $subjectList. Return ONLY a JSON array: [{\"id\":\"q1\",\"subject\":\"math\",\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"correctIndex\":0,\"difficulty\":2}]"
        val (retryRaw, retryMemory) = callAi(context, updatedMemory, retryMessage, maxTokens = 4000)
        val retryParsed = parseQuestions(retryRaw)

        if (retryParsed.isNotEmpty()) {
            onStep("✅ AssessmentAgent got ${retryParsed.size} questions on retry")
            return retryMemory.copy(
                capabilityTest = CapabilityTest(retryParsed),
                testRawResponse = retryRaw,
                testSource = CapabilityTestClient.TestSource.AI_GENERATED
            )
        }

        // Fallback to built-in questions
        onStep("⚠️ AI unavailable — loading built-in questions")
        val fallback = CapabilityTestClient.builtInAdaptiveFallback(profile, subjects)
        val reason = "AI returned ${parsed.size} questions (attempt 1) then ${retryParsed.size} (attempt 2). Raw: ${raw.take(200)}"
        return retryMemory.copy(
            capabilityTest = CapabilityTest(fallback),
            testRawResponse = raw.ifBlank { retryRaw },
            testSource = CapabilityTestClient.TestSource.BUILT_IN_FALLBACK,
            testFailureReason = reason
        )
    }

    private fun parseQuestions(raw: String): List<CapabilityQuestion> {
        if (raw.isBlank()) return emptyList()
        return try {
            var text = raw.trim()
            if (text.contains("```json")) text = text.substringAfter("```json").substringBefore("```").trim()
            else if (text.contains("```")) text = text.substringAfter("```").substringBefore("```").trim()
            if (text.startsWith("{")) {
                val obj = gson.fromJson(text, com.google.gson.JsonObject::class.java)
                text = (obj.get("questions") ?: obj.get("data") ?: obj.get("items"))?.toString() ?: text
            }
            val el: JsonElement = gson.fromJson(text, JsonElement::class.java)
            val arr = if (el.isJsonArray) el.asJsonArray else return emptyList()
            arr.mapIndexedNotNull { i, qEl ->
                if (!qEl.isJsonObject) return@mapIndexedNotNull null
                val o = qEl.asJsonObject
                val options = o.getAsJsonArray("options")?.map { it.asString } ?: return@mapIndexedNotNull null
                if (options.size < 2) return@mapIndexedNotNull null
                CapabilityQuestion(
                    id = o.get("id")?.asString ?: "q${i + 1}",
                    subject = o.get("subject")?.asString ?: "general",
                    questionText = o.get("question")?.asString ?: return@mapIndexedNotNull null,
                    options = options,
                    correctIndex = (o.get("correctIndex")?.asInt ?: 0).coerceIn(0, options.size - 1),
                    difficulty = o.get("difficulty")?.asInt ?: 2
                )
            }
        } catch (e: Exception) {
            Log.w("AssessmentAgent", "parse failed: ${e.message}")
            emptyList()
        }
    }

    private fun extractGradeNumber(label: String): Int {
        val num = label.filter { it.isDigit() }
        return if (num.isNotEmpty()) num.toInt() else 6
    }
}
