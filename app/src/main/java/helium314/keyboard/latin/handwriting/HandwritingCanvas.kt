// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint().apply {
        color = 0xFF3F51B5.toInt() // Default blue, will be overridden by theme later
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val path = Path()
    private val strokes = mutableListOf<FloatArray>()
    private var currentStroke = mutableListOf<Float>()
    private var startTime: Long = 0
    private var isRecognitionDone = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recognitionTimeout = 700L
    private val recognizeRunnable = Runnable {
        isRecognitionDone = true
        onRecognitionTriggered?.invoke(ArrayList(strokes))
    }

    var onRecognitionTriggered: ((List<FloatArray>) -> Unit)? = null
    var onStrokeStarted: (() -> Unit)? = null

    fun setStrokeColor(color: Int) {
        strokePaint.color = color
        invalidate()
    }

    fun clear() {
        mainHandler.removeCallbacks(recognizeRunnable)
        path.reset()
        strokes.clear()
        currentStroke.clear()
        isRecognitionDone = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val time = event.eventTime

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacks(recognizeRunnable)
                if (isRecognitionDone) {
                    onStrokeStarted?.invoke()
                    isRecognitionDone = false
                }
                path.moveTo(x, y)
                startTime = time
                currentStroke.clear()
                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add(0f) // Relative time start
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add((time - startTime).toFloat())
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(x, y)
                currentStroke.add(x)
                currentStroke.add(y)
                currentStroke.add((time - startTime).toFloat())
                strokes.add(currentStroke.toFloatArray())
                currentStroke.clear()
                invalidate()
                
                mainHandler.postDelayed(recognizeRunnable, recognitionTimeout)
            }
        }
        return true
    }
}
