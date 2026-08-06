package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils

/**
 * Manages the floating keyboard by reparenting the existing main_keyboard_frame
 * from the IME's InputView into a TYPE_APPLICATION_OVERLAY window.
 *
 * Key sizes are dynamically adjusted by setting a floating width override in
 * ResourceUtils before triggering a keyboard reload.
 */
class FloatingKeyboardManager(private val context: Context, private val latinIME: LatinIME) {

    companion object {
        private const val TAG = "FloatingKeyboardManager"
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val PREF_X = "floating_x"
        private const val PREF_Y = "floating_y"
        private const val PREF_WIDTH = "floating_width"
        private const val PREF_SCALE = "floating_scale"
        private const val FLOATING_WIDTH_FRACTION = 0.75f
        private const val HEADER_HEIGHT_DP = 28
        private const val CORNER_RADIUS_DP = 16f
    }

    private val prefs: SharedPreferences by lazy {
        DeviceProtectedUtils.getSharedPreferences(context, PREFS_NAME)
    }

    var overlayRoot: FrameLayout? = null
        private set
    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var savedParent: ViewGroup? = null
    private var savedLayoutParams: ViewGroup.LayoutParams? = null
    private var savedParentIndex: Int = -1

    // Touch tracking for drag & resize
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialResizeTouchX = 0f
    private var initialResizeTouchY = 0f
    private var initialResizeWidth = 0
    private var initialResizeHeight = 0
    private var initialResizeScale = 1.0f

    var isFloating = false
        private set

    fun canDrawOverlays(): Boolean = AndroidSettings.canDrawOverlays(context)

    fun requestOverlayPermission() {
        val intent = Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (!canDrawOverlays() || isFloating) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Calculate floating keyboard width & scale bounds
        val dm = context.resources.displayMetrics
        val minWidth = (dm.widthPixels * 0.40f).toInt()
        val maxWidth = (dm.widthPixels * 0.95f).toInt()
        val minHeight = (120 * dm.density).toInt()
        val maxHeight = (dm.heightPixels * 0.75f).toInt()
        val defaultWidth = (dm.widthPixels * FLOATING_WIDTH_FRACTION).toInt()
        val savedWidth = prefs.getInt(PREF_WIDTH, -1)
        val floatingWidth = (if (savedWidth != -1) savedWidth else defaultWidth).coerceIn(minWidth, maxWidth)
        val savedScale = prefs.getFloat(PREF_SCALE, 1.0f).coerceIn(0.5f, 1.8f)

        // Get theme colors
        val colors = Settings.getValues().mColors
        val bgColor = colors.get(ColorType.MAIN_BACKGROUND)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val density = dm.density
        val cornerRadius = CORNER_RADIUS_DP * density
        val headerHeight = (HEADER_HEIGHT_DP * density).toInt()

        // Create the overlay root with rounded corners and clipping
        val overlayBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            this.cornerRadius = cornerRadius
        }
        overlayRoot = FrameLayout(context).apply {
            background = overlayBg
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        // Create header bar with theme-matching colors and rounded top corners
        val headerBar = createHeaderBar(headerHeight, bgColor, textColor, density, cornerRadius, minWidth, maxWidth, minHeight, maxHeight)

        // Build content container: header on top, keyboard below
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,   // top-left
                    cornerRadius, cornerRadius,   // top-right
                    cornerRadius, cornerRadius,   // bottom-right
                    cornerRadius, cornerRadius    // bottom-left
                )
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        contentContainer.addView(headerBar)      // Index 0
        overlayRoot?.addView(contentContainer) ?: return

        // Calculate window position
        val savedX = prefs.getInt(PREF_X, -1)
        val savedY = prefs.getInt(PREF_Y, -1)

        windowParams = WindowManager.LayoutParams(
            floatingWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (savedX != -1 && savedY != -1) {
                x = savedX
                y = savedY
            } else {
                // Center horizontally, near bottom third
                x = (dm.widthPixels - floatingWidth) / 2
                y = dm.heightPixels / 3
            }
        }

        try {
            windowManager?.addView(overlayRoot, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
            overlayRoot = null
            return
        }

        isFloating = true
        
        // Manually trigger reparenting of the current input view into the overlay.
        // reloadKeyboard() alone won't trigger setInputView() if the theme hasn't changed.
        latinIME.mInputView?.let { onInputViewRecreated(it) }

        // Set the floating width & scale overrides so keyboard keys re-measure
        ResourceUtils.setFloatingKeyboardWidth(floatingWidth)
        ResourceUtils.setFloatingKeyboardScale(savedScale)

        // Force keyboard reload so keys re-measure at the new width & scale
        // This will trigger onInputViewRecreated which reparents the NEW keyboard into our overlay
        KeyboardSwitcher.getInstance().reloadKeyboard()

        // Hide the IME window so the bottom nav bar goes away
        latinIME.onFloatingKeyboardShown()

        Log.i(TAG, "Floating keyboard shown at ${floatingWidth}px width, scale ${savedScale}")
    }

