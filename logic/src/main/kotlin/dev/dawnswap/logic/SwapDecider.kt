package dev.dawnswap.logic

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Which home-screen position was tapped.
 *
 * [PRIMARY] is the spot your thumb already goes to — where the app you want to interrupt
 * normally lives. [SECONDARY] only exists in swap mode: the spot the replacement app
 * normally lives, which during the window hands back the original app so it stays one tap away.
 */
enum class Slot { PRIMARY, SECONDARY }

/**
 * @param real the app being interrupted — what [Slot.PRIMARY] opens the rest of the day.
 * @param decoy the app to serve instead on the first tap of the window.
 */
data class SwapConfig(
    val enabled: Boolean,
    val window: SwapWindow,
    val real: LaunchTarget,
    val decoy: LaunchTarget,
)

/**
 * @param target what to open — and, equally, whose icon the slot should be wearing.
 * @param armed whether the swap is currently live.
 * @param consumes non-null exactly when this tap uses up the swap, carrying the date to
 *   record. Bundling the date into the decision means a caller cannot record the wrong day,
 *   nor silently skip the write by recomputing "now" and landing outside the window.
 */
data class Decision(
    val target: LaunchTarget,
    val armed: Boolean,
    val consumes: LocalDate?,
)

/**
 * The whole rule set, as one pure function.
 *
 * A slot always displays the icon of whatever it will open, so "which icon do I show?" and
 * "which app do I launch?" are the same question — [decide] answers both. That invariant is
 * why there is no separate icon-state logic to drift out of sync.
 */
object SwapDecider {

    fun decide(
        config: SwapConfig,
        lastConsumed: LocalDate?,
        now: LocalDateTime,
        slot: Slot,
    ): Decision {
        val occurrence = if (config.enabled) config.window.occurrenceDate(now) else null
        val armed = occurrence != null && occurrence != lastConsumed

        // Armed flips both slots; unarmed leaves both showing their usual app.
        val showDecoy = armed == (slot == Slot.PRIMARY)

        return Decision(
            target = if (showDecoy) config.decoy else config.real,
            armed = armed,
            consumes = occurrence.takeIf { armed && slot == Slot.PRIMARY },
        )
    }
}
