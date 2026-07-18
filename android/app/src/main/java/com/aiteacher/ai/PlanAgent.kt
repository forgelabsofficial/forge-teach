package com.aiteacher.ai

import android.content.Context
import android.util.Log
import com.aiteacher.data.AppDatabase
import com.aiteacher.onboarding.Plan
import com.aiteacher.onboarding.RankedTopic
import com.aiteacher.onboarding.SessionItem
import com.google.gson.Gson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * PlanAgent
 *
 * Role: Slot ranked topics into the student's availability slots, applying:
 * - Dependency ordering (prerequisites always before dependents)
 * - Weakness weighting (more sessions + earlier placement for weak topics)
 * - Spaced repetition (strong topics reappear at increasing intervals)
 * - Current-term boosting (topics being taught now appear in week 1-2)
 * - Session coherence (related topics in consecutive sessions)
 *
 * Memory in:  rankedTopics, availability, sessionLengthMinutes, weeksToSchedule,
 *             capabilityResult, profile, conversationHistory
 * Memory out: plan, planRawResponse
 */
class PlanAgent : BaseAgent(
    agentName = "PlanAgent",
    tools = listOf(AvailabilityTool)
) {
    private val gson = Gson()
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    override val systemPrompt = """
You are a personalised learning planner. You receive a ranked list of curriculum 
topics and a student's availability, and you produce a study schedule.

Rules you must follow:
1. Respect dependency order — never schedule a topic before its prerequisites
2. Weak topics (high weaknessScore) get more sessions and appear earlier
3. Current-term topics appear in weeks 1-2
4. Strong topics (low weaknessScore) appear as spaced revision: week 1, week 3, week 6
5. Each session has ONE focused topic — no mixing
6. Session titles are specific: "Algebra — solving linear equations", not "Math"
7. Schedule only within the provided availability slots

Output ONLY a valid JSON object:
{
  "weeks": 4,
  "sessions": [
    {"date":"YYYY-MM-DD","topic":"...","subject":"...","duration":30,"isoDateTime":"...","topicRank":1,"isRevision":false,"weaknessScore":80}
  ]
}
    """.trimIndent()

    override suspend fun run(
        context: Context,
        memory: AgentMemory,
        onStep: (String) -> Unit
    ): AgentMemory {
        val profile = memory.profile ?: return memory
        val rankedTopics = memory.rankedTopics

        // If we have ranked topics, try AI scheduling first
        if (rankedTopics.isNotEmpty()) {
            onStep("📅 PlanAgent scheduling ${rankedTopics.size} ranked topics…")

            val topicsJson = rankedTopics.take(30).joinToString("\n") { t ->
                "rank=${t.rank} subject=${t.subject} title=\"${t.title}\" weakness=${t.weaknessScore} currentTerm=${t.isCurrentTerm} sessions=${t.suggestedSessionCount} dependsOn=[${t.dependsOn.joinToString(",")}]"
            }

            val availabilityText = AvailabilityTool.format(memory.availability, memory.sessionLengthMinutes)
            val today = LocalDate.now().toString()

            val userMessage = """
${memory.studentContext()}

${memory.testResultContext()}

Ranked topics to schedule (in priority order):
$topicsJson

$availabilityText

Today: $today | Plan: ${memory.weeksToSchedule} weeks | Session: ${memory.sessionLengthMinutes}min

Build the full schedule. Apply spaced repetition for revision sessions.
Return ONLY the JSON object.
            """.trimIndent()

            val (raw, updatedMemory) = callAi(context, memory, userMessage, maxTokens = 4000)
            val plan = parsePlan(raw)

            if (plan != null && plan.sessions.isNotEmpty()) {
                onStep("✅ PlanAgent scheduled ${plan.sessions.size} sessions")
                return updatedMemory.copy(plan = plan, planRawResponse = raw)
            }

            onStep("⚠️ AI scheduling failed — using local ranked scheduler")
            Log.w("PlanAgent", "Plan parse failed. Raw: ${raw.take(300)}")
            val fallback = buildRankedLocalPlan(memory, rankedTopics)
            return updatedMemory.copy(plan = fallback, planRawResponse = raw)
        }

        // No ranked topics — basic fallback
        onStep("📅 PlanAgent building schedule from subject scores…")
        val fallback = buildRankedLocalPlan(memory, emptyList())
        return memory.copy(plan = fallback)
    }

    // ── Local ranked scheduler (fallback) ────────────────────────────────────

    /**
     * Build a local plan from ranked topics, injecting Memory Boost sessions
     * for topics the MemoryAgent predicts are due for review.
     */
    private fun buildRankedLocalPlan(memory: AgentMemory, rankedTopics: List<RankedTopic>): Plan {
        val zoneId = ZoneId.systemDefault()
        val slots = collectSlots(memory.availability, memory.weeksToSchedule, zoneId)
        val sessions = mutableListOf<SessionItem>()

        // If there's an AppDatabase available, check for review candidates
        // PlanAgent doesn't have direct DB access in pipeline mode,
        // but the context is available. Try to inject Memory Boost sessions.
        // Review candidates will be injected by a separate daily check in PlanViewModel.

        if (rankedTopics.isEmpty()) {
            // Pure subject-score fallback
            val subjectScores = memory.capabilityResult?.subjectScores ?: emptyMap()
            val sortedSubjects = memory.subjects.sortedBy { subjectScores[it] ?: 50 }
            slots.forEachIndexed { i, slot ->
                val subject = sortedSubjects[i % sortedSubjects.size.coerceAtLeast(1)]
                sessions.add(slot.toSessionItem(
                    topic = "${subject.replaceFirstChar { it.uppercase() }} — Study Session",
                    subject = subject,
                    duration = memory.sessionLengthMinutes,
                    rank = i + 1,
                    weaknessScore = 100 - (subjectScores[subject] ?: 50)
                ))
            }
            return Plan(memory.weeksToSchedule, sessions)
        }

        // Topological sort — respect dependencies
        val ordered = topologicalSort(rankedTopics)

        // Expand topics into session slots based on suggestedSessionCount
        // Weak topics (weaknessScore > 60) get extra revision sessions
        val sessionQueue = mutableListOf<Pair<RankedTopic, Boolean>>() // topic, isRevision
        for (topic in ordered) {
            repeat(topic.suggestedSessionCount) { sessionQueue.add(topic to false) }
            // Add spaced revision for weak topics
            if (topic.weaknessScore >= 60) {
                sessionQueue.add(topic to true) // revision pass
            }
        }

        // Assign to slots
        slots.forEachIndexed { i, slot ->
            if (i >= sessionQueue.size) return@forEachIndexed
            val (topic, isRevision) = sessionQueue[i]
            sessions.add(slot.toSessionItem(
                topic = if (isRevision) "${topic.title} — Revision" else topic.title,
                subject = topic.subject,
                duration = memory.sessionLengthMinutes,
                rank = topic.rank,
                isRevision = isRevision,
                weaknessScore = topic.weaknessScore
            ))
        }

        return Plan(memory.weeksToSchedule, sessions)
    }

    /** Kahn's algorithm — topics with no unresolved dependencies come first */
    private fun topologicalSort(topics: List<RankedTopic>): List<RankedTopic> {
        val idMap = topics.associateBy { it.id }
        val inDegree = topics.associate { it.id to it.dependsOn.count { dep -> idMap.containsKey(dep) } }.toMutableMap()
        val queue = ArrayDeque<RankedTopic>()
        val result = mutableListOf<RankedTopic>()

        // Seed with topics that have no dependencies, sorted by rank
        topics.filter { (inDegree[it.id] ?: 0) == 0 }.sortedBy { it.rank }.forEach { queue.add(it) }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            // Reduce in-degree for topics that depended on this one
            topics.filter { current.id in it.dependsOn }.forEach { dependent ->
                val newDegree = (inDegree[dependent.id] ?: 1) - 1
                inDegree[dependent.id] = newDegree
                if (newDegree == 0) {
                    // Insert in rank order
                    val insertAt = queue.indexOfFirst { it.rank > dependent.rank }.let { if (it == -1) queue.size else it }
                    queue.add(insertAt, dependent)
                }
            }
        }

        // Append any remaining (circular deps or missed) sorted by rank
        val remaining = topics.filter { t -> result.none { it.id == t.id } }.sortedBy { it.rank }
        result.addAll(remaining)
        return result
    }

    private fun collectSlots(
        availability: Map<String, List<String>>,
        weeks: Int,
        zoneId: ZoneId
    ): List<ZonedDateTime> {
        val dayMap = mapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY,
            "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY
        )
        val slots = mutableListOf<ZonedDateTime>()
        val start = LocalDate.now()
        val end = start.plusWeeks(weeks.toLong())
        var date = start

        if (availability.isEmpty()) {
            // Default: weekday evenings
            while (!date.isAfter(end)) {
                if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                    slots.add(ZonedDateTime.of(date, LocalTime.of(16, 0), zoneId))
                }
                date = date.plusDays(1)
            }
            return slots
        }

        while (!date.isAfter(end)) {
            val key = date.dayOfWeek.name.take(3).lowercase()
            val times = availability[key] ?: emptyList()
            for (timeRange in times) {
                val startTime = try {
                    LocalTime.parse(timeRange.split("-")[0].trim())
                } catch (e: Exception) { LocalTime.of(16, 0) }
                slots.add(ZonedDateTime.of(date, startTime, zoneId))
            }
            date = date.plusDays(1)
        }
        return slots.sorted()
    }

    private fun ZonedDateTime.toSessionItem(
        topic: String, subject: String, duration: Int,
        rank: Int = 0, isRevision: Boolean = false, weaknessScore: Int = 0
    ) = SessionItem(
        date = toLocalDate().toString(),
        topic = topic,
        subject = subject,
        duration = duration,
        isoDateTime = format(isoFormatter),
        topicRank = rank,
        isRevision = isRevision,
        weaknessScore = weaknessScore
    )

    private fun parsePlan(raw: String): Plan? {
        if (raw.isBlank()) return null
        return try {
            var text = raw.trim()
            if (text.contains("```json")) text = text.substringAfter("```json").substringBefore("```").trim()
            else if (text.contains("```")) text = text.substringAfter("```").substringBefore("```").trim()

            val obj = gson.fromJson(text, com.google.gson.JsonObject::class.java)
            val weeks = obj.get("weeks")?.asInt ?: 4
            val arr = obj.getAsJsonArray("sessions") ?: return null

            val sessions = arr.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val s = el.asJsonObject
                SessionItem(
                    date = s.get("date")?.asString ?: return@mapNotNull null,
                    topic = s.get("topic")?.asString ?: return@mapNotNull null,
                    subject = s.get("subject")?.asString ?: "",
                    duration = s.get("duration")?.asInt ?: 30,
                    isoDateTime = s.get("isoDateTime")?.asString,
                    topicRank = s.get("topicRank")?.asInt ?: 0,
                    isRevision = s.get("isRevision")?.asBoolean ?: false,
                    weaknessScore = s.get("weaknessScore")?.asInt ?: 0
                )
            }
            if (sessions.isEmpty()) null else Plan(weeks = weeks, sessions = sessions)
        } catch (e: Exception) {
            Log.w("PlanAgent", "parsePlan failed: ${e.message}")
            null
        }
    }
}