    fun hide(showDockedKeyboard: Boolean = true) {
        if (!isFloating) return

        // Clear the floating overrides FIRST
        ResourceUtils.setFloatingKeyboardWidth(0)
        ResourceUtils.setFloatingKeyboardScale(0.0f)

        val mainKeyboardFrame = overlayRoot?.findViewById<View>(R.id.main_keyboard_frame)
        if (mainKeyboardFrame != null) {
            // Remove from overlay content container so it can be safely GC'd
            (mainKeyboardFrame.parent as? ViewGroup)?.removeView(mainKeyboardFrame)
        }

        // Remove overlay window
        overlayRoot?.let { root ->
            try {
                windowManager?.removeView(root)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayRoot = null
        savedParent = null
        savedLayoutParams = null
        savedParentIndex = -1
        isFloating = false

        // Show the IME window again
        latinIME.onFloatingKeyboardHidden(showDockedKeyboard)

        // Reload keyboard at full width so keys re-measure properly
        KeyboardSwitcher.getInstance().reloadKeyboard()

        Log.i(TAG, "Floating keyboard hidden, docked mode restored")
    }

    fun toggle() {
        if (isFloating) {
            hide()
        } else {
            if (canDrawOverlays()) {
                show()
            } else {
                requestOverlayPermission()
            }
        }
    }

    /**
     * Called from LatinIME.setInputView() when the input view is recreated
     * (e.g., theme change, orientation change). If floating mode is active,
     * we need to reparent the new keyboard views into the existing overlay.
     */
    fun onInputViewRecreated(newInputView: View) {
        if (!isFloating) return

        Log.i(TAG, "Input view recreated while floating, re-reparenting keyboard")

        val newMainKeyboardFrame = newInputView.findViewById<View>(R.id.main_keyboard_frame)
            ?: return
        val newParent = newMainKeyboardFrame.parent as? ViewGroup ?: return

        // Save new parent info
        savedParent = newParent
        savedLayoutParams = newMainKeyboardFrame.layoutParams
        savedParentIndex = newParent.indexOfChild(newMainKeyboardFrame)

        // Find the content container in our overlay (the LinearLayout)
        val contentContainer = overlayRoot?.getChildAt(0) as? LinearLayout ?: return

        // Remove old keyboard frame from overlay content container (index 1, after header)
        if (contentContainer.childCount > 1) {
            contentContainer.removeViewAt(1)
        }

        // Reparent new keyboard frame
        val floatingWidth = ResourceUtils.getFloatingKeyboardWidth()
        newParent.removeView(newMainKeyboardFrame)
        newMainKeyboardFrame.layoutParams = LinearLayout.LayoutParams(
            if (floatingWidth > 0) floatingWidth else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentContainer.addView(newMainKeyboardFrame)
    }

    /**
     * Called from LatinIME.onDestroy() to clean up.
     */
    fun destroy() {
        if (isFloating) {
            ResourceUtils.setFloatingKeyboardWidth(0)
            ResourceUtils.setFloatingKeyboardScale(0.0f)
            overlayRoot?.let { root ->
                try {
                    windowManager?.removeView(root)
                } catch (_: Exception) {}
            }
            overlayRoot = null
            isFloating = false
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createHeaderBar(
        height: Int,
        bgColor: Int,
        textColor: Int,
        density: Float,
        cornerRadius: Float,
        minWidth: Int,
        maxWidth: Int,
        minHeight: Int,
        maxHeight: Int
    ): FrameLayout {
        // Header with rounded top corners matching the container
        val headerBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,   // top-left
                    cornerRadius, cornerRadius,   // top-right
                    0f, 0f,                       // bottom-right
                    0f, 0f                        // bottom-left
                )
            }
        }

        // Sleek drag handle pill in center
        val pillWidth = (44 * density).toInt()
        val pillHeight = (5 * density).toInt()
        val defaultPillColor = (textColor and 0x00FFFFFF) or 0x66000000.toInt()
        val activePillColor = (textColor and 0x00FFFFFF) or 0xCC000000.toInt()
        val dragHandleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 2.5f * density
            setColor(defaultPillColor)
        }
        val dragHandle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(pillWidth, pillHeight).apply {
                gravity = Gravity.CENTER
            }
            background = dragHandleBg
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
        }
        headerBar.addView(dragHandle)

        // Close button (Top-Right)
        val closeBtnSize = (height * 0.75f).toInt()
        val closePadding = (3 * density).toInt()
        val closeBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = (6 * density).toInt()
            }
            setPadding(closePadding, closePadding, closePadding, closePadding)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(textColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = 6 * density
                setColor((textColor and 0x00FFFFFF) or 0x1A000000.toInt())
            }
            contentDescription = "Close floating keyboard"
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { toggle() }
        }
        headerBar.addView(closeBtn)

        // Inverted L-shaped Corner Pill Resize Button (Top-Left)
        val btnSize = closeBtnSize
        val defaultAlpha = 0x66000000.toInt()
        val activeAlpha = 0xEE000000.toInt()
        val defaultBgColor = (textColor and 0x00FFFFFF) or 0x1F000000.toInt()
        val activeBgColor = (textColor and 0x00FFFFFF) or 0x55000000.toInt()

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = (textColor and 0x00FFFFFF) or defaultAlpha
            strokeWidth = 3.5f * density
            strokeCap = android.graphics.Paint.Cap.ROUND
            style = android.graphics.Paint.Style.STROKE
        }

        val resizeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 6 * density
            setColor(defaultBgColor)
        }

        val resizeBtn = object : View(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val pad = 6.5f * density
                val oval = android.graphics.RectF(pad, pad, w - pad, h - pad)
                // Draw top-left inverted L arc (180 to 270 degrees)
                canvas.drawArc(oval, 180f, 90f, false, paint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (6 * density).toInt()
            }
            background = resizeBg
            contentDescription = "Resize floating keyboard"
        }

        var initialWindowX = 0
        var initialWindowY = 0

        resizeBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialResizeTouchX = event.rawX
                    initialResizeTouchY = event.rawY
                    initialWindowX = windowParams?.x ?: 0
                    initialWindowY = windowParams?.y ?: 0
                    initialResizeWidth = windowParams?.width ?: ResourceUtils.getFloatingKeyboardWidth()
                    initialResizeHeight = overlayRoot?.height ?: 0
                    initialResizeScale = ResourceUtils.getFloatingKeyboardScale().let { if (it > 0f) it else 1.0f }
                    paint.color = (textColor and 0x00FFFFFF) or activeAlpha
                    paint.strokeWidth = 4.5f * density
                    resizeBg.setColor(activeBgColor)
                    resizeBtn.invalidate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialResizeTouchX).toInt()
                    val dy = (event.rawY - initialResizeTouchY).toInt()

                    // Dragging top-left corner: dx < 0 expands left, dy < 0 expands top
                    val targetWidth = initialResizeWidth - dx
                    val baseHeight = if (initialResizeHeight > 0) initialResizeHeight else (250 * density).toInt()
                    val targetHeight = baseHeight - dy

                    val newWidth = targetWidth.coerceIn(minWidth, maxWidth)
                    val newHeight = targetHeight.coerceIn(minHeight, maxHeight)

                    val effectiveDx = initialResizeWidth - newWidth
                    val effectiveDy = baseHeight - newHeight

                    windowParams?.let { lp ->
                        lp.x = initialWindowX + effectiveDx
                        lp.y = initialWindowY + effectiveDy
                        lp.width = newWidth
                        lp.height = newHeight
                        try {
                            windowManager?.updateViewLayout(overlayRoot, lp)
                            val content = overlayRoot?.getChildAt(0) as? LinearLayout
                            if (content != null) {
                                content.layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                if (content.childCount > 1) {
                                    val keyboardFrame = content.getChildAt(1)
                                    keyboardFrame.layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        0,
                                        1.0f
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update overlay layout on resize", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    paint.color = (textColor and 0x00FFFFFF) or defaultAlpha
                    paint.strokeWidth = 3.5f * density
                    resizeBg.setColor(defaultBgColor)
                    resizeBtn.invalidate()

                    windowParams?.let { lp ->
                        val finalWidth = lp.width
                        val finalHeight = lp.height
                        val baseHeight = if (initialResizeHeight > 0) initialResizeHeight else (250 * density).toInt()
                        val heightRatio = if (baseHeight > 0) finalHeight.toFloat() / baseHeight else 1.0f
                        val finalScale = (initialResizeScale * heightRatio).coerceIn(0.5f, 1.8f)

                        // Reset window height back to WRAP_CONTENT so it wraps newly-measured keys tightly
                        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                        try {
                            windowManager?.updateViewLayout(overlayRoot, lp)
                        } catch (_: Exception) {}

                        prefs.edit()
                            .putInt(PREF_X, lp.x)
                            .putInt(PREF_Y, lp.y)
                            .putInt(PREF_WIDTH, finalWidth)
                            .putFloat(PREF_SCALE, finalScale)
                            .apply()

                        ResourceUtils.setFloatingKeyboardWidth(finalWidth)
                        ResourceUtils.setFloatingKeyboardScale(finalScale)
                        KeyboardSwitcher.getInstance().reloadKeyboard()
                    }
                    true
                }
                else -> false
            }
        }

        headerBar.addView(resizeBtn)

        // Setup drag on the entire header bar with touch state feedback
        headerBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragHandleBg.setColor(activePillColor)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    windowParams?.let { lp ->
                        lp.x = (lp.x + dx).toInt()
                        lp.y = (lp.y + dy).toInt()
                        try {
                            windowManager?.updateViewLayout(overlayRoot, lp)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update overlay layout", e)
                        }
                    }
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragHandleBg.setColor(defaultPillColor)
                    savePosition()
                    true
                }
                else -> false
            }
        }

        return headerBar
    }

    private fun savePosition() {
        windowParams?.let { lp ->
            prefs.edit()
                .putInt(PREF_X, lp.x)
                .putInt(PREF_Y, lp.y)
                .apply()
        }
    }
}
