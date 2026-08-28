package dev.dawnswap.logic

import java.time.LocalDate
import java.time.LocalDateTime

/** Minutes elapsed since local midnight. */
internal fun LocalDateTime.minuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

const val MINUTES_PER_HOUR = 60
const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * The daily stretch of time during which the swap is allowed to fire.
 *
 * [startMinute] is inclusive, [endMinute] is exclusive, both measured from local midnight.
 * An [endMinute] lower than [startMinute] means the window crosses midnight (23:00 -> 02:00).
 * Equal values are a zero-length window that never contains anything — the literal reading,
 * and it prevents a mis-set window from becoming an always-on swap.
 */
data class SwapWindow(val startMinute: Int, val endMinute: Int) {

    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
    }

    val crossesMidnight: Boolean get() = endMinute < startMinute

    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        crossesMidnight -> minuteOfDay >= startMinute || minuteOfDay < endMinute
        else -> minuteOfDay >= startMinute && minuteOfDay < endMinute
    }

    fun contains(at: LocalDateTime): Boolean = contains(at.minuteOfDay())

    /**
     * The date on which the window occurrence covering [at] *started*, or null when [at] is
     * outside the window.
     *
     * This is what makes "fire once per morning" correct for a window that crosses midnight.
     * Keying on the plain calendar date would let a 23:00-02:00 window fire at 23:30 **and**
     * again at 00:30, because midnight silently rolls the date over mid-window.
     */
    fun occurrenceDate(at: LocalDateTime): LocalDate? {
        if (!contains(at)) return null
        val inPostMidnightTail = crossesMidnight && at.minuteOfDay() < endMinute
        return if (inPostMidnightTail) at.toLocalDate().minusDays(1) else at.toLocalDate()
    }

    companion object {
        fun of(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) = SwapWindow(
            startHour * MINUTES_PER_HOUR + startMinute,
            endHour * MINUTES_PER_HOUR + endMinute,
        )

        /**
         * Builds a window from values that came from storage, falling back when they are out
         * of range.
         *
         * The constructor rightly rejects nonsense, but storage is not a trusted source: a
         * corrupted preference file would otherwise throw from inside a property getter and
         * crash the app on every single tap, with no way back except clearing app data.
         */
        fun sanitized(startMinute: Int, endMinute: Int, fallback: SwapWindow): SwapWindow {
            val valid = startMinute in 0 until MINUTES_PER_DAY &&
                endMinute in 0 until MINUTES_PER_DAY
            return if (valid) SwapWindow(startMinute, endMinute) else fallback
        }
    }
}
