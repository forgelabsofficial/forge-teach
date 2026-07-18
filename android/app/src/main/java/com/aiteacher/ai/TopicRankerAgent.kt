package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.onboarding.RankedTopic
import com.google.gson.Gson
import java.time.LocalDate
import java.time.Month

/**
 * TopicRankerAgent
 *
 * Role: Given the full curriculum context and test results, enumerate ALL topics
 * for the student's subjects and rank them using four dimensions:
 *
 *   1. Importance       — exam weight, how often tested, core vs peripheral
 *   2. Dependency       — prerequisite chain (foundational topics rank higher)
 *   3. Current term     — what is the school likely teaching right now
 *   4. Weakness         — derived from the student's test scores per subject/question
 *
 * Final rank = weighted composite of all four scores.
 * PlanAgent reads rankedTopics from memory and slots them into dates.
 *
 * Memory in:  profile, subjects, curriculumContext, capabilityResult, capabilityTest
 * Memory out: rankedTopics
 */
class TopicRankerAgent : BaseAgent(
    agentName = "TopicRankerAgent",
    tools = listOf(WebSearchAgentTool)
) {
    private val gson = Gson()

    override val systemPrompt = """
You are a curriculum sequencing expert. Your job is to list and rank ALL topics 
for a student's subjects in the order they should be studied.

Ranking rules (apply in this order):
1. DEPENDENCY first — a topic that is a prerequisite for others must come before them, always.
2. CURRENT TERM second — topics likely being taught in school right now get boosted.
3. IMPORTANCE third — topics with high exam weight or broad applicability rank higher.
4. WEAKNESS fourth — topics in subjects where the student scored poorly rank higher.

For each topic output:
- id: snake_case unique identifier
- subject: subject name
- title: clear human-readable topic name
- rank: integer starting at 1 (1 = study first)
- importanceScore: 0-100
- dependencyScore: 0-100 (how foundational — how many other topics need this first)
- currentTermScore: 0-100 (likelihood this is being taught in school right now)
- weaknessScore: 0-100 (100 = student is very weak here)
- dependsOn: array of topic ids that must be studied before this one
- isCurrentTerm: true/false
- suggestedSessionCount: 1-3 (how many study sessions this topic needs)

You ALWAYS output ONLY a valid JSON array. No explanation, no markdown.
Format: [{"id":"...","subject":"...","title":"...","rank":1,"importanceScore":80,"dependencyScore":90,"currentTermScore":60,"weaknessScore":70,"dependsOn":[],"isCurrentTerm":true,"suggestedSessionCount":2}]
    """.trimIndent()

    override suspend fun run(
        context: Context,
        memory: AgentMemory,
        onStep: (String) -> Unit
    ): AgentMemory {
        val profile = memory.profile ?: return memory
        val subjects = memory.subjects.ifEmpty { return memory }
        val currentMonth = LocalDate.now().month
        val termHint = termHint(currentMonth, profile.countryName)
        val subjectList = subjects.joinToString(", ")

        // Build weakness context from test results
        val weaknessContext = buildWeaknessContext(memory)

        val userMessage = """
${memory.studentContext()}

${memory.curriculumContext.take(1200).ifBlank { "Use your training knowledge of this curriculum." }}

$weaknessContext

Current date: ${LocalDate.now()}
School term context: $termHint

List and rank ALL topics for these subjects: $subjectList

Cover the full syllabus for ${profile.gradeLevelLabel} under ${profile.curriculumBody}.
Aim for 6-12 topics per subject. Apply all four ranking dimensions.
Return ONLY the JSON array.
        """.trimIndent()

        onStep("🗂️ TopicRankerAgent ranking curriculum topics…")
        val (raw, updatedMemory) = callAi(context, memory, userMessage, maxTokens = 4000)

        val ranked = parseRankedTopics(raw)

        if (ranked.isNotEmpty()) {
            onStep("✅ Ranked ${ranked.size} topics across $subjectList")
            return updatedMemory.copy(rankedTopics = ranked)
        }

        // Fallback: build a basic ranked list from subjects + test scores
        onStep("⚠️ Topic ranking fallback — using score-based ordering")
        Log.w("TopicRankerAgent", "Parse failed. Raw: ${raw.take(200)}")
        val fallback = buildFallbackRanking(memory)
        return updatedMemory.copy(rankedTopics = fallback)
    }

    private fun buildWeaknessContext(memory: AgentMemory): String {
        val result = memory.capabilityResult ?: return ""
        val test = memory.capabilityTest ?: return ""

        // Find which specific sub-topics the student got wrong
        val wrongQuestions = result.answers
            .filter { !it.correct }
            .mapNotNull { answer ->
                test.questions.find { it.id == answer.questionId }
            }

        val wrongBySubject = wrongQuestions.groupBy { it.subject }
        if (wrongBySubject.isEmpty()) return ""

        val lines = wrongBySubject.entries.joinToString("\n") { (subj, qs) ->
            val topics = qs.joinToString("; ") { it.questionText.take(60) }
            "  $subj — struggled with: $topics"
        }

        val subjectScores = result.subjectScores.entries
            .sortedBy { it.value }
            .joinToString(", ") { "${it.key}: ${it.value}%" }

        return """
Student weakness analysis:
Subject scores (lowest first): $subjectScores
Specific questions answered incorrectly:
$lines
        """.trimIndent()
    }

    private fun termHint(month: Month, country: String): String {
        // Rough term detection by country/hemisphere
        val isNorthern = !listOf("australia", "new zealand", "south africa", "brazil", "argentina")
            .any { country.lowercase().contains(it) }

        return if (isNorthern) when (month) {
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "Autumn/Fall term — start of academic year, foundational topics"
            Month.DECEMBER -> "End of autumn term — revision and assessments"
            Month.JANUARY, Month.FEBRUARY, Month.MARCH -> "Spring term — mid-year, building on foundations"
            Month.APRIL -> "End of spring term — exam preparation beginning"
            Month.MAY, Month.JUNE -> "Summer term — exam season, revision critical"
            Month.JULY, Month.AUGUST -> "Summer holidays — optional enrichment"
            else -> "Mid academic year"
        } else when (month) {
            Month.JANUARY, Month.FEBRUARY, Month.MARCH -> "Start of academic year — foundational topics"
            Month.APRIL, Month.MAY -> "Mid first term"
            Month.JUNE, Month.JULY -> "Second term"
            Month.AUGUST, Month.SEPTEMBER -> "Third term — exam preparation"
            Month.OCTOBER, Month.NOVEMBER -> "End of year — revision"
            Month.DECEMBER -> "Year end / holidays"
            else -> "Mid academic year"
        }
    }

    private fun parseRankedTopics(raw: String): List<RankedTopic> {
        if (raw.isBlank()) return emptyList()
        return try {
            var text = raw.trim()
            if (text.contains("```json")) text = text.substringAfter("```json").substringBefore("```").trim()
            else if (text.contains("```")) text = text.substringAfter("```").substringBefore("```").trim()

            val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
            arr.mapIndexedNotNull { i, el ->
                if (!el.isJsonObject) return@mapIndexedNotNull null
                val o = el.asJsonObject
                RankedTopic(
                    id = o.get("id")?.asString ?: "topic_$i",
                    subject = o.get("subject")?.asString ?: "",
                    title = o.get("title")?.asString ?: return@mapIndexedNotNull null,
                    rank = o.get("rank")?.asInt ?: (i + 1),
                    importanceScore = o.get("importanceScore")?.asInt ?: 50,
                    dependencyScore = o.get("dependencyScore")?.asInt ?: 50,
                    currentTermScore = o.get("currentTermScore")?.asInt ?: 50,
                    weaknessScore = o.get("weaknessScore")?.asInt ?: 50,
                    dependsOn = o.getAsJsonArray("dependsOn")?.map { it.asString } ?: emptyList(),
                    isCurrentTerm = o.get("isCurrentTerm")?.asBoolean ?: false,
                    suggestedSessionCount = (o.get("suggestedSessionCount")?.asInt ?: 1).coerceIn(1, 3)
                )
            }.sortedBy { it.rank }
        } catch (e: Exception) {
            Log.w("TopicRankerAgent", "parseRankedTopics failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildFallbackRanking(memory: AgentMemory): List<RankedTopic> {
        val subjectScores = memory.capabilityResult?.subjectScores ?: emptyMap()
        var rank = 1
        // Weakest subjects first, generic topic titles
        return memory.subjects
            .sortedBy { subjectScores[it] ?: 50 }
            .flatMap { subject ->
                val weakness = 100 - (subjectScores[subject] ?: 50)
                listOf(
                    RankedTopic("${subject}_foundations", subject, "${subject.replaceFirstChar { it.uppercase() }} — Foundations", rank++, 90, 90, 70, weakness, emptyList(), true, 2),
                    RankedTopic("${subject}_core", subject, "${subject.replaceFirstChar { it.uppercase() }} — Core Concepts", rank++, 80, 60, 60, weakness, listOf("${subject}_foundations"), false, 2),
                    RankedTopic("${subject}_advanced", subject, "${subject.replaceFirstChar { it.uppercase() }} — Advanced Topics", rank++, 60, 30, 40, (weakness * 0.7).toInt(), listOf("${subject}_core"), false, 1),
                    RankedTopic("${subject}_revision", subject, "${subject.replaceFirstChar { it.uppercase() }} — Revision", rank++, 70, 10, 50, (weakness * 0.5).toInt(), listOf("${subject}_core"), false, 1)
                )
            }
    }
}
