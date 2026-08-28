package dev.dawnswap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.dawnswap.logic.LaunchTarget
import dev.dawnswap.logic.SwapWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwapRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SwapRepository

    private val defaultWindow = SwapWindow.of(6, 0, 10, 0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SwapRepository(context)
    }

    private fun rawPrefs() = context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE)

    // --- Defaults ----------------------------------------------------------------------

    @Test
    fun `a fresh install starts disabled with nothing configured`() {
        assertFalse(repository.enabled)
        assertNull(repository.real)
        assertNull(repository.decoy)
        assertFalse(repository.swapMode)
        assertNull(repository.lastConsumed)
    }

    @Test
    fun `a fresh install defaults to a morning window`() {
        assertEquals(defaultWindow, repository.window)
    }

    @Test
    fun `config stays null until both targets are chosen`() {
        assertNull(repository.config())

        repository.real = LaunchTarget.App("com.instagram.android")
        assertNull(repository.config())

        repository.decoy = LaunchTarget.web("https://news.local")
        assertNotNull(repository.config())
    }

    // --- Round trips -------------------------------------------------------------------

    @Test
    fun `an app target survives being written and read back`() {
        repository.real = LaunchTarget.App("com.instagram.android")

        assertEquals(LaunchTarget.App("com.instagram.android"), SwapRepository(context).real)
    }

    @Test
    fun `a web target survives being written and read back`() {
        repository.decoy = LaunchTarget.web("https://news.local/today")

        assertEquals(LaunchTarget.Web("https://news.local/today"), SwapRepository(context).decoy)
    }

    @Test
    fun `the window survives being written and read back`() {
        repository.window = SwapWindow.of(23, 30, 2, 15)

        assertEquals(SwapWindow.of(23, 30, 2, 15), SwapRepository(context).window)
    }

    @Test
    fun `a recorded consumption survives being written and read back`() {
        repository.markConsumed(LocalDate.of(2026, 3, 14))

        assertEquals(LocalDate.of(2026, 3, 14), SwapRepository(context).lastConsumed)
    }

    @Test
    fun `enabled and swap mode survive being written and read back`() {
        repository.enabled = true
        repository.swapMode = true

        val reopened = SwapRepository(context)
        assertTrue(reopened.enabled)
        assertTrue(reopened.swapMode)
    }

    // --- Consumption is cleared whenever the rules change --------------------------------
    // A recorded consumption refers to a window occurrence. Change the window or a target
    // and that occurrence no longer means what it did, so the record must go.

    @Test
    fun `changing the window clears a recorded consumption`() {
        repository.markConsumed(LocalDate.of(2026, 3, 14))

        repository.window = SwapWindow.of(7, 0, 11, 0)

        assertNull(repository.lastConsumed)
    }

    @Test
    fun `changing the interrupted app clears a recorded consumption`() {
        repository.markConsumed(LocalDate.of(2026, 3, 14))

        repository.real = LaunchTarget.App("com.other.app")

        assertNull(repository.lastConsumed)
    }

    @Test
    fun `changing the replacement clears a recorded consumption`() {
        repository.markConsumed(LocalDate.of(2026, 3, 14))

        repository.decoy = LaunchTarget.web("https://elsewhere.local")

        assertNull(repository.lastConsumed)
    }

    @Test
    fun `toggling enabled does not clear a recorded consumption`() {
        repository.markConsumed(LocalDate.of(2026, 3, 14))

        repository.enabled = true

        assertEquals(LocalDate.of(2026, 3, 14), repository.lastConsumed)
    }

    // --- Hostile stored values ----------------------------------------------------------
    // Regression: the window getter used to build SwapWindow directly from preferences.
    // SwapWindow rejects out-of-range minutes, so a corrupt value threw from inside a
    // property getter and crashed the app on every tap, recoverable only by clearing data.

    @Test
    fun `an out of range stored window falls back instead of throwing`() {
        rawPrefs().edit().putInt("window_start", 99999).putInt("window_end", -7).commit()

        assertEquals(defaultWindow, SwapRepository(context).window)
    }

    @Test
    fun `an out of range stored window still lets the app build a config`() {
        rawPrefs().edit().putInt("window_start", Int.MAX_VALUE).commit()
        repository.real = LaunchTarget.App("com.instagram.android")
        repository.decoy = LaunchTarget.web("https://news.local")

        val config = SwapRepository(context).config()

        assertNotNull(config)
        assertEquals(defaultWindow, config!!.window)
    }

    @Test
    fun `a corrupt stored date reads as no consumption rather than throwing`() {
        rawPrefs().edit().putString("last_consumed", "not-a-date").commit()

        assertNull(SwapRepository(context).lastConsumed)
    }

    @Test
    fun `a corrupt stored target reads as unset rather than throwing`() {
        rawPrefs().edit().putString("real", "web:file:///etc/passwd").commit()

        assertNull(SwapRepository(context).real)
        assertNull(SwapRepository(context).config())
    }

    @Test
    fun `an untagged stored target is not silently reinterpreted`() {
        rawPrefs().edit().putString("decoy", "com.instagram.android").commit()

        assertNull(SwapRepository(context).decoy)
    }
}
