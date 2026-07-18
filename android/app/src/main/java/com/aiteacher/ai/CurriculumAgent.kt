package com.aiteacher.ai

import android.content.Context

/**
 * CurriculumAgent
 * Role: Research the student's curriculum online and build a rich context
 * string that subsequent agents use to ground their outputs.
 *
 * Tools: WebSearchAgentTool
 * Memory in:  profile, subjects
 * Memory out: curriculumContext, conversationHistory
 */
class CurriculumAgent : BaseAgent(
    agentName = "CurriculumAgent",
    tools = listOf(WebSearchAgentTool)
) {
    override val systemPrompt = """
You are a curriculum research specialist. Your job is to find accurate, 
up-to-date information about school curricula, syllabi, and exam requirements 
for specific countries and grade levels.

When given a student profile, you will:
1. Identify the key topics covered at their grade level for each subject
2. Note any upcoming exams or assessments they should prepare for
3. Highlight areas that are typically challenging at this level
4. Return a concise, structured summary suitable for an examiner to use

Always be specific to the country, curriculum body, and grade level provided.
Output plain text — no JSON, no markdown headers.
    """.trimIndent()

    override suspend fun run(
        context: Context,
        memory: AgentMemory,
        onStep: (String) -> Unit
    ): AgentMemory {
        val profile = memory.profile ?: return memory
        val subjects = memory.subjects.take(4).joinToString(", ").ifBlank { "core subjects" }

        onStep("🌐 Searching ${profile.curriculumBody} curriculum for ${profile.gradeLevelLabel}…")

        // Use the web search tool to get live curriculum data
        val searchQuery = "${profile.countryName} ${profile.gradeLevelLabel} ${profile.curriculumBody} syllabus $subjects topics"
        val searchResult = WebSearchAgentTool.execute(searchQuery)

        val userMessage = """
${memory.studentContext()}

I searched the web and found:
${searchResult.ifBlank { "No web results — use your training knowledge." }}

Based on this, summarise the key curriculum topics, exam focus areas, and 
typical difficulty points for this student's grade and subjects.
        """.trimIndent()

        onStep("🧠 CurriculumAgent analysing syllabus…")
        val (raw, updatedMemory) = callAi(context, memory, userMessage, maxTokens = 1000)

        val curriculumContext = if (raw.isNotBlank()) {
            "Curriculum context (${profile.curriculumBody} · ${profile.gradeLevelLabel}):\n$raw"
        } else {
            // Fallback to raw search result if AI call fails
            if (searchResult.isNotBlank()) "Curriculum context from web:\n$searchResult" else ""
        }

        onStep("✅ Curriculum context ready")
        return updatedMemory.copy(curriculumContext = curriculumContext)
    }
}
