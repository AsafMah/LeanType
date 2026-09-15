// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Application
import android.widget.ImageView
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [23])
class MediaPreviewOfflineTest {
    @Test fun wrongUiStateFailsUnavailableWithoutConsultingSource() {
        val context = RuntimeEnvironment.getApplication()
        val source = Mockito.mock(MediaSource::class.java)
        val loader = MediaPreviewLoader(context, source)
        val view = ImageView(context)
        var reason: MediaError? = null
        loader.bind(view, OwnedMediaFixtures.item()) { reason = it }
        loader.pause()
        loader.resume()
        loader.clear(view)
        loader.close()
        assertEquals(MediaError.UNAVAILABLE, reason)
        assertNull(view.drawable)
        Mockito.verifyNoInteractions(source)
    }
}
