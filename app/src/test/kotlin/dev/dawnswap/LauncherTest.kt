package dev.dawnswap

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.dawnswap.logic.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // --- Intents ------------------------------------------------------------------------

    @Test
    fun `a web target becomes a view intent for that url`() {
        val intent = Launcher.intentFor(context, LaunchTarget.Web("https://news.local/today"))

        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https://news.local/today", intent?.data?.toString())
    }

    @Test
    fun `an app that is not installed yields no intent rather than throwing`() {
        val intent = Launcher.intentFor(context, LaunchTarget.App("com.not.installed.anywhere"))

        assertNull(intent)
    }

    // --- Labels -------------------------------------------------------------------------

    @Test
    fun `a web target is labelled with its host`() {
        val label = Launcher.labelFor(context, LaunchTarget.Web("https://news.local/today?x=1"))

        assertEquals("news.local", label)
    }

    @Test
    fun `a web target on a non standard port is still labelled with its host`() {
        val label = Launcher.labelFor(context, LaunchTarget.Web("https://news.local:8443/today"))

        assertEquals("news.local", label)
    }

    @Test
    fun `an unknown app falls back to its package name rather than throwing`() {
        val label = Launcher.labelFor(context, LaunchTarget.App("com.not.installed.anywhere"))

        assertEquals("com.not.installed.anywhere", label)
    }

    // --- Icons --------------------------------------------------------------------------
    // Both of these once threw: a web page has no icon to read, and an uninstalled package
    // makes getApplicationIcon raise. Each must fall back to this app's own icon instead.

    @Test
    fun `a web target falls back to a usable icon`() {
        assertNotNull(Launcher.iconFor(context, LaunchTarget.Web("https://news.local")))
    }

    @Test
    fun `an app that is not installed falls back to a usable icon`() {
        assertNotNull(Launcher.iconFor(context, LaunchTarget.App("com.not.installed.anywhere")))
    }
}
