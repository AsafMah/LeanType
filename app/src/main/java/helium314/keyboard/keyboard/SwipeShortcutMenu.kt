// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.Context
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import kotlin.math.floor
import kotlin.math.roundToInt

/** Row geometry belongs to the keyboard, not to the key which opens the menu. */
object SwipeShortcutMenu {
    enum class Direction(val layoutType: LayoutType) {
        UP(LayoutType.SWIPE_UP), DOWN(LayoutType.SWIPE_DOWN)
    }

    class Row(val keys: List<Key>) {
        val left = keys.first().x
        val right = keys.last().x + keys.last().width
        val top = keys.minOf { it.y }
        val bottom = keys.maxOf { it.y + it.height }

        fun centers(count: Int): List<Int> {
            require(count > 0 && count <= right - left) { "Too many swipe shortcuts for this row" }
            return List(count) { index ->
                val position = if (count == 1) keys.lastIndex / 2f
                    else index.toFloat() * keys.lastIndex / (count - 1)
                val before = floor(position).toInt()
                val after = (before + 1).coerceAtMost(keys.lastIndex)
                val start = keys[before].x + keys[before].width / 2
                val end = keys[after].x + keys[after].width / 2
                (start + (end - start) * (position - before)).roundToInt()
            }
        }
    }

    fun findRows(keyboard: Keyboard): Map<Direction, Row> {
        // Custom menus may contain clipboard history or private text; mirror locked toolbar behavior.
        if (!keyboard.mId.isAlphabetKeyboard || keyboard.mId.mDeviceLocked) return emptyMap()
        // The space/punctuation row is functional; "." must keep its own long-press menu.
        val spaceY = keyboard.getKey(Constants.CODE_SPACE)?.y ?: Int.MAX_VALUE
        val characters = keyboard.sortedKeys.filter {
            it.y < spaceY && it.isEnabled && !it.isSpacer && !it.isModifier()
                && it.backgroundType == Key.BACKGROUND_TYPE_NORMAL
                && (it.code > Constants.CODE_SPACE || !it.outputText.isNullOrEmpty())
        }
        if (characters.isEmpty()) return emptyMap()
        val top = characters.minOf { it.y }
        val bottom = characters.maxOf { it.y }
        return mapOf(
            Direction.UP to Row(characters.filter { it.y == top }.sortedBy { it.x }),
            Direction.DOWN to Row(characters.filter { it.y == bottom }.sortedBy { it.x })
        )
    }

    class Builder(
        context: Context,
        parent: Keyboard,
        private val row: Row,
        private val direction: Direction
    ) : KeyboardBuilder<PopupKeysKeyboard.PopupKeysKeyboardParams>(
        context, PopupKeysKeyboard.PopupKeysKeyboardParams()
    ) {
        init {
            mParams.mId = parent.mId
            readAttributes(parent.mPopupKeysTemplate)
            mParams.mLeftPadding = 0
            mParams.mRightPadding = 0
            mParams.mTopPadding = 0
            mParams.mBottomPadding = 0
            mParams.mHorizontalGap = 0
            mParams.mVerticalGap = 0
            mParams.mRelativeHorizontalGap = 0f
            mParams.mRelativeVerticalGap = 0f
            mParams.mOccupiedWidth = row.right - row.left
            mParams.mBaseWidth = mParams.mOccupiedWidth
            mParams.mOccupiedHeight = row.bottom - row.top
            mParams.mBaseHeight = mParams.mOccupiedHeight
            mParams.mDefaultRowHeight = 1f
            addLocaleKeyTextsToParams(context, mParams, Settings.getValues().mShowMorePopupKeys)
        }

        override fun build(): PopupKeysKeyboard {
            val data = LayoutParser.parseLayout(direction.layoutType, mParams, mContext).flatten()
            require(data.isNotEmpty()) { "Swipe shortcut layout is empty" }
            val centers = row.centers(data.size)
            val spacing = centers.zipWithNext { a, b -> b - a }.minOrNull()
                ?: mParams.mBaseWidth
            require(spacing > 0) { "Too many swipe shortcuts for this row" }
            val width = minOf(row.keys.minOf { it.width }, spacing)
            data.forEachIndexed { index, keyData ->
                val keyParams = keyData.toKeyParams(mParams, Key.LABEL_FLAGS_AUTO_X_SCALE)
                keyParams.xPos = (centers[index] - row.left - width / 2).toFloat()
                keyParams.yPos = 0f
                keyParams.mAbsoluteWidth = width.toFloat()
                keyParams.mAbsoluteHeight = mParams.mBaseHeight.toFloat()
                mParams.onAddKey(keyParams.createKey())
            }
            return PopupKeysKeyboard(mParams)
        }
    }
}
