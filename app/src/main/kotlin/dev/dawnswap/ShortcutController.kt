package dev.dawnswap

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.dawnswap.logic.Slot
import dev.dawnswap.logic.SwapDecider
import java.time.LocalDateTime

/**
 * The pinned shortcuts that sit on the home screen.
 *
 * A shortcut always wears the icon and name of whatever it will open, which is the same
 * question [SwapDecider.decide] already answers - so there is no second rule set here to
 * drift out of step with the launch behaviour.
 */
object ShortcutController {

    fun isPinningSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to place [slot] on the home screen. */
    fun requestPin(context: Context, slot: Slot): Boolean {
        val shortcut = build(context, slot) ?: return false
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /**
     * Repaints shortcuts already on the home screen.
     *
     * Best-effort by design: Pixel Launcher and One UI honour the update, while some
     * third-party launchers snapshot the icon at pin time and never refresh it. Nothing
     * about which app opens depends on this succeeding.
     */
    fun refresh(context: Context) {
        // Every slot, not only the ones we would pin today. A second shortcut pinned while
        // swap mode was on stays on the home screen after it is switched off, and a shortcut
        // we stop repainting freezes wearing the icon of an app it no longer opens.
        val shortcuts = Slot.entries.mapNotNull { build(context, it) }
        if (shortcuts.isEmpty()) return

        runCatching { ShortcutManagerCompat.updateShortcuts(context, shortcuts) }
    }

    /** The slots worth putting on the home screen — the second one only in swap mode. */
    fun slotsToPin(context: Context): List<Slot> =
        if (SwapRepository(context).swapMode) Slot.entries else listOf(Slot.PRIMARY)

    private fun build(context: Context, slot: Slot): ShortcutInfoCompat? {
        val repository = SwapRepository(context)
        val config = repository.config() ?: return null
        val decision = SwapDecider.decide(config, repository.lastConsumed, LocalDateTime.now(), slot)
        val label = Launcher.labelFor(context, decision.target)

        return ShortcutInfoCompat.Builder(context, slot.shortcutId())
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Launcher.iconFor(context, decision.target))
            .setIntent(TrampolineActivity.intentFor(context, slot))
            .build()
    }

    /** Stable per slot, so re-pinning updates the existing shortcut instead of duplicating it. */
    private fun Slot.shortcutId() = "dawn_swap_${name.lowercase()}"
}
