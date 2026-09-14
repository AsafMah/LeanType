package helium314.keyboard.settings

import android.content.Context
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsContainerTest {

    private lateinit var container: SettingsContainer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = SettingsContainer(context)
    }

    @Test
    fun testFilterFunctionality() {
        // Just verify it doesn't crash and returns some results for empty or common strings
        val res = container.filter("a")
        assertTrue(res.isNotEmpty())
    }

    @Test
    fun swipeMenusHaveOneSettingAndOneSecondaryLayoutEach() {
        val keys = listOf(
            Settings.PREF_SWIPE_UP_MENU, Settings.PREF_SWIPE_DOWN_MENU,
            Settings.PREF_LAYOUT_PREFIX + LayoutType.SWIPE_UP.name,
            Settings.PREF_LAYOUT_PREFIX + LayoutType.SWIPE_DOWN.name
        )
        val settings = container.filter("")
        keys.forEach { key ->
            assertEquals(1, settings.count { it.key == key })
            assertTrue(container[key] != null)
        }
    }

    @Test
    fun testFilterPerformance() {
        val searches = listOf("a", "b", "c", "theme", "color", "sound", "vib", "dictionary", "key", "layout")

        // Warmup
        for (i in 1..10) {
            for (s in searches) {
                container.filter(s)
            }
        }

        val time = measureNanoTime {
            for (i in 1..100) {
                for (s in searches) {
                    container.filter(s)
                }
            }
        }
        println("Filter performance: ${time / 1_000_000} ms")
    }
}
