// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.content.res.Configuration
import helium314.keyboard.latin.utils.ResourceUtils

internal object PickerPaneGeometry {
    fun panelHeight(context: Context, keyboardHeight: Int, expanded: Boolean): Int {
        val metrics = context.resources.displayMetrics
        val constrained = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            ResourceUtils.getFloatingKeyboardWidth() > 0
        val desired = ((if (expanded && !constrained) 300 else 180) * metrics.density).toInt()
        // Reserve space for the app and the IME's outer strip. Never shrink the retained keyboard.
        val available = (metrics.heightPixels - keyboardHeight - (144 * metrics.density).toInt())
            .coerceAtLeast((96 * metrics.density).toInt())
        return minOf(desired, available)
    }
}
