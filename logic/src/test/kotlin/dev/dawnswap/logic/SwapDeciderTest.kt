package dev.dawnswap.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SwapDeciderTest {

    private val instagram = LaunchTarget.App("com.instagram.android")
    private val localNews = LaunchTarget.Web("https://news.local/today")

    private val morningConfig = SwapConfig(
        enabled = true,
        window = SwapWindow.of(6, 0, 10, 0),
        real = instagram,
        decoy = localNews,
    )

    private val overnightConfig = morningConfig.copy(window = SwapWindow.of(23, 0, 2, 0))

    private fun decide(
        config: SwapConfig = morningConfig,
        lastConsumed: LocalDate? = null,
        at: LocalDateTime,
        slot: Slot = Slot.PRIMARY,
    ) = SwapDecider.decide(config, lastConsumed, at, slot)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute)

    // --- The headline behaviour -------------------------------------------------------

    @Test
    fun `the first tap inside the window opens the replacement app`() {
        val decision = decide(at = at(2026, 3, 14, 7, 0))

        assertEquals(localNews, decision.target)
        assertTrue(decision.armed)
        assertEquals(LocalDate.of(2026, 3, 14), decision.consumes)
    }

    @Test
    fun `every tap after the first opens the original app`() {
        val decision = decide(
            lastConsumed = LocalDate.of(2026, 3, 14),
            at = at(2026, 3, 14, 7, 5),
        )

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertNull(decision.consumes)
    }

    @Test
    fun `the swap re-arms the following morning`() {
        val decision = decide(
            lastConsumed = LocalDate.of(2026, 3, 13),
            at = at(2026, 3, 14, 7, 0),
        )

        assertEquals(localNews, decision.target)
        assertTrue(decision.armed)
        assertEquals(LocalDate.of(2026, 3, 14), decision.consumes)
    }

    // --- Outside the window ------------------------------------------------------------

    @Test
    fun `a tap before the window opens the original app`() {
        val decision = decide(at = at(2026, 3, 14, 5, 59))

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertNull(decision.consumes)
    }

    @Test
    fun `a tap after the window opens the original app`() {
        val decision = decide(at = at(2026, 3, 14, 10, 0))

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
    }

    @Test
    fun `a disabled swap never fires even inside the window`() {
        val decision = decide(
            config = morningConfig.copy(enabled = false),
            at = at(2026, 3, 14, 7, 0),
        )

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertNull(decision.consumes)
    }

    @Test
    fun `a disabled swap leaves the second slot on the replacement app`() {
        val decision = decide(
            config = morningConfig.copy(enabled = false),
            at = at(2026, 3, 14, 7, 0),
            slot = Slot.SECONDARY,
        )

        assertEquals(localNews, decision.target)
        assertFalse(decision.armed)
    }

    @Test
    fun `a zero-length window never fires`() {
        val decision = decide(
            config = morningConfig.copy(window = SwapWindow.of(6, 0, 6, 0)),
            at = at(2026, 3, 14, 6, 0),
        )

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
    }

    // --- The midnight-crossing regression ----------------------------------------------
    // A 23:00-02:00 window must fire once per NIGHT. Keying "once a day" on the calendar
    // date would let it fire again the moment the date rolls over at midnight.

    @Test
    fun `an overnight swap fires before midnight`() {
        val decision = decide(config = overnightConfig, at = at(2026, 3, 14, 23, 30))

        assertEquals(localNews, decision.target)
        assertEquals(LocalDate.of(2026, 3, 14), decision.consumes)
    }

    @Test
    fun `an overnight swap does not fire again after midnight the same night`() {
        val consumedAt2330 = decide(config = overnightConfig, at = at(2026, 3, 14, 23, 30)).consumes
        val decision = decide(
            config = overnightConfig,
            lastConsumed = consumedAt2330,
            at = at(2026, 3, 15, 0, 30),
        )

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertNull(decision.consumes)
    }

    @Test
    fun `an overnight swap fires again the next night`() {
        val consumedLastNight = decide(config = overnightConfig, at = at(2026, 3, 14, 23, 30)).consumes
        val decision = decide(
            config = overnightConfig,
            lastConsumed = consumedLastNight,
            at = at(2026, 3, 15, 23, 30),
        )

        assertEquals(localNews, decision.target)
        assertEquals(LocalDate.of(2026, 3, 15), decision.consumes)
    }

    @Test
    fun `an overnight swap first tapped after midnight fires only once`() {
        val firstTap = decide(config = overnightConfig, at = at(2026, 3, 15, 0, 15))
        assertEquals(LocalDate.of(2026, 3, 14), firstTap.consumes)

        val secondTap = decide(
            config = overnightConfig,
            lastConsumed = firstTap.consumes,
            at = at(2026, 3, 15, 1, 45),
        )
        assertFalse(secondTap.armed)
        assertEquals(instagram, secondTap.target)
    }

    @Test
    fun `an overnight swap consumed after midnight still arms that same evening`() {
        // Consumed at 00:15 on the 15th, which belongs to the 14th's occurrence. The window
        // that opens at 23:00 on the 15th is a new occurrence and must arm.
        val consumed = LocalDate.of(2026, 3, 14)
        val decision = decide(
            config = overnightConfig,
            lastConsumed = consumed,
            at = at(2026, 3, 15, 23, 5),
        )

        assertTrue(decision.armed)
        assertEquals(LocalDate.of(2026, 3, 15), decision.consumes)
    }

    // --- Swap mode: the second slot ----------------------------------------------------

    @Test
    fun `while armed the second slot hands back the original app`() {
        val decision = decide(at = at(2026, 3, 14, 7, 0), slot = Slot.SECONDARY)

        assertEquals(instagram, decision.target)
        assertTrue(decision.armed)
    }

    @Test
    fun `outside the window the second slot opens the replacement app as usual`() {
        val decision = decide(at = at(2026, 3, 14, 15, 0), slot = Slot.SECONDARY)

        assertEquals(localNews, decision.target)
        assertFalse(decision.armed)
    }

    @Test
    fun `the two slots always hold different apps`() {
        for (minute in 0 until MINUTES_PER_DAY) {
            val moment = at(2026, 3, 14, 0, 0).plusMinutes(minute.toLong())
            val primary = decide(at = moment, slot = Slot.PRIMARY).target
            val secondary = decide(at = moment, slot = Slot.SECONDARY).target

            assertTrue(primary != secondary, "slots collided at minute $minute")
        }
    }

    // --- The armed flip ----------------------------------------------------------------
    // decide() answers both "what do I launch?" and "which icon do I wear?", so this table
    // is the whole of the app's visible behaviour. Locking it down here is what lets the
    // Android side derive icon state from the same call with no second rule set to drift.

    @Test
    fun `the armed flip follows the documented truth table`() {
        val armedMoment = at(2026, 3, 14, 7, 0)
        val alreadyConsumed = LocalDate.of(2026, 3, 14)

        data class Case(val armed: Boolean, val slot: Slot, val expected: LaunchTarget)

        val table = listOf(
            Case(armed = true, slot = Slot.PRIMARY, expected = localNews),
            Case(armed = true, slot = Slot.SECONDARY, expected = instagram),
            Case(armed = false, slot = Slot.PRIMARY, expected = instagram),
            Case(armed = false, slot = Slot.SECONDARY, expected = localNews),
        )

        for (case in table) {
            val decision = decide(
                lastConsumed = if (case.armed) null else alreadyConsumed,
                at = armedMoment,
                slot = case.slot,
            )

            assertEquals(case.armed, decision.armed, "armed mismatch for $case")
            assertEquals(case.expected, decision.target, "target mismatch for $case")
        }
    }

    // --- Exhaustive sweeps -------------------------------------------------------------

    @Test
    fun `across every minute of the day the primary slot tracks the window exactly`() {
        val window = morningConfig.window
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val decision = decide(at = midnight.plusMinutes(minute.toLong()))
            val expectedArmed = window.contains(minute)

            assertEquals(expectedArmed, decision.armed, "armed at minute $minute")
            assertEquals(
                if (expectedArmed) localNews else instagram,
                decision.target,
                "target at minute $minute",
            )
        }
    }

    @Test
    fun `across every minute of the day an overnight window tracks its own hours`() {
        val window = overnightConfig.window
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val decision = decide(config = overnightConfig, at = midnight.plusMinutes(minute.toLong()))

            assertEquals(window.contains(minute), decision.armed, "armed at minute $minute")
        }
    }

    @Test
    fun `a primary tap consumes exactly when it is armed and never otherwise`() {
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val decision = decide(at = midnight.plusMinutes(minute.toLong()))

            assertEquals(
                decision.armed,
                decision.consumes != null,
                "consume disagreed with armed at minute $minute",
            )
        }
    }

    @Test
    fun `the second slot never consumes the swap at any minute of the day`() {
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val decision = decide(at = midnight.plusMinutes(minute.toLong()), slot = Slot.SECONDARY)

            assertNull(decision.consumes, "second slot consumed at minute $minute")
        }
    }

    @Test
    fun `a consumed date always matches the occurrence the tap fell in`() {
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val moment = midnight.plusMinutes(minute.toLong())
            val decision = decide(config = overnightConfig, at = moment)

            assertEquals(
                overnightConfig.window.occurrenceDate(moment),
                decision.consumes,
                "wrong occurrence recorded at minute $minute",
            )
        }
    }

    @Test
    fun `replaying a consumption always closes the swap for that occurrence`() {
        // Whatever moment first fires, feeding its recorded date back must disarm every other
        // moment in the same occurrence. This is the property the whole design rests on.
        val midnight = at(2026, 3, 14, 0, 0)

        for (minute in 0 until MINUTES_PER_DAY) {
            val moment = midnight.plusMinutes(minute.toLong())
            val first = decide(config = overnightConfig, at = moment)
            if (first.consumes == null) continue

            val replay = decide(config = overnightConfig, lastConsumed = first.consumes, at = moment)

            assertFalse(replay.armed, "swap stayed armed after consuming at minute $minute")
            assertEquals(instagram, replay.target, "wrong target after consuming at minute $minute")
        }
    }

    // --- Robustness --------------------------------------------------------------------

    @Test
    fun `a consumption date in the future does not wedge the swap shut`() {
        val decision = decide(
            lastConsumed = LocalDate.of(2026, 12, 25),
            at = at(2026, 3, 14, 7, 0),
        )

        assertTrue(decision.armed)
        assertEquals(localNews, decision.target)
    }

    @Test
    fun `an app can be swapped for another app not just a url`() {
        val config = morningConfig.copy(decoy = LaunchTarget.App("com.duolingo"))
        val decision = decide(config = config, at = at(2026, 3, 14, 7, 0))

        assertEquals(LaunchTarget.App("com.duolingo"), decision.target)
    }

    @Test
    fun `an armed primary decision always carries a date to record`() {
        val decision = decide(at = at(2026, 3, 14, 6, 0))

        assertTrue(decision.armed)
        assertNotNull(decision.consumes)
    }
}
