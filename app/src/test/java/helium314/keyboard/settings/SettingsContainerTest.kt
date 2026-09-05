package helium314.keyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.screens.createOcrSettings
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
    fun upstreamMathAndOcrSettingsAreRegistered() {
        val keys = listOf(
            Settings.PREF_INLINE_MATH_CALCULATION,
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

    @Test
    fun ocrRegistryAndSearchRespectBuildAndApiAvailability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keys = createOcrSettings(context).map { it.key } + SettingsWithoutKey.SCREEN_NAV_OCR
        for (flavor in listOf("standard", "standardfull", "offline", "offlinelite")) {
            for (buildType in listOf("debug", "nouserlib")) {
                for (sdk in listOf(25, 26, 33)) {
                    val candidate = SettingsContainer(context, SettingsAvailability(flavor, buildType, sdk))
                    val expected = buildType != "nouserlib" && sdk >= 26
                    for (key in keys) {
                        val label = "$flavor/$buildType/API$sdk/$key"
                        assertEquals(label, expected, candidate[key] != null)
                        assertEquals(label, if (expected) 1 else 0,
                            candidate.filter("").count { it.key == key })
                    }
                }
            }
        }
    }

    @Test
    fun cloudControlsAreOnlyRegisteredForCloudFlavors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keys = listOf(
            SettingsWithoutKey.GEMINI_API_KEY, SettingsWithoutKey.GEMINI_MODEL,
            SettingsWithoutKey.GEMINI_TARGET_LANGUAGE,
            SettingsWithoutKey.GROQ_TOKEN, SettingsWithoutKey.GROQ_MODEL,
            SettingsWithoutKey.HUGGINGFACE_TOKEN, SettingsWithoutKey.HUGGINGFACE_MODEL,
            SettingsWithoutKey.HUGGINGFACE_ENDPOINT, SettingsWithoutKey.AI_PROVIDER,
            SettingsWithoutKey.TRANSLATE_GEMINI_MODEL, SettingsWithoutKey.TRANSLATE_GROQ_MODEL,
            SettingsWithoutKey.TRANSLATE_HUGGINGFACE_MODEL,
            SettingsWithoutKey.AI_ALLOW_INSECURE_CONNECTIONS, SettingsWithoutKey.CLOUD_AI_MAX_TOKENS,
        )
        for (flavor in listOf("standard", "standardfull", "offline", "offlinelite")) {
            val candidate = SettingsContainer(context, SettingsAvailability(flavor = flavor))
            for (key in keys) {
                val expected = flavor == "standard" || flavor == "standardfull"
                assertEquals("$flavor/$key", expected, candidate[key] != null)
                assertEquals("$flavor/$key", if (expected) 1 else 0,
                    candidate.filter("").count { it.key == key })
            }
        }
    }

    @Test
    fun liteHasNoAiNavigationOrSharedAiControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidate = SettingsContainer(context, SettingsAvailability(flavor = "offlinelite"))
        val keys = listOf(
            SettingsWithoutKey.SCREEN_NAV_AI_INTEGRATION, SettingsWithoutKey.CUSTOM_AI_KEYS,
            SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, SettingsWithoutKey.TRANSLATION_ENGINE,
            SettingsWithoutKey.OFFLINE_MODEL_PATH, SettingsWithoutKey.OFFLINE_KEEP_MODEL_LOADED,
            SettingsWithoutKey.LOAD_OFFLINE_AI_PLUGIN,
        )
        for (key in keys) {
            assertNull(key, candidate[key])
            assertTrue(key, candidate.filter("").none { it.key == key })
        }
        assertEquals(Settings.PREF_INLINE_MATH_CALCULATION,
            candidate[Settings.PREF_INLINE_MATH_CALCULATION]?.key)
    }
}
