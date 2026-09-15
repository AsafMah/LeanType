// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import coil3.asDrawable
import coil3.decode.ImageSource
import coil3.gif.AnimatedImageDecoder
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Native graphics are required: a Robolectric placeholder bitmap is not animation evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaAnimationTest {
    @Test fun singleFrameStickerRendersItsPixelsAndTransparency() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bytes = OwnedMediaFixtures.gif(frames = 1)
        assertEquals(1, MediaFormat.inspect(bytes, OwnedMediaFixtures.rendition(bytes), true).frames)
        val source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM)
        val drawable = AnimatedImageDecoder(source, Options(context)).decode().image.asDrawable(context.resources)
        drawable.setBounds(0, 0, 2, 1)
        val pixels = pixels(drawable)
        assertEquals(Color.RED, pixels[0])
        assertEquals(Color.TRANSPARENT, pixels[1])
    }

    @Test fun modernGifDecoderProducesAnAnimationNotAStaticFallback() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val source = ImageSource(Buffer().write(OwnedMediaFixtures.gif()), FileSystem.SYSTEM)
        val drawable = AnimatedImageDecoder(source, Options(context)).decode().image.asDrawable(context.resources)
        val animation = drawable as Animatable
        drawable.setBounds(0, 0, 2, 1)
        animation.start()
        assertTrue(animation.isRunning)
        val first = pixels(drawable)
        assertEquals(Color.RED, first[0])
        assertEquals(Color.TRANSPARENT, first[1])
        var second = first
        // Native Skia animation time is independent of Robolectric's Java uptime clock.
        for (attempt in 0 until 20) {
            Thread.sleep(25)
            ShadowSystemClock.advanceBy(Duration.ofMillis(25))
            second = pixels(drawable)
            if (second[0] != first[0]) break
        }
        assertEquals(Color.BLUE, second[0])
        assertEquals(Color.TRANSPARENT, second[1])
        animation.stop()
        assertFalse(animation.isRunning)
    }

    @Test fun animatedWebpPreservesFrameChangesAndAlpha() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bytes = OwnedMediaFixtures.animatedWebp()
        val rendition = OwnedMediaFixtures.rendition(bytes, "image/webp", width = 3)
        assertEquals(2, MediaFormat.inspect(bytes, rendition, true).frames)
        val source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM)
        val drawable = AnimatedImageDecoder(source, Options(context)).decode().image.asDrawable(context.resources)
        val animation = drawable as Animatable
        drawable.setBounds(0, 0, 3, 1)
        animation.start()
        val first = pixels(drawable, 3)
        var second = first
        for (attempt in 0 until 20) {
            Thread.sleep(25)
            ShadowSystemClock.advanceBy(Duration.ofMillis(25))
            second = pixels(drawable, 3)
            if (second[0] != first[0]) break
        }
        assertEquals(setOf(Color.RED, Color.BLUE), setOf(first[0], second[0]))
        assertEquals(Color.TRANSPARENT, first[1])
        assertEquals(Color.TRANSPARENT, second[1])
        animation.stop()
        assertFalse(animation.isRunning)
    }

    private fun pixels(drawable: Drawable, width: Int = 2): IntArray {
        val bitmap = Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888)
        return try {
            drawable.draw(Canvas(bitmap))
            IntArray(width).also { bitmap.getPixels(it, 0, width, 0, 0, width, 1) }
        } finally {
            bitmap.recycle()
        }
    }
}
