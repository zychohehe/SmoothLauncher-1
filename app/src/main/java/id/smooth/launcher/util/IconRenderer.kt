package id.smooth.launcher.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable

/** Menggambar ikon sebagai rounded-square seragam (gaya iOS), terlepas dari bentuk mask bawaan sistem. */
object IconRenderer {
    fun render(d: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = px * 0.225f
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, Path.Direction.CW) })
        if (d is AdaptiveIconDrawable) {
            val o = (px * 0.25f).toInt()                 // layer 108dp vs area terlihat 72dp
            val b = Rect(-o, -o, px + o, px + o)
            d.background?.let { it.bounds = b; it.draw(c) }
            d.foreground?.let { it.bounds = b; it.draw(c) }
        } else {
            c.drawColor(0xFFF2F2F7.toInt())
            val inset = (px * 0.14f).toInt()
            d.setBounds(inset, inset, px - inset, px - inset)
            d.draw(c)
        }
        return bmp
    }
}
