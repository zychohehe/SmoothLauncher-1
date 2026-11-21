package id.smooth.launcher.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import id.smooth.launcher.data.Prefs

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** 0f jika pengguna mematikan animasi sistem (Remove animations). */
fun Context.motionScale(): Float =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

object Palette {
    fun isDark(c: Context, p: Prefs): Boolean = when (p.theme) {
        1 -> false
        2 -> true
        else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    fun drawerGlass(d: Boolean) = if (d) 0xD91C1C1E.toInt() else 0xD9F2F2F7.toInt()
    fun barGlass(d: Boolean) = if (d) 0x80202226.toInt() else 0x66FFFFFF
    fun field(d: Boolean) = if (d) 0x33FFFFFF else 0x14000000
    fun text(d: Boolean) = if (d) Color.WHITE else 0xFF1C1C1E.toInt()
    fun hint(d: Boolean) = if (d) 0x99FFFFFF.toInt() else 0x801C1C1E.toInt()
}

fun glass(c: Context, color: Int, radiusDp: Float, topOnly: Boolean = false): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        val r = c.dpf(radiusDp)
        if (topOnly) cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else cornerRadius = r
        setStroke(c.dp(1).coerceAtLeast(1), 0x26FFFFFF)
    }
