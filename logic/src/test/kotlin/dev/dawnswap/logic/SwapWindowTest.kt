package dev.dawnswap.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalDateTime

class SwapWindowTest {

    private val morning = SwapWindow.of(startHour = 6, startMinute = 0, endHour = 10, endMinute = 0)
    private val overnight = SwapWindow.of(startHour = 23, startMinute = 0, endHour = 2, endMinute = 0)

    @ParameterizedTest(name = "06:00-10:00 contains {0}:{1} -> {2}")
    @CsvSource(
        "5, 59, false",
        "6, 0, true",
        "6, 1, true",
        "7, 30, true",
        "9, 59, true",
        "10, 0, false",
        "10, 1, false",
        "0, 0, false",
        "23, 30, false",
    )
    fun `a normal window includes its start and excludes its end`(
        hour: Int,
        minute: Int,
        expected: Boolean,
    ) {
        assertEquals(expected, morning.contains(hour * 60 + minute))
    }

    @ParameterizedTest(name = "23:00-02:00 contains {0}:{1} -> {2}")
    @CsvSource(
        "22, 59, false",
        "23, 0, true",
        "23, 59, true",
        "0, 0, true",
        "1, 59, true",
        "2, 0, false",
        "2, 1, false",
        "12, 0, false",
    )
    fun `a window crossing midnight wraps around instead of emptying out`(
        hour: Int,
        minute: Int,
        expected: Boolean,
    ) {
        assertEquals(expected, overnight.contains(hour * 60 + minute))
    }

    @Test
    fun `only a window whose end precedes its start counts as crossing midnight`() {
        assertFalse(morning.crossesMidnight)
        assertTrue(overnight.crossesMidnight)
    }

    @ParameterizedTest(name = "zero-length window never contains {0}:00")
    @CsvSource("0", "6", "12", "23")
    fun `a zero-length window never contains anything`(hour: Int) {
        val zeroLength = SwapWindow.of(6, 0, 6, 0)
        assertFalse(zeroLength.contains(hour * 60))
    }

    @Test
    fun `a zero-length window is not treated as crossing midnight`() {
        assertFalse(SwapWindow.of(6, 0, 6, 0).crossesMidnight)
    }

    @Test
    fun `a window covering all but one minute still excludes that minute`() {
        val almostAllDay = SwapWindow(startMinute = 1, endMinute = 0)
        assertTrue(almostAllDay.crossesMidnight)
        assertTrue(almostAllDay.contains(1))
        assertTrue(almostAllDay.contains(MINUTES_PER_DAY - 1))
        assertFalse(almostAllDay.contains(0))
    }

    @Test
    fun `minutes outside a single day are rejected`() {
        assertThrows<IllegalArgumentException> { SwapWindow(startMinute = -1, endMinute = 60) }
        assertThrows<IllegalArgumentException> { SwapWindow(startMinute = 0, endMinute = MINUTES_PER_DAY) }
        assertThrows<IllegalArgumentException> { SwapWindow(startMinute = MINUTES_PER_DAY, endMinute = 0) }
    }

    @Test
    fun `occurrence date is the calendar date for a normal window`() {
        val at = LocalDateTime.of(2026, 3, 14, 7, 30)
        assertEquals(LocalDate.of(2026, 3, 14), morning.occurrenceDate(at))
    }

    @Test
    fun `occurrence date is null outside the window`() {
        assertNull(morning.occurrenceDate(LocalDateTime.of(2026, 3, 14, 12, 0)))
        assertNull(overnight.occurrenceDate(LocalDateTime.of(2026, 3, 14, 12, 0)))
    }

    @Test
    fun `before midnight an overnight window reports today`() {
        val at = LocalDateTime.of(2026, 3, 14, 23, 30)
        assertEquals(LocalDate.of(2026, 3, 14), overnight.occurrenceDate(at))
    }

    @Test
    fun `after midnight an overnight window still reports the day it started`() {
        val at = LocalDateTime.of(2026, 3, 15, 0, 30)
        assertEquals(LocalDate.of(2026, 3, 14), overnight.occurrenceDate(at))
    }

    @Test
    fun `both halves of one overnight night share a single occurrence date`() {
        val beforeMidnight = overnight.occurrenceDate(LocalDateTime.of(2026, 3, 14, 23, 45))
        val afterMidnight = overnight.occurrenceDate(LocalDateTime.of(2026, 3, 15, 1, 15))
        assertEquals(beforeMidnight, afterMidnight)
    }

    @Test
    fun `consecutive nights get different occurrence dates`() {
        val firstNight = overnight.occurrenceDate(LocalDateTime.of(2026, 3, 14, 23, 45))
        val secondNight = overnight.occurrenceDate(LocalDateTime.of(2026, 3, 15, 23, 45))
        assertEquals(LocalDate.of(2026, 3, 14), firstNight)
        assertEquals(LocalDate.of(2026, 3, 15), secondNight)
    }

    @Test
    fun `the boundary minute after midnight falls outside the window`() {
        assertNull(overnight.occurrenceDate(LocalDateTime.of(2026, 3, 15, 2, 0)))
    }

    @Test
    fun `of builds the same window as raw minutes`() {
        assertEquals(SwapWindow(6 * 60 + 30, 10 * 60 + 15), SwapWindow.of(6, 30, 10, 15))
    }
}
