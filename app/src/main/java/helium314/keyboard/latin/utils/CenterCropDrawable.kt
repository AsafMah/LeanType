// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class CenterCropDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val dwidth = bitmap.width
        val dheight = bitmap.height
        val vwidth = bounds.width()
        val vheight = bounds.height()

        if (dwidth <= 0 || dheight <= 0 || vwidth <= 0 || vheight <= 0) {
            return
        }

        val scale: Float
        var dx = 0f
        var dy = 0f

        if (dwidth * vheight > vwidth * dheight) {
            scale = vheight.toFloat() / dheight.toFloat()
            dx = (vwidth - dwidth * scale) * 0.5f
        } else {
            scale = vwidth.toFloat() / dwidth.toFloat()
            dy = (vheight - dheight * scale) * 0.5f
        }

        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left + dx, bounds.top + dy)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return if (bitmap.hasAlpha() || paint.alpha < 255) {
            PixelFormat.TRANSLUCENT
        } else {
            PixelFormat.OPAQUE
        }
    }

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height
}
