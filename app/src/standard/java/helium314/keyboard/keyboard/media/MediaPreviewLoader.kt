// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.annotation.MainThread
import coil3.ImageLoader
import coil3.asDrawable
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Bind attached cells; clear detached/recycled cells; pause hidden picker panels; close dismissed sessions.
 * All public methods are main-thread only. No URLs are passed to Coil and no image caches are installed.
 */
@MainThread
class MediaPreviewLoader(context: Context, private val source: MediaSource) : AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = Semaphore(2)
    private val bindings = mutableMapOf<ImageView, Binding>()
    private val loaderDelegate = lazy {
        ImageLoader.Builder(this.context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .memoryCache { null }
            .diskCache { null }
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    private val loader by loaderDelegate
    private var paused = false
    private var closed = false
    private var animationsEnabled = true

    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        bindings.values.forEach {
            val animation = it.view.drawable as? Animatable
            if (enabled && it.visible()) animation?.start() else animation?.stop()
        }
    }
    private var reservedBytes = 0L

    fun bind(view: ImageView, item: MediaItem, onError: (MediaError) -> Unit) {
        clear(view)
        if (closed || !source.available) {
            onError(MediaError.UNAVAILABLE)
            return
        }
        val binding = Binding(view, item, onError)
        bindings[view] = binding
        binding.observer = view.viewTreeObserver
        binding.observer?.addOnPreDrawListener(binding)
        view.addOnAttachStateChangeListener(binding)
        binding.update()
    }

    fun clear(view: ImageView) {
        bindings.remove(view)?.let { binding ->
            binding.observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(binding)
            view.removeOnAttachStateChangeListener(binding)
            release(binding)
        }
        (view.drawable as? Animatable)?.stop()
        view.setImageDrawable(null)
    }

    fun pause() {
        paused = true
        bindings.values.forEach(::release)
    }

    fun resume() {
        if (closed) return
        paused = false
        bindings.values.toList().forEach { it.update() }
    }

    override fun close() {
        if (closed) return
        closed = true
        bindings.keys.toList().forEach(::clear)
        scope.cancel()
        // Do not initialize Coil just to tear down a picker that never requested a thumbnail.
        if (loaderDelegate.isInitialized()) loader.shutdown()
    }

    private fun release(binding: Binding) {
        binding.job?.cancel()
        binding.job = null
        (binding.view.drawable as? Animatable)?.stop()
        binding.view.setImageDrawable(null)
        reservedBytes -= binding.reservation
        binding.reservation = 0
    }

    private fun load(binding: Binding) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var reservation = 0L
            try {
                jobs.withPermit {
                    val rendition = selectRendition(binding.item) ?: throw MediaException(MediaError.UNSUPPORTED)
                    val version = source.credentialVersion
                    val bytes = source.download(rendition, MediaLimits.PREVIEW_BYTES)
                    val format = withContext(Dispatchers.Default) {
                        MediaFormat.inspect(bytes, rendition, preview = true)
                    }
                    if (Build.VERSION.SDK_INT < 28 && format.mimeType == "image/webp" && format.frames > 1)
                        throw MediaException(MediaError.UNSUPPORTED)
                    coroutineContext.ensureActive()
                    val estimate = format.frameMemoryBytes + bytes.size.toLong() * 2
                    if (estimate > MAX_PREVIEW_MEMORY || reservedBytes > MAX_VISIBLE_MEMORY - estimate) {
                        throw MediaException(MediaError.TOO_LARGE)
                    }
                    reservedBytes += estimate
                    reservation = estimate
                    val drawable = decode(bytes, rendition)
                    coroutineContext.ensureActive()
                    if (version != source.credentialVersion || bindings[binding.view] !== binding || !binding.visible()) {
                        throw CancellationException()
                    }
                    if (format.frames > 1 && drawable !is Animatable) throw MediaException(MediaError.UNSUPPORTED)
                    binding.view.setImageDrawable(drawable)
                    binding.reservation = reservation
                    reservation = 0
                    if (format.frames > 1 && animationsEnabled) (drawable as? Animatable)?.start()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (bindings[binding.view] === binding && binding.visible()) {
                    binding.failed = true
                    binding.onError((error as? MediaException)?.reason ?: MediaError.INVALID_RESPONSE)
                }
            } finally {
                reservedBytes -= reservation
                if (binding.job === coroutineContext[Job]) binding.job = null
            }
        }
        binding.job = job
        job.start()
    }

    internal fun selectRendition(item: MediaItem): MediaRendition? =
        item.renditions.filter {
            it.width in 1..MediaLimits.PREVIEW_DIMENSION && it.height in 1..MediaLimits.PREVIEW_DIMENSION &&
                (it.byteSize == null || it.byteSize in 1..MediaLimits.PREVIEW_BYTES.toLong()) &&
                it.mimeType in setOf("image/gif", "image/webp")
        }.sortedWith(compareBy<MediaRendition> {
            if (Build.VERSION.SDK_INT < 28) it.mimeType != "image/gif" else !it.preview
        }.thenBy { !it.preview }).firstOrNull()

    internal suspend fun decode(bytes: ByteArray, rendition: MediaRendition): Drawable {
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .size(rendition.width, rendition.height)
            .build()
        val result = loader.execute(request)
        return (result as? SuccessResult)?.image?.asDrawable(context.resources)
            ?: throw MediaException(MediaError.INVALID_RESPONSE)
    }

    private inner class Binding(
        val view: ImageView, val item: MediaItem, val onError: (MediaError) -> Unit
    ) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        var job: Job? = null
        var observer: ViewTreeObserver? = null
        var failed = false
        var reservation = 0L
        private val bounds = Rect()

        fun visible(): Boolean = !closed && !paused && view.isAttachedToWindow &&
            view.windowVisibility == View.VISIBLE && view.isShown && view.getGlobalVisibleRect(bounds)

        fun update() {
            if (!visible()) {
                release(this)
            } else if (!failed && job == null && view.drawable == null) {
                load(this)
            }
        }

        override fun onPreDraw(): Boolean {
            update()
            return true
        }

        override fun onViewAttachedToWindow(view: View) {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = view.viewTreeObserver
            observer?.addOnPreDrawListener(this)
            update()
        }

        override fun onViewDetachedFromWindow(view: View) = clear(this.view)
    }

    companion object {
        // Admission estimates include all composited frames; actual native allocations still need
        // on-device profiling. Oversubscription is a visible failure, never a static-image fallback.
        internal const val MAX_PREVIEW_MEMORY = 32 * 1024 * 1024L
        internal const val MAX_VISIBLE_MEMORY = 64 * 1024 * 1024L
    }
}
