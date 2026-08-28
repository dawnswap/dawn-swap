package dev.dawnswap

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.dawnswap.logic.LaunchTarget
import dev.dawnswap.logic.MINUTES_PER_DAY
import dev.dawnswap.logic.Slot
import dev.dawnswap.logic.SwapWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * The end-to-end behaviour, exercised the way the launcher exercises it: build the same
 * intent a pinned shortcut carries, hand it to the activity, and see which app comes out.
 *
 * Both targets are web URLs so every launch resolves identically in the test environment -
 * that keeps the assertions about *which* app was chosen, not about package installation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrampolineActivityTest {

    private lateinit var context: Context
    private lateinit var repository: SwapRepository

    private val real = LaunchTarget.web("https://real.example/feed")!!
    private val decoy = LaunchTarget.web("https://decoy.example/news")!!

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SwapRepository(context)
    }

    /** A window guaranteed to contain the moment this test runs, whatever time that is. */
    private fun windowAroundNow(): SwapWindow {
        val now = LocalTime.now()
        val start = now.hour * 60 + now.minute
        return SwapWindow(start, (start + 60) % MINUTES_PER_DAY)
    }

    private fun configureLiveSwap() {
        repository.real = real
        repository.decoy = decoy
        repository.window = windowAroundNow()
        repository.enabled = true
    }

    private fun tap(intent: Intent): Intent? {
        val controller = Robolectric.buildActivity(TrampolineActivity::class.java, intent).create()
        return shadowOf(controller.get()).nextStartedActivity
    }

    private fun tap(slot: Slot = Slot.PRIMARY): Intent? =
        tap(TrampolineActivity.intentFor(context, slot))

    private fun Intent?.openedUrl(): String? = this?.data?.toString()

    // --- The headline behaviour ---------------------------------------------------------

    @Test
    fun `the first tap in the window opens the replacement`() {
        configureLiveSwap()

        val launched = tap()

        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals("https://decoy.example/news", launched.openedUrl())
    }

    @Test
    fun `the first tap records that the swap has been used`() {
        configureLiveSwap()

        tap()

        assertNotNull(SwapRepository(context).lastConsumed)
    }

    @Test
    fun `the second tap opens the original app`() {
        configureLiveSwap()

        tap()
        val second = tap()

        assertEquals("https://real.example/feed", second.openedUrl())
    }

    @Test
    fun `every later tap keeps opening the original app`() {
        configureLiveSwap()

        tap()

        repeat(5) {
            assertEquals("https://real.example/feed", tap().openedUrl())
        }
    }

    // --- Not armed ----------------------------------------------------------------------

    @Test
    fun `a disabled swap opens the original app straight away`() {
        configureLiveSwap()
        repository.enabled = false

        assertEquals("https://real.example/feed", tap().openedUrl())
    }

    @Test
    fun `a disabled swap does not use up the swap`() {
        configureLiveSwap()
        repository.enabled = false

        tap()

        assertNull(SwapRepository(context).lastConsumed)
    }

    @Test
    fun `a tap outside the window opens the original app`() {
        configureLiveSwap()
        // A window that cannot contain now: one minute wide, an hour behind.
        val now = LocalTime.now()
        val start = (now.hour * 60 + now.minute + 120) % MINUTES_PER_DAY
        repository.window = SwapWindow(start, (start + 1) % MINUTES_PER_DAY)

        assertEquals("https://real.example/feed", tap().openedUrl())
    }

    // --- Swap mode ----------------------------------------------------------------------

    @Test
    fun `the second slot hands back the original app while the swap is armed`() {
        configureLiveSwap()

        assertEquals("https://real.example/feed", tap(Slot.SECONDARY).openedUrl())
    }

    @Test
    fun `tapping the second slot does not use up the swap`() {
        configureLiveSwap()

        tap(Slot.SECONDARY)

        assertNull(SwapRepository(context).lastConsumed)
        assertEquals("https://decoy.example/news", tap(Slot.PRIMARY).openedUrl())
    }

    // --- Robustness ---------------------------------------------------------------------

    @Test
    fun `a tap before setup is finished opens the setup screen`() {
        val launched = tap()

        assertEquals(SetupActivity::class.java.name, launched?.component?.className)
    }

    @Test
    fun `an unrecognised slot name is treated as the primary slot`() {
        configureLiveSwap()
        val hostile = Intent(context, TrampolineActivity::class.java).putExtra("slot", "NONSENSE")

        assertEquals("https://decoy.example/news", tap(hostile).openedUrl())
    }

    @Test
    fun `a missing slot extra is treated as the primary slot`() {
        configureLiveSwap()
        val bare = Intent(context, TrampolineActivity::class.java)

        assertEquals("https://decoy.example/news", tap(bare).openedUrl())
    }

    @Test
    fun `the trampoline always finishes so it never shows a window`() {
        configureLiveSwap()
        val controller = Robolectric
            .buildActivity(TrampolineActivity::class.java, TrampolineActivity.intentFor(context, Slot.PRIMARY))
            .create()

        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun `an out of range stored window does not crash the tap path`() {
        configureLiveSwap()
        context.getSharedPreferences("dawn_swap", Context.MODE_PRIVATE)
            .edit().putInt("window_start", Int.MAX_VALUE).commit()

        // Must resolve to something rather than throwing out of a property getter.
        assertNotNull(tap())
    }
}
