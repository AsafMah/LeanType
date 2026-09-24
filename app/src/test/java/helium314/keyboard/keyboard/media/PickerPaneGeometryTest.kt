// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Application
import android.content.res.Configuration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PickerPaneGeometryTest {
    @Test fun constrainedPaneAlwaysLeavesResultsBelowFixedControls() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        val density = context.resources.displayMetrics.density
        val panel = PickerPaneGeometry.panelHeight(context, Int.MAX_VALUE / 2, true)
        // Media's fixed-control allowance is independent of this minimum content viewport.
        assertTrue(panel >= (96 * density).toInt())
    }

    @Test fun expandedPortraitIsNotSmallerThanCompact() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        assertTrue(PickerPaneGeometry.panelHeight(context, 200, true) >=
            PickerPaneGeometry.panelHeight(context, 200, false))
    }
}
