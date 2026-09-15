// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Activity
import android.app.Application
import android.os.Build
import android.widget.ImageView
import android.widget.LinearLayout
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23, 28])
class MediaPreviewLifecycleTest {
    @Test fun neverMoreThanTwoFetchesAndClearCancelsOffscreenWork() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        activity.setContentView(root)
        val views = List(10) { ImageView(activity).also { view -> root.addView(view, LinearLayout.LayoutParams(20, 20)) } }
        controller.visible().windowFocusChanged(true)
        // ActivityController attaches the window but its stub WindowManager leaves mAppVisible
        // false. Deliver the real framework callback instead of bypassing loader visibility checks.
        val viewRoot = ReflectionHelpers.callInstanceMethod<Any>(activity.window.decorView, "getViewRootImpl")
        ReflectionHelpers.callInstanceMethod<Void>(
            viewRoot, "handleAppVisibility", ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType, true)
        )
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        root.measure(exact(100), exact(300))
        root.layout(0, 0, 100, 300)
        assertTrue(views.first().isAttachedToWindow, "Fixture cell must have a real window attachment")
        assertTrue(views.first().isShown, "Fixture cell must be shown")
        assertTrue(views.first().getGlobalVisibleRect(android.graphics.Rect()), "Fixture cell must occupy visible window bounds")
        assertEquals(android.view.View.VISIBLE, views.first().windowVisibility, "Fixture window must be visible")
        val source = WaitingSource()
        val loader = MediaPreviewLoader(activity, source)
        try {
            views.forEach { loader.bind(it, OwnedMediaFixtures.item()) { error("Unexpected $it") } }
            root.viewTreeObserver.dispatchOnPreDraw()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(2, source.active)
            assertEquals(2, source.maximum)
            loader.pause()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, source.active)
            loader.resume()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(2, source.active)
            views.forEach(loader::clear)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(0, source.active)
            assertTrue(views.all { it.drawable == null })
        } finally {
            loader.close()
            activity.finish()
        }
    }

    @Test fun invisibleBindingsDoNotFetchAndOlderDevicesRequireGif() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val source = WaitingSource()
        val loader = MediaPreviewLoader(activity, source)
        try {
            val gif = OwnedMediaFixtures.rendition()
            val webp = gif.copy(mimeType = "image/webp")
            val candidates = OwnedMediaFixtures.item(listOf(webp, gif))
            assertEquals(if (Build.VERSION.SDK_INT < 28) gif else webp, loader.selectRendition(candidates))
            assertEquals(webp, loader.selectRendition(OwnedMediaFixtures.item(listOf(webp))))
            val detached = ImageView(activity)
            loader.bind(detached, candidates) { error("Detached cell must not load") }
            assertEquals(0, source.active)
            loader.clear(detached)
        } finally {
            loader.close()
            activity.finish()
        }
    }

    private fun exact(size: Int) = android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY)

    private class WaitingSource : MediaSource {
        var active = 0
        var maximum = 0
        override val available = true
        override val credentialVersion = 0L
        override fun hasApiKey() = true
        override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage = error("No search")
        override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray {
            assertEquals(MediaLimits.PREVIEW_BYTES, maxBytes)
            active++
            maximum = maxOf(maximum, active)
            try { awaitCancellation() } finally { active-- }
        }
    }
}
