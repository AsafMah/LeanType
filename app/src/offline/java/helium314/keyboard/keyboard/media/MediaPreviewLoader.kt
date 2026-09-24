// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.graphics.drawable.Animatable
import android.widget.ImageView
import androidx.annotation.MainThread

/** Offline never constructs a decoder, cache, or network source. */
@MainThread
@Suppress("UNUSED_PARAMETER")
class MediaPreviewLoader(context: Context, source: MediaSource) : AutoCloseable {
    fun setAnimationsEnabled(enabled: Boolean) = Unit
    fun bind(view: ImageView, item: MediaItem, onError: (MediaError) -> Unit) {
        clear(view)
        onError(MediaError.UNAVAILABLE)
    }

    fun clear(view: ImageView) {
        (view.drawable as? Animatable)?.stop()
        view.setImageDrawable(null)
    }

    fun pause() = Unit
    fun resume() = Unit
    override fun close() = Unit
}
