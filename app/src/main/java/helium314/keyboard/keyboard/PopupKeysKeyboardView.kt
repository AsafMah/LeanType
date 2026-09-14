/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.accessibility.PopupKeysKeyboardAccessibilityDelegate
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.emoji.EmojiViewCallback
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * A view that renders a virtual [PopupKeysKeyboard]. It handles rendering of keys and
 * detecting key presses and touch movements.
 */
open class PopupKeysKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.popupKeysKeyboardViewStyle
) : KeyboardView(context, attrs, defStyle), PopupKeysPanel {

    private val mCoordinates: IntArray = CoordinateUtils.newInstance()

    private val mDivider: Drawable?
    protected val mKeyDetector: KeyDetector
    private var mController: PopupKeysPanel.Controller = PopupKeysPanel.EMPTY_CONTROLLER
    protected var mListener: KeyboardActionListener? = null
    protected var mEmojiViewCallback: EmojiViewCallback? = null
    private var mOriginXInParent = 0
    private var mOriginYInParent = 0
    private var mCurrentKey: Key? = null
    private var mSwipeShortcutDirection: SwipeShortcutMenu.Direction? = null
    private var mSwipeStartYInParent = 0

    private var mActivePointerId = 0

    protected var mAccessibilityDelegate: PopupKeysKeyboardAccessibilityDelegate? = null

    init {
        val popupKeysKeyboardViewAttr = context.obtainStyledAttributes(
            attrs,
            R.styleable.PopupKeysKeyboardView,
            defStyle,
            R.style.PopupKeysKeyboardView
        )
        mDivider = popupKeysKeyboardViewAttr.getDrawable(R.styleable.PopupKeysKeyboardView_divider)
        popupKeysKeyboardViewAttr.recycle()
        mKeyDetector = PopupKeysDetector(
            resources.getDimension(R.dimen.config_popup_keys_keyboard_slide_allowance)
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = keyboard
        if (keyboard != null) {
            val width = keyboard.mOccupiedWidth + paddingLeft + paddingRight
            val height = keyboard.mOccupiedHeight + paddingTop + paddingBottom
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDrawKeyTopVisuals(
        key: Key,
        canvas: Canvas,
        paint: Paint,
        params: KeyDrawParams
    ) {
        if (!key.isSpacer || key !is PopupKeysKeyboard.PopupKeyDivider || mDivider == null) {
            super.onDrawKeyTopVisuals(key, canvas, paint, params)
            return
        }
        val keyWidth = key.drawWidth
        val keyHeight = key.height
        val iconWidth = min(mDivider.intrinsicWidth, keyWidth)
        val iconHeight = mDivider.intrinsicHeight
        val iconX = (keyWidth - iconWidth) / 2 // Align horizontally center
        val iconY = (keyHeight - iconHeight) / 2 // Align vertically center
        drawIcon(canvas, mDivider, iconX, iconY, iconWidth, iconHeight)
    }

    override fun setKeyboard(keyboard: Keyboard) {
        mCurrentKey?.onReleased()
        mCurrentKey = null
        mSwipeShortcutDirection = null
        super.setKeyboard(keyboard)
        mKeyDetector.setKeyboard(
            keyboard,
            -paddingLeft.toFloat(),
            -paddingTop.toFloat() + verticalCorrection
        )
        if (AccessibilityUtils.instance.isAccessibilityEnabled) {
            val delegate = mAccessibilityDelegate ?: PopupKeysKeyboardAccessibilityDelegate(this, mKeyDetector).also {
                it.setOpenAnnounce(R.string.spoken_open_popup_keys_keyboard)
                it.setCloseAnnounce(R.string.spoken_close_popup_keys_keyboard)
                mAccessibilityDelegate = it
            }
            delegate.keyboard = keyboard
        } else {
            mAccessibilityDelegate = null
        }
        val shortcutKey = keyboard.getKey(KeyCode.VOICE_INPUT)
        if (shortcutKey != null) {
            shortcutKey.isEnabled = RichInputMethodManager.getInstance().isShortcutImeReady
            invalidateKey(shortcutKey)
        }
    }

    override fun showPopupKeysPanel(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int,
        listener: KeyboardActionListener
    ) {
        mListener = listener
        mEmojiViewCallback = null
        showPopupKeysPanelInternal(parentView, controller, pointX, pointY)
    }

    fun showSwipeShortcutPanel(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        keysLeftInParent: Int,
        keysTopInParent: Int,
        direction: SwipeShortcutMenu.Direction,
        startYInParent: Int,
        listener: KeyboardActionListener
    ) {
        mListener = listener
        mEmojiViewCallback = null
        mController = controller
        mSwipeShortcutDirection = direction
        mSwipeStartYInParent = startYInParent
        mOriginXInParent = keysLeftInParent - paddingLeft
        mOriginYInParent = keysTopInParent - paddingTop
        val container = getContainerView()
        parentView.getLocationInWindow(mCoordinates)
        val parentXInWindow = CoordinateUtils.x(mCoordinates)
        val parentYInWindow = CoordinateUtils.y(mCoordinates)
        // The preview container is window-relative; its child view includes background padding.
        container.x = (parentXInWindow + mOriginXInParent - left).toFloat()
        container.y = (parentYInWindow + mOriginYInParent - top).toFloat()
        translationX = 0f
        controller.setLayoutGravity(Gravity.CENTER_HORIZONTAL)
        onPanelShown()
    }

    override fun showPopupKeysPanel(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int,
        emojiViewCallback: EmojiViewCallback
    ) {
        mListener = null
        mEmojiViewCallback = emojiViewCallback
        showPopupKeysPanelInternal(parentView, controller, pointX, pointY)
    }

    @SuppressLint("RtlHardcoded") // a key on the left is on the left, independent of layout direction
    private fun showPopupKeysPanelInternal(
        parentView: View,
        controller: PopupKeysPanel.Controller,
        pointX: Int,
        pointY: Int
    ) {
        mController = controller
        val container = getContainerView()
        val keyboardHeight = (parentView as? KeyboardView)?.keyboard?.mOccupiedHeight
            ?: parentView.measuredHeight.takeIf { it > 0 }
            ?: (230 * resources.displayMetrics.density).toInt()
        val verticalOffsetPx = (keyboardHeight * Settings.getValues().mPopupKeysVerticalOffsetFraction).toInt()
        val x = pointX - getDefaultCoordX() - container.paddingLeft - paddingLeft
        val y = pointY - container.measuredHeight + container.paddingBottom + paddingBottom - verticalOffsetPx

        parentView.getLocationInWindow(mCoordinates)
        val rawContainerY = y + CoordinateUtils.y(mCoordinates)
        val containerY = max(0, rawContainerY)
        container.y = containerY.toFloat()

        // This is needed for cases where there's also a text popup above this keyboard
        val panelMaxX = parentView.measuredWidth - measuredWidth
        val panelFinalX = max(0, min(panelMaxX, x))
        val center = panelFinalX + measuredWidth / 2
        val keyboard = keyboard ?: return
        val layoutGravity = when {
            center < pointX - keyboard.mMostCommonKeyWidth / 2 -> Gravity.RIGHT
            center > pointX + keyboard.mMostCommonKeyWidth / 2 -> Gravity.LEFT
            else -> Gravity.CENTER_HORIZONTAL
        }

        var containerAdjustedX = x
        if (measuredWidth < container.measuredWidth) {
            containerAdjustedX = when (layoutGravity) {
                Gravity.LEFT -> panelFinalX
                Gravity.RIGHT -> panelFinalX + measuredWidth - container.measuredWidth
                else -> panelFinalX + (measuredWidth - container.measuredWidth) / 2
            }
        }

        // Ensure the horizontal position of the panel does not extend past the parentView edges.
        val containerMaxX = parentView.measuredWidth - container.measuredWidth
        val containerFinalX = max(0, min(containerMaxX, containerAdjustedX))
        val containerX = containerFinalX + CoordinateUtils.x(mCoordinates)
        container.x = containerX.toFloat()
        translationX = (panelFinalX - containerFinalX).toFloat()
        controller.setLayoutGravity(layoutGravity)

        val clampedY = containerY - CoordinateUtils.y(mCoordinates)
        mOriginXInParent = panelFinalX
        mOriginYInParent = clampedY + container.paddingTop + this.y.toInt()

        // Anchor animation pivot to the center of the pressed key:
        val keyCenterXInContainer = (pointX - containerFinalX).toFloat()
        container.pivotX = keyCenterXInContainer.coerceIn(0f, container.measuredWidth.toFloat())
        container.pivotY = container.measuredHeight.toFloat()

        onPanelShown()
    }

    private fun onPanelShown() {
        mController.onShowPopupKeysPanel(this)
        val accessibilityDelegate = mAccessibilityDelegate
        if (accessibilityDelegate != null && AccessibilityUtils.instance.isAccessibilityEnabled) {
            accessibilityDelegate.onShowPopupKeysKeyboard()
        }
    }

    /**
     * Returns the default x coordinate for showing this panel.
     */
    protected open fun getDefaultCoordX(): Int {
        return (keyboard as PopupKeysKeyboard).getDefaultCoordX()
    }

    override fun onDownEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {
        mActivePointerId = pointerId
        mCurrentKey = detectKey(x, y)
    }

    override fun onMoveEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {
        if (mActivePointerId != pointerId) {
            return
        }
        val hasOldKey = (mCurrentKey != null)
        mCurrentKey = detectKey(x, y)
        if (hasOldKey && mCurrentKey == null) {
            // A popup keys keyboard is canceled when detecting no key.
            mController.onCancelPopupKeysPanel()
        }
    }

    override fun onUpEvent(x: Int, y: Int, pointerId: Int, eventTime: Long) {
        if (mActivePointerId != pointerId) {
            return
        }
        // Calling detectKey here is harmless because the last move event and
        // the following up event share the same coordinates.
        mCurrentKey = detectKey(x, y)
        val currentKey = mCurrentKey
        if (currentKey != null) {
            updateReleaseKeyGraphics(currentKey)
            onKeyInput(currentKey, x, y)
            mCurrentKey = null
        }
    }

    /**
     * Performs the specific action for this panel when the user presses a key on the panel.
     */
    protected open fun onKeyInput(key: Key, x: Int, y: Int) {
        val listener = mListener
        if (listener != null) {
            val code = key.code
            val isSwipeShortcut = mSwipeShortcutDirection != null
            if (isSwipeShortcut) listener.onPressKey(code, 0, true, HapticEvent.NO_HAPTICS)
            if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                listener.onTextInput(key.outputText)
            } else if (code != KeyCode.NOT_SPECIFIED) {
                if (keyboard?.hasProximityCharsCorrection(code) == true) {
                    listener.onCodeInput(code, x, y, false /* isKeyRepeat */)
                } else {
                    listener.onCodeInput(
                        code,
                        Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE,
                        false /* isKeyRepeat */
                    )
                }
            }
            if (isSwipeShortcut) listener.onReleaseKey(code, false)
        } else {
            mEmojiViewCallback?.onReleaseKey(key)
        }
    }

    private fun detectKey(x: Int, y: Int): Key? {
        val oldKey = mCurrentKey
        val newKey = if (mSwipeShortcutDirection == null) mKeyDetector.detectHitKey(x, y)
            else detectSwipeShortcut(x, y)
        if (newKey === oldKey) {
            return newKey
        }
        // A new key is detected.
        if (oldKey != null) {
            updateReleaseKeyGraphics(oldKey)
            invalidateKey(oldKey)
        }
        if (newKey != null) {
            updatePressKeyGraphics(newKey)
            invalidateKey(newKey)
        }
        return newKey
    }

    private fun detectSwipeShortcut(x: Int, y: Int): Key? {
        val keyboard = keyboard ?: return null
        val touchYInParent = y + mOriginYInParent
        val outward = if (mSwipeShortcutDirection == SwipeShortcutMenu.Direction.UP)
            touchYInParent < mSwipeStartYInParent else touchYInParent > mSwipeStartYInParent
        val touchXInKeyboard = mKeyDetector.getTouchX(x)
        if (!outward || touchXInKeyboard < 0 || touchXInKeyboard >= keyboard.mOccupiedWidth) return null
        // Project onto the menu row so a short outward swipe already selects its aligned item.
        return keyboard.sortedKeys.minByOrNull { abs(touchXInKeyboard - (it.x + it.width / 2)) }
            ?.takeIf { it.isEnabled && !it.isSpacer }
    }

    private fun updateReleaseKeyGraphics(key: Key) {
        key.onReleased()
        invalidateKey(key)
    }

    private fun updatePressKeyGraphics(key: Key) {
        key.onPressed()
        invalidateKey(key)
    }

    override fun dismissPopupKeysPanel() {
        if (!isShowingInParent) {
            return
        }
        val accessibilityDelegate = mAccessibilityDelegate
        if (accessibilityDelegate != null && AccessibilityUtils.instance.isAccessibilityEnabled) {
            accessibilityDelegate.onDismissPopupKeysKeyboard()
        }
        mController.onDismissPopupKeysPanel()
    }

    override fun translateX(x: Int): Int {
        return x - mOriginXInParent
    }

    override fun translateY(y: Int): Int {
        return y - mOriginYInParent
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.actionMasked
        val eventTime = me.eventTime
        val index = me.actionIndex
        val x = me.getX(index).toInt()
        val y = me.getY(index).toInt()
        val pointerId = me.getPointerId(index)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                onDownEvent(x, y, pointerId, eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                onUpEvent(x, y, pointerId, eventTime)
            MotionEvent.ACTION_MOVE ->
                onMoveEvent(x, y, pointerId, eventTime)
        }
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val accessibilityDelegate = mAccessibilityDelegate
        if (accessibilityDelegate != null && AccessibilityUtils.instance.isTouchExplorationEnabled) {
            return accessibilityDelegate.onHoverEvent(event)
        }
        return super.onHoverEvent(event)
    }
}
