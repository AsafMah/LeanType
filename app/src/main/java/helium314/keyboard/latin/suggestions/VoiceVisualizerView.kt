// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import helium314.keyboard.latin.utils.dpToPx
import kotlin.math.sin

class VoiceVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode {
        IDLE,
        CONNECTING,
        RECORDING,
        PROCESSING
    }

    private var mode = Mode.IDLE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barCount = 4
    private val barRect = RectF()
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init {
        val defaultColor = 0xFF4285F4.toInt()
        paint.color = defaultColor
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        updateAnimation()
        invalidate()
    }

    private fun updateAnimation() {
        animator?.cancel()
        animator = null

        if (mode == Mode.IDLE || !isAttachedToWindow) {
            return
        }

        val duration = when (mode) {
            Mode.CONNECTING -> 1200L
            Mode.RECORDING -> 800L
            Mode.PROCESSING -> 600L
            Mode.IDLE -> 0L
        }

        if (duration > 0) {
            animator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                this.duration = duration
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    phase = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            updateAnimation()
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == Mode.IDLE) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val barWidth = 3.dpToPx(resources).toFloat()
        val barSpacing = 3.dpToPx(resources).toFloat()
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * barSpacing
        var startX = (viewWidth - totalBarsWidth) / 2f
        val centerY = viewHeight / 2f

        val minHeight = 4.dpToPx(resources).toFloat()
        val maxHeight = (viewHeight * 0.6f).coerceAtLeast(minHeight + 6.dpToPx(resources))

        for (i in 0 until barCount) {
            val barHeight = when (mode) {
                Mode.RECORDING -> {
                    val offset = i * 0.9f
                    val wave = (sin(phase + offset) + 1f) / 2f
                    val secondary = (sin(phase * 1.5f + offset * 1.3f) + 1f) / 4f
                    val normalized = (wave * 0.7f + secondary * 0.3f).coerceIn(0f, 1f)
                    minHeight + normalized * (maxHeight - minHeight)
                }
                Mode.PROCESSING -> {
                    val offset = i * (Math.PI / 2).toFloat()
                    val wave = (sin(phase * 2 + offset) + 1f) / 2f
                    minHeight + wave * (maxHeight - minHeight) * 0.75f
                }
                Mode.CONNECTING -> {
                    val pulse = (sin(phase) + 1f) / 2f
                    minHeight + pulse * (maxHeight - minHeight) * 0.4f
                }
                Mode.IDLE -> minHeight
            }

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val right = startX + barWidth
            val radius = barWidth / 2f

            barRect.set(startX, top, right, bottom)
            canvas.drawRoundRect(barRect, radius, radius, paint)

            startX += barWidth + barSpacing
        }
    }
}
