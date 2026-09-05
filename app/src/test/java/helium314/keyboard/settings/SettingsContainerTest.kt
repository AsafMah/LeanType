package helium314.keyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun twoThumbGestureGatedAutospaceSettingIsRegistered() {
        assertEquals(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE,
            container[Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE]?.key)
    }

    @Test
    fun spacingDeferGraceSpaceSettingIsRegistered() {
        assertEquals(Settings.PREF_SPACING_DEFER_GRACE_SPACE,
            container[Settings.PREF_SPACING_DEFER_GRACE_SPACE]?.key)
    }

    @Test
    fun combiningGraceOnlyAfterGestureSettingIsRegistered() {
        assertEquals(Settings.PREF_COMBINING_GRACE_ONLY_AFTER_GESTURE,
            container[Settings.PREF_COMBINING_GRACE_ONLY_AFTER_GESTURE]?.key)
    }

    @Test
    fun twoThumbLowLevelBackspaceSettingIsHiddenFromSearchRegistry() {
        assertNull(container[Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD])
    }

    @Test
    fun twoThumbAdvancedTogglesAreRegistered() {
        assertEquals(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT,
            container[Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT]?.key)
        assertEquals(Settings.PREF_MULTIPART_FULL_WORD_SUGGESTIONS,
            container[Settings.PREF_MULTIPART_FULL_WORD_SUGGESTIONS]?.key)
        assertEquals(Settings.PREF_GESTURE_DEBUG_ACCUMULATE_FRAGMENTS,
            container[Settings.PREF_GESTURE_DEBUG_ACCUMULATE_FRAGMENTS]?.key)
    }

    @Test
    fun twoThumbFragmentBackspaceLabelMatchesBehavior() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Delete last fragment", context.getString(R.string.two_thumb_backspace_fragment))
    }

    @Test
    fun autospaceAfterEmojiSettingIsRegistered() {
        assertEquals(Settings.PREF_AUTOSPACE_AFTER_EMOJI,
            container[Settings.PREF_AUTOSPACE_AFTER_EMOJI]?.key)
    }

    @Test
    fun touchpadEdgeScrollSettingIsRegistered() {
        assertEquals(Settings.PREF_TOUCHPAD_EDGE_SCROLL,
            container[Settings.PREF_TOUCHPAD_EDGE_SCROLL]?.key)
    }

    @Test
    fun toolbarSwipeDownToHideSettingIsRegistered() {
        assertEquals(Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE,
            container[Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE]?.key)
    }

    @Test
    fun onlyToolbarWithHardwareKeyboardSettingIsRegistered() {
        assertEquals(Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD,
            container[Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD]?.key)
    }

    @Test
    fun upstreamMathOcrAndCloudSettingsAreRegistered() {
        val keys = listOf(
            Settings.PREF_INLINE_MATH_CALCULATION,
            Settings.PREF_CLOUD_AI_MAX_TOKENS,
            OcrPluginLoader.PREF_OCR_CASING,
            OcrPluginLoader.PREF_OCR_LINE_JOIN_FORMAT,
            OcrPluginLoader.PREF_OCR_KEEP_LINE_BREAKS,
            OcrPluginLoader.PREF_OCR_TRIM_WHITESPACE,
            OcrPluginLoader.PREF_OCR_DEHYPHENATE,
            OcrPluginLoader.PREF_OCR_NORMALIZE_PUNCTUATION,
            OcrPluginLoader.PREF_OCR_STRIP_BULLETS,
            OcrPluginLoader.PREF_OCR_REMOVE_NOISE,
            OcrPluginLoader.PREF_OCR_AUTO_COPY,
            OcrPluginLoader.PREF_OCR_AUTO_INSERT,
            OcrPluginLoader.PREF_OCR_SUGGEST_SCREENSHOT_TEXT,
            OcrPluginLoader.PREF_OCR_PERSIST_FLASH,
        )
        for (key in keys) {
            assertEquals(key, container[key]?.key)
            assertEquals("Duplicate setting: $key", 1, container.filter("").count { it.key == key })
        }
    }

    @Test
    fun soundControlsRemainSearchableAfterMovingToPlugins() {
        val keys = listOf(
            Settings.PREF_SOUND_ON, Settings.PREF_KEYPRESS_SOUND_STYLE,
            Settings.PREF_KEYPRESS_SOUND_VOLUME, Settings.PREF_SOUND_PITCH_SCALE,
            Settings.PREF_SOUND_RANDOM_PITCH, Settings.PREF_SOUND_STEREO_PAN,
            Settings.PREF_SOUND_DYNAMIC_VELOCITY, Settings.PREF_SOUND_MUTE_IN_SILENT,
            Settings.PREF_SOUND_MUTE_IN_DND, Settings.PREF_SOUND_VOL_SPACE,
            Settings.PREF_SOUND_VOL_DELETE, Settings.PREF_SOUND_VOL_ENTER,
            Settings.PREF_SOUND_VOL_MODIFIERS,
        )
        for (key in keys) {
            assertEquals(key, container[key]?.key)
            assertEquals("Duplicate setting: $key", 1, container.filter("").count { it.key == key })
        }
    }
}
