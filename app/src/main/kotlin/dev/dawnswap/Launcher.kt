package dev.dawnswap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.graphics.drawable.IconCompat
import dev.dawnswap.logic.LaunchTarget

/** Turns a [LaunchTarget] into something Android can actually open, name, or draw. */
object Launcher {

    private const val FALLBACK_ICON_PX = 192

    /** The intent that opens [target], or null when it can no longer be opened. */
    fun intentFor(context: Context, target: LaunchTarget): Intent? = when (target) {
        is LaunchTarget.App -> context.packageManager.getLaunchIntentForPackage(target.packageName)
        is LaunchTarget.Web -> Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
    }

    /** The name shown under the shortcut. */
    fun labelFor(context: Context, target: LaunchTarget): String = when (target) {
        is LaunchTarget.App -> appLabel(context, target.packageName) ?: target.packageName
        is LaunchTarget.Web -> Uri.parse(target.url).host ?: context.getString(R.string.web_page)
    }

    /**
     * The icon the shortcut wears. Falls back to this app's own icon when the target has
     * none we can read - a web page, or an app that has since been uninstalled.
     */
    fun iconFor(context: Context, target: LaunchTarget): IconCompat {
        val drawable = when (target) {
            is LaunchTarget.App -> runCatching {
                context.packageManager.getApplicationIcon(target.packageName)
            }.getOrNull()

            is LaunchTarget.Web -> null
        }

        return drawable?.toIcon() ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun appLabel(context: Context, packageName: String): String? = runCatching {
        val packages = context.packageManager
        packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    /**
     * Adaptive icons are full-bleed and expect the launcher to mask them, so they must be
     * wrapped as adaptive or they end up over-cropped. Legacy icons are already the final
     * artwork and must not be, or the launcher crops away their edges.
     */
    private fun Drawable.toIcon(): IconCompat {
        val bitmap = rasterize()
        return if (this is AdaptiveIconDrawable) {
            IconCompat.createWithAdaptiveBitmap(bitmap)
        } else {
            IconCompat.createWithBitmap(bitmap)
        }
    }

    /**
     * Draws any drawable into a bitmap. Casting straight to `BitmapDrawable` would throw for
     * the `AdaptiveIconDrawable` that essentially every modern app returns.
     */
    private fun Drawable.rasterize(): Bitmap {
        val size = maxOf(intrinsicWidth, intrinsicHeight).takeIf { it > 0 } ?: FALLBACK_ICON_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bitmap))
        return bitmap
    }
}
