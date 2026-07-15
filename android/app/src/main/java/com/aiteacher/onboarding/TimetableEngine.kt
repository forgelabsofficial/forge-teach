package com.aiteacher.onboarding

import java.time.*
import java.time.format.DateTimeFormatter

/**
 * TimetableEngine
 * - Generates a simple timezone-aware schedule (Plan) from an `Assessment`.
 * - Algorithm: flatten availability slots over the next `weeks`, round-robin assign selected topics to earliest slots,
 *   and produce ISO offset datetimes for each session.
 */
object TimetableEngine {

    fun generateSchedule(
        assessment: Assessment,
        weeks: Int = 4,
        startDate: LocalDate = LocalDate.now(),
        zone: ZoneId? = null
    ): Plan {
        val zoneId = zone ?: ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val sessionLength = assessment.prefs.sessionLength

        val topics = if (assessment.topics.isNotEmpty()) assessment.topics else listOf(Topic("general", "General", true))

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
                // Split by semicolon, comma or space in case multiple ranges are specified
                val ranges = t.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
                for (r in ranges) {
                    val startLocalTime = try {
                        LocalTime.parse(r.split("-")[0].trim())
                    } catch (e: Exception) {
                        LocalTime.of(9, 0)
                    }
                    val zdt = ZonedDateTime.of(date, startLocalTime, zoneId)
                    // Prefer future slots; allow very near past slots (ScheduleManager will adjust)
                    if (zdt.toInstant().isAfter(Instant.now().minusSeconds(60))) {
                        slots.add(zdt)
                    }
                }
            }
            date = date.plusDays(1)
        }

        slots.sort()

        // Assign topics into slots applying spacing rules:
        // Spacing Rule: Prioritize scheduling at most one session per day. Only schedule multiple sessions on the same
        // day if the total number of unique days available is insufficient to meet the target maxSessions.
        val sessions = mutableListOf<SessionItem>()
        val maxSessions = weeks * topics.size

        if (slots.isEmpty()) {
            // Fallback: generate simple daily sessions starting today
            var d = startDate
            var idx = 0
            while (sessions.size < maxSessions) {
                val zdt = ZonedDateTime.of(d, LocalTime.of(9, 0), zoneId)
                val topic = topics[idx % topics.size]
                sessions.add(SessionItem(date = d.toString(), topic = topic.name, duration = sessionLength, isoDateTime = zdt.format(formatter)))
                idx++
                d = d.plusDays(1)
            }
            return Plan(weeks = weeks, sessions = sessions)
        }

        // Pass 1: Schedule at most one session per day
        val scheduledDays = mutableSetOf<LocalDate>()
        val remainingSlots = mutableListOf<ZonedDateTime>()
        var topicIdx = 0

        for (slot in slots) {
            val slotDate = slot.toLocalDate()
            if (!scheduledDays.contains(slotDate) && sessions.size < maxSessions) {
                val topic = topics[topicIdx % topics.size]
                sessions.add(SessionItem(date = slotDate.toString(), topic = topic.name, duration = sessionLength, isoDateTime = slot.format(formatter)))
                scheduledDays.add(slotDate)
                topicIdx++
            } else {
                remainingSlots.add(slot)
            }
        }

        // Pass 2: If we still need more sessions and have remaining slots, fill them
        for (slot in remainingSlots) {
            if (sessions.size >= maxSessions) break
            val slotDate = slot.toLocalDate()
            val topic = topics[topicIdx % topics.size]
            sessions.add(SessionItem(date = slotDate.toString(), topic = topic.name, duration = sessionLength, isoDateTime = slot.format(formatter)))
            topicIdx++
        }

        // Sort final sessions chronologically
        sessions.sortBy { it.isoDateTime ?: it.date }

        return Plan(weeks = weeks, sessions = sessions)
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
