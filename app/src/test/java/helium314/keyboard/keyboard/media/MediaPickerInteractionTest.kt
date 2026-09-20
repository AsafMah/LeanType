// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.app.Activity
import android.content.res.AssetManager
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.compat.AppQuirk
import helium314.keyboard.compat.AppQuirksManager
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [
    ShadowInputMethodManager2::class, ShadowLocaleManagerCompat::class, ShadowProximityInfo::class
])
class MediaPickerInteractionTest {
    private lateinit var activity: Activity
    private lateinit var ime: LatinIME
    private lateinit var picker: MediaPickerView
    private lateinit var source: Source
    private lateinit var host: Host
    private var failShare = false

    @Before fun setUp() {
        ime = Robolectric.setupService(LatinIME::class.java)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(KeyboardTheme.getKeyboardTheme(ime).mStyleId)
        source = Source()
        host = Host()
        val assets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(assets.list(Mockito.anyString())).thenAnswer {
            ime.assets.list(it.getArgument<String>(0).replace('\\', '/'))
        }
        Mockito.`when`(assets.open(Mockito.anyString())).thenAnswer {
            ime.assets.open(it.getArgument<String>(0).replace('\\', '/'))
        }
        val context = object : ContextThemeWrapper(activity, KeyboardTheme.getKeyboardTheme(ime).mStyleId) {
            override fun getAssets() = assets
            override fun startActivity(intent: android.content.Intent) {
                if (failShare) throw android.content.ActivityNotFoundException("Fixture has no chooser")
                super.startActivity(intent)
            }
        }
        picker = MediaPickerView(context, host, source)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
    }

    @After fun tearDown() {
        picker.stop()
        activity.finish()
        ime.onDestroy()
    }

