package dev.dawnswap.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertTrue(decision.consume)
    }

    @Test
    fun `every tap after the first opens the original app`() {
        val consumedToday = LocalDate.of(2026, 3, 14)
        val decision = decide(lastConsumed = consumedToday, at = at(2026, 3, 14, 7, 5))

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertFalse(decision.consume)
    }

    @Test
    fun `the swap re-arms the following morning`() {
        val consumedYesterday = LocalDate.of(2026, 3, 13)
        val decision = decide(lastConsumed = consumedYesterday, at = at(2026, 3, 14, 7, 0))

        assertEquals(localNews, decision.target)
        assertTrue(decision.armed)
    }

    // --- Outside the window ------------------------------------------------------------

    @Test
    fun `a tap before the window opens the original app`() {
        val decision = decide(at = at(2026, 3, 14, 5, 59))

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertFalse(decision.consume)
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
        assertFalse(decision.consume)
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

    private val overnightConfig = morningConfig.copy(window = SwapWindow.of(23, 0, 2, 0))

    @Test
    fun `an overnight swap fires before midnight`() {
        val decision = decide(config = overnightConfig, at = at(2026, 3, 14, 23, 30))

        assertEquals(localNews, decision.target)
        assertTrue(decision.consume)
    }

    @Test
    fun `an overnight swap does not fire again after midnight the same night`() {
        val consumedAt2330 = overnightConfig.window.occurrenceDate(at(2026, 3, 14, 23, 30))
        val decision = decide(
            config = overnightConfig,
            lastConsumed = consumedAt2330,
            at = at(2026, 3, 15, 0, 30),
        )

        assertEquals(instagram, decision.target)
        assertFalse(decision.armed)
        assertFalse(decision.consume)
    }

    @Test
    fun `an overnight swap fires again the next night`() {
        val consumedLastNight = overnightConfig.window.occurrenceDate(at(2026, 3, 14, 23, 30))
        val decision = decide(
            config = overnightConfig,
            lastConsumed = consumedLastNight,
            at = at(2026, 3, 15, 23, 30),
        )

        assertEquals(localNews, decision.target)
        assertTrue(decision.armed)
    }

    @Test
    fun `an overnight swap first tapped after midnight fires only once`() {
        val firstTap = decide(config = overnightConfig, at = at(2026, 3, 15, 0, 15))
        assertTrue(firstTap.consume)

        val consumed = overnightConfig.window.occurrenceDate(at(2026, 3, 15, 0, 15))
        val secondTap = decide(
            config = overnightConfig,
            lastConsumed = consumed,
            at = at(2026, 3, 15, 1, 45),
        )
        assertFalse(secondTap.armed)
        assertEquals(instagram, secondTap.target)
    }

    // --- Swap mode: the second slot ----------------------------------------------------

    @Test
    fun `while armed the second slot hands back the original app`() {
        val decision = decide(at = at(2026, 3, 14, 7, 0), slot = Slot.SECONDARY)

        assertEquals(instagram, decision.target)
        assertTrue(decision.armed)
    }

    @Test
    fun `the second slot never uses up the swap`() {
        val decision = decide(at = at(2026, 3, 14, 7, 0), slot = Slot.SECONDARY)

        assertFalse(decision.consume)
    }

    @Test
    fun `outside the window the second slot opens the replacement app as usual`() {
        val decision = decide(at = at(2026, 3, 14, 15, 0), slot = Slot.SECONDARY)

        assertEquals(localNews, decision.target)
        assertFalse(decision.armed)
    }

    @Test
    fun `the two slots always hold different apps`() {
        for (hour in 0..23) {
            val moment = at(2026, 3, 14, hour, 0)
            val primary = decide(at = moment, slot = Slot.PRIMARY).target
            val secondary = decide(at = moment, slot = Slot.SECONDARY).target

            assertTrue(primary != secondary, "slots collided at ${hour}:00")
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

    @Test
    fun `only the primary slot can ever consume the swap`() {
        val consumingDecisions = buildList {
            for (hour in 0..23) {
                for (slot in Slot.entries) {
                    val decision = decide(at = at(2026, 3, 14, hour, 0), slot = slot)
                    if (decision.consume) add(slot)
                }
            }
        }

        assertTrue(consumingDecisions.isNotEmpty(), "expected at least one consuming tap")
        assertTrue(consumingDecisions.all { it == Slot.PRIMARY })
    }

    @Test
    fun `a consumption date in the future does not wedge the swap shut`() {
        val clockWentBackwards = LocalDate.of(2026, 12, 25)
        val decision = decide(lastConsumed = clockWentBackwards, at = at(2026, 3, 14, 7, 0))

        assertTrue(decision.armed)
        assertEquals(localNews, decision.target)
    }

    @Test
    fun `an app can be swapped for another app not just a url`() {
        val config = morningConfig.copy(decoy = LaunchTarget.App("com.duolingo"))
        val decision = decide(config = config, at = at(2026, 3, 14, 7, 0))

        assertEquals(LaunchTarget.App("com.duolingo"), decision.target)
    }
}
