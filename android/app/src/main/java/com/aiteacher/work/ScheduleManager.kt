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
    // Compute the scheduled instant for a session date string. Assumes date-only strings like yyyy-MM-dd.
    fun computeScheduledInstant(sessionDate: String, hourOfDay: Int = 9, minute: Int = 0): Instant {
        // Try ISO_OFFSET_DATE_TIME / ISO_DATE_TIME first
        try {
            val odt = OffsetDateTime.parse(sessionDate)
            return odt.toInstant()
        } catch (e: Exception) {}
        try {
            val zdt = ZonedDateTime.parse(sessionDate)
            return zdt.toInstant()
        } catch (e: Exception) {}
        // Fallback: parse date-only and use provided hour/minute
        val localDate = try {
            LocalDate.parse(sessionDate, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            try {
                LocalDate.parse(sessionDate)
            } catch (e2: Exception) {
                LocalDate.now()
            }
        }
        val zone = ZoneId.systemDefault()
        val zoned = ZonedDateTime.of(localDate, LocalTime.of(hourOfDay, minute), zone)
        return zoned.toInstant()
    }

    fun schedulePlanNotifications(context: Context, plan: Plan) {
        val wm = WorkManager.getInstance(context)
        plan.sessions.forEachIndexed { idx, s ->
            val scheduledInstant = if (!s.isoDateTime.isNullOrBlank()) {
                try {
                    // prefer isoDateTime if provided
                    computeScheduledInstant(s.isoDateTime!!)
                } catch (e: Exception) {
                    computeScheduledInstant(s.date)
                }
            } else {
                computeScheduledInstant(s.date)
            }
            val now = Instant.now()
            var delayMillis = Duration.between(now, scheduledInstant).toMillis()
            if (delayMillis <= 0L) {
                // If scheduled time is past, schedule shortly (30s) so user still gets a reminder
                delayMillis = 30_000L
            }
            val input = NotificationWorker.buildInput("${s.topic}", "Study: ${s.topic} — ${s.duration} min")
            val work = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()
            // include isoDateTime in unique work name to avoid collisions
            val uniqueName = if (!s.isoDateTime.isNullOrBlank()) "session-${s.isoDateTime}-${idx}" else "session-${s.date}-${idx}"
            wm.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
