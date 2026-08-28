package dev.dawnswap

import android.content.Context
import android.content.SharedPreferences
import dev.dawnswap.logic.LaunchTarget
import dev.dawnswap.logic.SwapConfig
import dev.dawnswap.logic.SwapWindow
import java.time.LocalDate

/**
 * Everything the app remembers.
 *
 * `SharedPreferences` rather than DataStore on purpose: [TrampolineActivity] has to decide
 * and launch in the time it takes an icon press to feel instant, and a synchronous read is
 * exactly the right tool for that. An async read would put a coroutine between the tap and
 * the app opening.
 */
class SwapRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** The app being interrupted. */
    var real: LaunchTarget?
        get() = LaunchTarget.parse(prefs.getString(KEY_REAL, null))
        set(value) = writeTarget(KEY_REAL, value)

    /** What gets served instead on the first tap of the window. */
    var decoy: LaunchTarget?
        get() = LaunchTarget.parse(prefs.getString(KEY_DECOY, null))
        set(value) = writeTarget(KEY_DECOY, value)

    var window: SwapWindow
        get() = SwapWindow(
            prefs.getInt(KEY_START, DEFAULT_START_MINUTE),
            prefs.getInt(KEY_END, DEFAULT_END_MINUTE),
        )
        set(value) {
            prefs.edit()
                .putInt(KEY_START, value.startMinute)
                .putInt(KEY_END, value.endMinute)
                .apply()
            // The stored consumption refers to an occurrence that no longer exists.
            clearConsumption()
        }

    /** Whether to pin a second shortcut that hands the original app back during the window. */
    var swapMode: Boolean
        get() = prefs.getBoolean(KEY_SWAP_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SWAP_MODE, value).apply()
        }

    val lastConsumed: LocalDate?
        get() = prefs.getString(KEY_LAST_CONSUMED, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Written with `commit()`, not `apply()`. The trampoline records the consumption before
     * launching, so two taps in quick succession cannot both find the swap armed.
     */
    fun markConsumed(date: LocalDate) {
        prefs.edit().putString(KEY_LAST_CONSUMED, date.toString()).commit()
    }

    fun clearConsumption() {
        prefs.edit().remove(KEY_LAST_CONSUMED).apply()
    }

    /** The full configuration, or null while setup is still incomplete. */
    fun config(): SwapConfig? {
        val real = real ?: return null
        val decoy = decoy ?: return null
        return SwapConfig(enabled = enabled, window = window, real = real, decoy = decoy)
    }

    private fun writeTarget(key: String, value: LaunchTarget?) {
        prefs.edit().putString(key, value?.serialize()).apply()
        clearConsumption()
    }

    private companion object {
        const val FILE = "dawn_swap"
        const val KEY_ENABLED = "enabled"
        const val KEY_REAL = "real"
        const val KEY_DECOY = "decoy"
        const val KEY_START = "window_start"
        const val KEY_END = "window_end"
        const val KEY_SWAP_MODE = "swap_mode"
        const val KEY_LAST_CONSUMED = "last_consumed"

        const val DEFAULT_START_MINUTE = 6 * 60
        const val DEFAULT_END_MINUTE = 10 * 60
    }
}
