package com.example.wpqkf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GoogleCalendarHelperTest {

    @Test
    fun testParseKoreanDate_validFormat() {
        val dateStr = "2026년 8월 27일"
        val cal = GoogleCalendarHelper.parseKoreanDate(dateStr)

        assertNotNull(cal)
        assertEquals(2026, cal?.get(Calendar.YEAR))
        assertEquals(7, cal?.get(Calendar.MONTH)) // 0-indexed: August is 7
        assertEquals(27, cal?.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testParseKoreanDate_extraSpaces() {
        val dateStr = "2026년   5월  12일"
        val cal = GoogleCalendarHelper.parseKoreanDate(dateStr)

        assertNotNull(cal)
        assertEquals(2026, cal?.get(Calendar.YEAR))
        assertEquals(4, cal?.get(Calendar.MONTH)) // May is 4
        assertEquals(12, cal?.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testParseKoreanDate_invalidFormat() {
        val dateStr = "2026-08-27"
        val cal = GoogleCalendarHelper.parseKoreanDate(dateStr)

        assertNull(cal)
    }

    @Test
    fun testBuildEventDateTime_reflectsLocalTimeZone() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.AUGUST, 27, 14, 30, 0)

        val (dateTime, timeZoneId) = GoogleCalendarHelper.buildEventDateTime(cal)

        assertNotNull(dateTime)
        assertEquals(TimeZone.getDefault().id, timeZoneId)
        assertEquals(cal.timeInMillis, dateTime.value)
    }

    @Test
    fun testMidnightSpanningDateTimeCalculation() {
        val startCal = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 27, 23, 0, 0)
        }
        val endCal = (startCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }

        if (endCal.before(startCal)) {
            endCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        assertEquals(28, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, endCal.get(Calendar.HOUR_OF_DAY))
    }
}
