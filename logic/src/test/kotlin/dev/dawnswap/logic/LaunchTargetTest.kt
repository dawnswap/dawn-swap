package dev.dawnswap.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LaunchTargetTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "com.instagram.android",
            "com.google.android.youtube",
            "org.mozilla.firefox",
            "a.b",
            "com.app2.thing",
            "com.my_app.core",
        ],
    )
    fun `plausible package names are accepted`(packageName: String) {
        assertEquals(LaunchTarget.App(packageName), LaunchTarget.app(packageName))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "nodots",
            ".leadingdot",
            "trailingdot.",
            "com..double",
            "9com.starts.with.digit",
            "com.9starts.with.digit",
            "com.has space",
            "com.has-hyphen",
        ],
    )
    fun `malformed package names are rejected`(packageName: String) {
        assertNull(LaunchTarget.app(packageName))
    }

    @Test
    fun `surrounding whitespace is trimmed from a package name`() {
        assertEquals(LaunchTarget.App("com.example.app"), LaunchTarget.app("  com.example.app  "))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://example.com",
            "https://example.com",
            "https://news.local/today",
            "HTTPS://EXAMPLE.COM",
            "https://example.com:8443/path?q=1",
        ],
    )
    fun `http and https urls are accepted`(url: String) {
        assertEquals(LaunchTarget.Web(url), LaunchTarget.web(url))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "intent://scan/#Intent;scheme=zxing;end",
            "file:///data/data/com.example/secret",
            "javascript:alert(1)",
            "content://media/external/images",
            "market://details?id=com.example",
            "://example.com",
            "https://",
            "https://   ",
            "example.com",
            "",
        ],
    )
    fun `non-web schemes and malformed urls are rejected`(url: String) {
        assertNull(LaunchTarget.web(url))
    }

    @Test
    fun `an app target survives a serialize and parse round trip`() {
        val target = LaunchTarget.App("com.instagram.android")
        assertEquals(target, LaunchTarget.parse(target.serialize()))
    }

    @Test
    fun `a web target survives a serialize and parse round trip`() {
        val target = LaunchTarget.Web("https://news.local/today")
        assertEquals(target, LaunchTarget.parse(target.serialize()))
    }

    @Test
    fun `a url that looks like a package name is never mistaken for one`() {
        val web = LaunchTarget.web("https://www.example.com")!!
        assertEquals(LaunchTarget.Web("https://www.example.com"), LaunchTarget.parse(web.serialize()))
    }

    @Test
    fun `a package name that looks like a host is never mistaken for a url`() {
        val app = LaunchTarget.app("www.example.com")!!
        assertEquals(LaunchTarget.App("www.example.com"), LaunchTarget.parse(app.serialize()))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "com.instagram.android",
            "https://example.com",
            "app:",
            "web:",
            "app:not a package",
            "web:file:///etc/passwd",
            "garbage",
        ],
    )
    fun `untagged or invalid stored values parse to nothing`(stored: String) {
        assertNull(LaunchTarget.parse(stored))
    }

    @Test
    fun `a null stored value parses to nothing`() {
        assertNull(LaunchTarget.parse(null))
    }
}
