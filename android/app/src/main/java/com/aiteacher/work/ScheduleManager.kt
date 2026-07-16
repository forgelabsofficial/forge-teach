package com.aiteacher.work

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.aiteacher.onboarding.Plan
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ScheduleManager {
    // Compute the scheduled instant for a session date string.
    // Handles:
    // - ISO_OFFSET_DATE_TIME (e.g. 2025-01-15T10:30:00+01:00)
    // - ISO_ZONED_DATE_TIME (e.g. 2025-01-15T10:30:00+01:00[Europe/Paris])
    // - ISO_LOCAL_DATE_TIME (e.g. 2025-01-15T10:30:00) using the provided zoneId/systemDefault
    // - date-only strings like yyyy-MM-dd
    fun computeScheduledInstant(
        sessionDate: String,
        hourOfDay: Int = 9,
        minute: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Instant {
        // Try ISO_OFFSET_DATE_TIME
        try {
            val odt = OffsetDateTime.parse(sessionDate)
            return odt.toInstant()
        } catch (_: Exception) {}

        // Try ISO_ZONED_DATE_TIME
        try {
            val zdt = ZonedDateTime.parse(sessionDate)
            return zdt.toInstant()
        } catch (_: Exception) {}

        // Try ISO_LOCAL_DATE_TIME (no offset/zone included)
        try {
            val ldt = LocalDateTime.parse(sessionDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            return ldt.atZone(zoneId).toInstant()
        } catch (_: Exception) {}

        // Fallback: parse date-only and use provided hour/minute
        val localDate = try {
            LocalDate.parse(sessionDate, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            try {
                LocalDate.parse(sessionDate)
            } catch (_: Exception) {
                LocalDate.now()
            }
        }

        val zoned = ZonedDateTime.of(localDate, LocalTime.of(hourOfDay, minute), zoneId)
        return zoned.toInstant()
    }

    fun schedulePlanNotifications(
        context: Context,
        plan: Plan,
        planId: Long = 0,
        zoneId: ZoneId? = null
    ) {
        val wm = WorkManager.getInstance(context)
        val effectiveZone = zoneId ?: ZoneId.systemDefault()

        plan.sessions.forEachIndexed { idx, s ->
            val scheduledInstant = if (!s.isoDateTime.isNullOrBlank()) {
                computeScheduledInstant(
                    sessionDate = s.isoDateTime!!,
                    zoneId = effectiveZone
                )
            } else {
                computeScheduledInstant(
                    sessionDate = s.date,
                    zoneId = effectiveZone
                )
            }

            val now = Instant.now()
            var delayMillis = Duration.between(now, scheduledInstant).toMillis()
            if (delayMillis <= 0L) {
                delayMillis = 30_000L
            }

            // Unique notification ID so each session gets its own notification
            val notifyId = (planId.toInt() * 1000) + idx
            val input = NotificationWorker.buildInput(
                title = s.topic,
                text = "Study: ${s.topic} — ${s.duration} min",
                notifyId = notifyId
            )

            val work = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()

            val uniqueName = if (!s.isoDateTime.isNullOrBlank()) {
                "session-${s.isoDateTime}-${idx}"
            } else {
                "session-${s.date}-${idx}"
            }

            wm.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
