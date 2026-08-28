package dev.dawnswap.logic

/**
 * What a home-screen slot opens when it is tapped.
 *
 * Targets are stored in an explicitly tagged form (`app:` / `web:`) rather than being
 * sniffed from free text. Without the tag `www.example.com` is indistinguishable from a
 * package name, and guessing wrong would either fail to launch or — worse — hand an
 * attacker-supplied string to an intent resolver.
 */
sealed interface LaunchTarget {

    /** An installed app, launched via its default launcher activity. */
    data class App(val packageName: String) : LaunchTarget

    /** A web page, launched via `ACTION_VIEW`. Always `http` or `https`. */
    data class Web(val url: String) : LaunchTarget

    /** The stable string form written to storage and read back by [parse]. */
    fun serialize(): String = when (this) {
        is App -> "$APP_PREFIX$packageName"
        is Web -> "$WEB_PREFIX$url"
    }

    companion object {
        private const val APP_PREFIX = "app:"
        private const val WEB_PREFIX = "web:"

        /** Only these schemes may be launched. Anything else could resolve to an arbitrary intent. */
        private val ALLOWED_SCHEMES = setOf("http", "https")

        /** Android package names: dot-separated segments, each starting with a letter. */
        private val PACKAGE_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

        /** Builds an [App] target, or null when [packageName] is not a usable package name. */
        fun app(packageName: String): App? {
            val trimmed = packageName.trim()
            return if (PACKAGE_PATTERN.matches(trimmed)) App(trimmed) else null
        }

        /** Builds a [Web] target, or null when [url] is not a plain http/https URL with a host. */
        fun web(url: String): Web? {
            val trimmed = url.trim()
            val separator = trimmed.indexOf("://")
            if (separator <= 0) return null
            if (trimmed.substring(0, separator).lowercase() !in ALLOWED_SCHEMES) return null
            if (trimmed.substring(separator + 3).isBlank()) return null
            return Web(trimmed)
        }

        /** Reads back a value produced by [serialize]. Returns null for anything unrecognised. */
        fun parse(stored: String?): LaunchTarget? = when {
            stored == null -> null
            stored.startsWith(APP_PREFIX) -> app(stored.removePrefix(APP_PREFIX))
            stored.startsWith(WEB_PREFIX) -> web(stored.removePrefix(WEB_PREFIX))
            else -> null
        }
    }
}
