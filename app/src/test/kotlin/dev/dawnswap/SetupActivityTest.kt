package dev.dawnswap

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.dawnswap.logic.LaunchTarget
import dev.dawnswap.logic.SwapWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The setup screen's status line is the only feedback a user gets about whether the swap
 * will actually fire, so it has to tell the truth about every state - especially the one
 * where the app is switched on and still can never do anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupActivityTest {

    private lateinit var context: Context
    private lateinit var repository: SwapRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SwapRepository(context)
        repository.real = LaunchTarget.App("com.instagram.android")
        repository.decoy = LaunchTarget.web("https://news.local")
        repository.enabled = true
    }

    private fun shownStatus(): String {
        val activity = Robolectric.buildActivity(SetupActivity::class.java).create().resume().get()
        return activity.findViewById<TextView>(R.id.statusText).text.toString()
    }

    @Test
    fun `a window that starts and ends at the same time is called out, not left silent`() {
        repository.window = SwapWindow.of(6, 0, 6, 0)
        repository.enabled = true

        assertEquals(context.getString(R.string.status_window_empty), shownStatus())
    }

    @Test
    fun `the empty-window warning wins over the merely-off message`() {
        // Switched off AND unfireable: naming the unfireable window is the more useful of
        // the two, because turning it on would still do nothing.
        repository.window = SwapWindow.of(9, 30, 9, 30)
        repository.enabled = false

        assertEquals(context.getString(R.string.status_window_empty), shownStatus())
    }

    @Test
    fun `an ordinary window does not trigger the warning`() {
        repository.window = SwapWindow.of(6, 0, 10, 0)

        assertNotEquals(context.getString(R.string.status_window_empty), shownStatus())
    }

    @Test
    fun `a window crossing midnight does not trigger the warning`() {
        repository.window = SwapWindow.of(23, 0, 2, 0)

        assertNotEquals(context.getString(R.string.status_window_empty), shownStatus())
    }

    @Test
    fun `an incomplete setup asks for the missing apps rather than warning about the window`() {
        context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(context.getString(R.string.finish_setup_first), shownStatus())
    }

    @Test
    fun `a switched-off swap with a usable window says so`() {
        repository.window = SwapWindow.of(6, 0, 10, 0)
        repository.enabled = false

        assertEquals(context.getString(R.string.status_off), shownStatus())
    }
}