    @Test fun typingAndTabChangesDoNotSearchButSubmitDoes() {
        val field = picker.findViewById<EditText>(R.id.media_query)
        field.setText("cat")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(source.requests.isEmpty())
        picker.open(MediaKind.STICKER)
        assertEquals("", field.text.toString())
        assertTrue(source.requests.isEmpty())
        field.setText("party")
        picker.findViewById<View>(R.id.media_submit).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(MediaKind.STICKER to "party"), source.requests)
        assertEquals(activity.getString(R.string.media_no_results),
            picker.findViewById<TextView>(R.id.media_status).text.toString())
    }

    @Test fun privateModeCannotSubmitAndBackReturnsToTyping() {
        host.privateMode = true
        picker.findViewById<EditText>(R.id.media_query).setText("must remain local")
        picker.findViewById<View>(R.id.media_submit).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(source.requests.isEmpty())
        assertTrue(picker.onBack())
        assertEquals(1, host.returns)
    }

    @Test fun appProfileIncognitoBlocksActualPickerSubmissionUntilRemoved() {
        val editor = EditorInfo().apply {
            packageName = "media.picker.privacy.fixture"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        host.editor = editor
        try {
            AppQuirksManager.saveQuirk(AppQuirk(editor.packageName, forceIncognito = true))
            val field = picker.findViewById<EditText>(R.id.media_query)
            field.setText("must remain local")
            picker.findViewById<View>(R.id.media_submit).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(source.requests.isEmpty())
            assertEquals(activity.getString(R.string.media_unavailable),
                picker.findViewById<TextView>(R.id.media_status).text.toString())

            AppQuirksManager.removeQuirk(editor.packageName)
            field.setText("public")
            picker.findViewById<View>(R.id.media_submit).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(MediaKind.GIF to "public"), source.requests)
        } finally {
            AppQuirksManager.removeQuirk(editor.packageName)
        }
    }

    @Test fun staleEditorCannotSubmit() {
        host.editorVersion++
        picker.findViewById<EditText>(R.id.media_query).setText("old editor")
        picker.findViewById<View>(R.id.media_submit).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(source.requests.isEmpty())
    }

    @Test fun missingKeyReportsSetupWithoutNetwork() {
        source.hasKey = false
        picker.open(MediaKind.GIF)
        picker.findViewById<EditText>(R.id.media_query).setText("cat")
        picker.findViewById<View>(R.id.media_submit).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(source.requests.isEmpty())
        assertEquals(activity.getString(R.string.media_missing_key),
            picker.findViewById<TextView>(R.id.media_status).text.toString())
    }

    @Test fun realTouchRoutingReacquiresQueryListenerAndRestoresHost() {
        // Reproduce the palette bottom-row setup replacing PointerTracker's global listener.
        PointerTracker.setKeyboardActionListener(host.typingListener)
        picker.findViewById<EditText>(R.id.media_query).performClick()
        val keyboard = picker.findViewById<MainKeyboardView>(R.id.media_keyboard)
        val key = requireNotNull(keyboard.keyboard?.getKey('a'.code))
        keyboard.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        keyboard.layout(0, 0, keyboard.measuredWidth, keyboard.measuredHeight)
        val x = (key.x + key.width / 2 + keyboard.paddingLeft).toFloat()
        val y = (key.y + key.height / 2 + keyboard.paddingTop).toFloat()
        val now = android.os.SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 20)) {
            val event = MotionEvent.obtain(now, time, action, x, y, 0)
            keyboard.onTouchEvent(event)
            event.recycle()
        }
        assertEquals("a", picker.findViewById<EditText>(R.id.media_query).text.toString())
        Mockito.verifyNoInteractions(host.typingListener)
        picker.onBack()
        assertSame(host.typingListener,
            PointerTracker::class.java.getDeclaredField("sListener").apply { isAccessible = true }.get(null))
    }

    @Test fun throwingReceiverRetainsResultsAndOffersExplicitShare() {
        host.throwOnInsert = true
        picker.insertStagedMedia(StagedMedia(android.net.Uri.parse("content://fixture/media"), "image/gif", "Owned fixture"))
        assertEquals(0, host.returns)
        assertEquals(View.VISIBLE, picker.findViewById<View>(R.id.media_share).visibility)
        assertEquals(activity.getString(R.string.media_unsupported),
            picker.findViewById<TextView>(R.id.media_status).text.toString())
    }

    @Test fun queryKeyboardFitsViewportAndClearKeepsEditing() {
        val field = picker.findViewById<EditText>(R.id.media_query)
        field.performClick()
        field.setText("cat")
        field.setSelection(1)
        field.performClick()
        assertEquals(1, field.selectionStart)
        val width = helium314.keyboard.latin.utils.ResourceUtils.getKeyboardWidth(
            picker.context, helium314.keyboard.latin.settings.Settings.getValues())
        val height = (240 * picker.resources.displayMetrics.density).toInt()
        picker.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
        val keyboard = picker.findViewById<MainKeyboardView>(R.id.media_keyboard)
        assertTrue(keyboard.bottom <= picker.measuredHeight, "Query keyboard must not extend below the IME viewport")
        picker.findViewById<View>(R.id.media_clear).performClick()
        assertEquals("", field.text.toString())
        assertEquals(View.VISIBLE, keyboard.visibility)
        assertTrue(source.requests.isEmpty())
        picker.onBack()
        assertEquals(View.GONE, keyboard.visibility)
        assertEquals(0, host.returns)
        picker.onBack()
        assertEquals(1, host.returns)
    }

    @Test fun unavailableChooserReportsErrorWithoutLeavingPicker() {
        host.throwOnInsert = true
        picker.insertStagedMedia(StagedMedia(android.net.Uri.parse("content://fixture/media"), "image/gif", "Owned fixture"))
        failShare = true
        picker.findViewById<View>(R.id.media_share).performClick()
        assertEquals(0, host.returns)
        assertEquals(activity.getString(R.string.media_share_error),
            picker.findViewById<TextView>(R.id.media_status).text.toString())
    }

    private class Host : MediaPickerHost {
        override var editorVersion = 1L
        var editor: EditorInfo? = null
        override var privateMode = false
            get() = field || editor?.let { MediaPrivacy.isRestricted(false, it) } == true
        override val typingListener = Mockito.mock(KeyboardActionListener::class.java)
        var returns = 0
        var throwOnInsert = false
        override fun supports(mimeType: String) = true
        override fun insert(media: StagedMedia, editorVersion: Long): Boolean {
            if (throwOnInsert) throw IllegalStateException("Receiver fixture failure")
            return true
        }
        override fun returnToTyping() { returns++ }
    }

    private class Source : MediaSource {
        override val available = true
        override var credentialVersion = 0L
        var hasKey = true
        val requests = mutableListOf<Pair<MediaKind, String>>()
        override fun hasApiKey() = hasKey
        override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage {
            requests.add(kind to query)
            return MediaPage(emptyList(), null)
        }
        override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray =
            error("No download expected")
    }
}
