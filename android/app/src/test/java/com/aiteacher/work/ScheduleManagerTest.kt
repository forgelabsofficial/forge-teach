package com.aiteacher.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleManagerTest {
    @Test
    fun computeScheduledInstant_parsesDateAtDefaultHour() {
        val date = "2026-07-20"
        val instant: Instant = ScheduleManager.computeScheduledInstant(date, hourOfDay = 9, minute = 0)
        val z = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
        assertEquals(2026, z.year)
        assertEquals(7, z.monthValue)
        assertEquals(20, z.dayOfMonth)
        assertEquals(9, z.hour)
        assertEquals(0, z.minute)
    }
}
