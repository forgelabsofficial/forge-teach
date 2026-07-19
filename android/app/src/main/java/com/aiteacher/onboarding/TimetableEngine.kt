package com.aiteacher.onboarding

import java.time.*
import java.time.format.DateTimeFormatter

/**
 * TimetableEngine
 * - Generates a timezone-aware schedule (Plan) from an `Assessment`.
 * - Adaptive allocation: subjects with low capability test scores get more slots.
 * - Level 1 (score 0-20%) → 3× slots of a level 5 (80-100%) subject.
 * - Algorithm: compute per-subject weights from subjectLevels, build a weighted
 *   allocation pool, assign sessions to the earliest availability slots.
 */
object TimetableEngine {

    /**
     * Map capability level (1-5) to a session multiplier.
     * Level 1 (weakest) → 3.0×, Level 5 (strongest) → 1.0×
     */
    private fun weightForLevel(level: Int): Float = when (level) {
        1 -> 3.0f
        2 -> 2.3f
        3 -> 1.7f
        4 -> 1.3f
        else -> 1.0f
    }

    /**
     * Build per-subject session weights from the assessment's subjectLevels.
     * Topics without a level get weight 1.0 (neutral).
     */
    private fun computeSubjectWeights(assessment: Assessment): Map<String, Float> {
        val levels = assessment.subjectLevels
        val weights = mutableMapOf<String, Float>()

        for (topic in assessment.topics) {
            val level = levels[topic.id] ?: 3 // default to mid level
            weights[topic.id] = weightForLevel(level)
        }
        return weights
    }

    fun generateSchedule(
        assessment: Assessment,
        weeks: Int = 4,
        startDate: LocalDate = LocalDate.now(),
        zone: ZoneId? = null
    ): Plan {
        val zoneId = zone ?: ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val sessionLength = assessment.prefs.sessionLength

        val topics = if (assessment.topics.isNotEmpty()) assessment.topics 
            else listOf(Topic("general", "General", true))
        val weights = computeSubjectWeights(assessment)

        // Build a weighted topic pool: weaker subjects appear more times
        val weightedPool = mutableListOf<String>() // list of topicId
        for (topic in topics) {
            val w = weights[topic.id] ?: 1.0f
            val copies = (w * 10).toInt().coerceIn(1, 30) // scale for integer pool
            repeat(copies) { weightedPool.add(topic.id) }
        }
        // Shuffle so we don't get blocks of the same subject
        weightedPool.shuffle()

        // Map availability keys like "mon" -> DayOfWeek
        val availByDow: Map<DayOfWeek, List<String>> = assessment.availability.mapNotNull { (k, v) ->
            val dow = dayKeyToDayOfWeek(k)
            if (dow != null) dow to v else null
        }.toMap()

        // Collect candidate slots within the date range
        val slots = mutableListOf<ZonedDateTime>()
        var date = startDate
        val endDate = startDate.plusWeeks(weeks.toLong()).minusDays(1)
        while (!date.isAfter(endDate)) {
            val dow = date.dayOfWeek
            val times = availByDow[dow] ?: emptyList()
            for (t in times) {
                val ranges = t.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
                for (r in ranges) {
                    val startLocalTime = try {
                        LocalTime.parse(r.split("-")[0].trim())
                    } catch (e: Exception) {
                        LocalTime.of(9, 0)
                    }
                    val zdt = ZonedDateTime.of(date, startLocalTime, zoneId)
                    if (zdt.toInstant().isAfter(Instant.now().minusSeconds(60))) {
                        slots.add(zdt)
                    }
                }
            }
            date = date.plusDays(1)
        }

        slots.sort()

        val sessions = mutableListOf<SessionItem>()
        val maxSessions = weeks * 7 // at most one session per day per week

        if (slots.isEmpty()) {
            // Fallback: generate daily sessions using weighted pool
            var d = startDate
            var idx = 0
            while (sessions.size < maxSessions && idx < weightedPool.size) {
                val topicId = weightedPool[idx % weightedPool.size]
                val topic = topics.find { it.id == topicId } ?: topics[idx % topics.size]
                val zdt = ZonedDateTime.of(d, LocalTime.of(9, 0), zoneId)
                sessions.add(SessionItem(
                    date = d.toString(),
                    topic = topic.name,
                    subject = topicId,
                    duration = sessionLength,
                    isoDateTime = zdt.format(formatter),
                    weaknessScore = computeWeakness(assessment, topicId)
                ))
                idx++
                d = d.plusDays(1)
            }
            return Plan(weeks = weeks, sessions = sessions)
        }

        // Pass 1: Schedule at most one session per day using weighted pool
        val scheduledDays = mutableSetOf<LocalDate>()
        val remainingSlots = mutableListOf<ZonedDateTime>()
        var poolIdx = 0

        for (slot in slots) {
            val slotDate = slot.toLocalDate()
            if (!scheduledDays.contains(slotDate) && sessions.size < maxSessions && poolIdx < weightedPool.size) {
                val topicId = weightedPool[poolIdx % weightedPool.size]
                val topic = topics.find { it.id == topicId } ?: topics[poolIdx % topics.size]
                sessions.add(SessionItem(
                    date = slotDate.toString(),
                    topic = topic.name,
                    subject = topicId,
                    duration = sessionLength,
                    isoDateTime = slot.format(formatter),
                    weaknessScore = computeWeakness(assessment, topicId)
                ))
                scheduledDays.add(slotDate)
                poolIdx++
            } else {
                remainingSlots.add(slot)
            }
        }

        // Pass 2: Fill remaining slots if still under max
        for (slot in remainingSlots) {
            if (sessions.size >= maxSessions || poolIdx >= weightedPool.size) break
            val slotDate = slot.toLocalDate()
            val topicId = weightedPool[poolIdx % weightedPool.size]
            val topic = topics.find { it.id == topicId } ?: topics[poolIdx % topics.size]
            sessions.add(SessionItem(
                date = slotDate.toString(),
                topic = topic.name,
                subject = topicId,
                duration = sessionLength,
                isoDateTime = slot.format(formatter),
                weaknessScore = computeWeakness(assessment, topicId)
            ))
            poolIdx++
        }

        // Sort final sessions chronologically
        sessions.sortBy { it.isoDateTime ?: it.date }

        return Plan(weeks = weeks, sessions = sessions)
    }

    /**
     * Derive a weakness score (0-100, higher = weaker) from the assessment's
     * per-subject level. Level 1 → 80 weakness, Level 5 → 10 weakness.
     */
    private fun computeWeakness(assessment: Assessment, subjectId: String): Int {
        val level = assessment.subjectLevels[subjectId] ?: 3
        return when (level) {
            1 -> 80
            2 -> 65
            3 -> 45
            4 -> 25
            else -> 10
        }
    }

    private fun dayKeyToDayOfWeek(key: String): DayOfWeek? {
        return when (key.lowercase().trim().take(3)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            "sun" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}
