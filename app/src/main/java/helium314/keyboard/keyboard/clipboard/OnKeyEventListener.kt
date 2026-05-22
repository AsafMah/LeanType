// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.view.View

interface OnKeyEventListener {

    fun onKeyDown(clipId: Long)

    fun onKeyUp(clipId: Long)

    /**
     * Called when a clipboard entry is long-pressed. Implementations should show a
     * context menu for the entry (edit, pin, copy, share, delete, ...).
     * Returns true if the long-press was consumed.
     * Default implementation falls back to the legacy "toggle pin" behaviour so this
     * is a non-breaking addition.
     */
    fun onKeyLongPress(clipId: Long, anchor: View): Boolean = false
}