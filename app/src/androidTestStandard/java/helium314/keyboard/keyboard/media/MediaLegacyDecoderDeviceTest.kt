// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Animatable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asDrawable
import coil3.decode.ImageSource
import coil3.gif.GifDecoder
import coil3.request.Options
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android Movie needs real platform JNI; Robolectric's Movie bridge cannot decode this fixture. */
@RunWith(AndroidJUnit4::class)
class MediaLegacyDecoderDeviceTest {
    @Test fun legacyGifDecoderRendersDifferentFramesAndPreservesTransparency() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = ImageSource(Buffer().write(OwnedMediaFixtures.gif()), FileSystem.SYSTEM)
        val drawable = GifDecoder(source, Options(context)).decode().image.asDrawable(context.resources)
        val animation = drawable as Animatable
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun pixels(): IntArray {
            bitmap.eraseColor(Color.TRANSPARENT)
            drawable.draw(canvas)
            return IntArray(2).also { bitmap.getPixels(it, 0, 2, 0, 0, 2, 1) }
        }
        try {
            drawable.setBounds(0, 0, 2, 1)
            animation.start()
            assertTrue(animation.isRunning)
            val first = pixels()
            var second = first
            for (attempt in 0 until 40) {
                Thread.sleep(25)
                second = pixels()
                if (second[0] != first[0]) break
            }
            assertNotEquals(first[0], second[0])
            assertEquals(setOf(Color.RED, Color.BLUE), setOf(first[0], second[0]))
            assertEquals(Color.TRANSPARENT, first[1])
            assertEquals(Color.TRANSPARENT, second[1])
        } finally {
            animation.stop()
            bitmap.recycle()
        }
        assertFalse(animation.isRunning)
    }
}
