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
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [
    ShadowInputMethodManager2::class, ShadowLocaleManagerCompat::class, ShadowProximityInfo::class,
    HostAtomicFile::class
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

    @Test fun repeatedNetworkLossClearsNewlyEnteredQueriesEachTime() {
        val field = picker.findViewById<EditText>(R.id.media_query)
        repeat(2) {
            host.networkAvailable = true
            picker.viewTreeObserver.dispatchOnPreDraw()
            field.setText("sensitive-$it")
            host.networkAvailable = false
            picker.viewTreeObserver.dispatchOnPreDraw()
            assertEquals("", field.text.toString())
        }
        assertTrue(source.requests.isEmpty())
    }

    @Test fun hardwareKeysInHostModeAreNotCapturedAsQueries() {
        assertTrue(picker.isTypingInApp)
        val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_A)
        kotlin.test.assertFalse(picker.onHardwareKey(event.keyCode, event))
        assertEquals("", picker.findViewById<EditText>(R.id.media_query).text.toString())
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
        assertQueryTouchRouting()
    }

    @Test fun literalModeKeepsSeparateQueryTypingAndExplicitMediaSubmission() {
        val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        org.robolectric.util.ReflectionHelpers.setField(ime, "mInputEditorInfo", editor)
        ime.keyboardActionListener.onCodeInput(
            helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.TOGGLE_LITERAL_MODE, 0, 0, false
        )
        assertTrue(ime.isLiteralMode)
        assertQueryTouchRouting()
        assertTrue(source.requests.isEmpty())
        picker.findViewById<View>(R.id.media_submit).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(MediaKind.GIF to "a"), source.requests)
        assertTrue(ime.isLiteralMode)
        Mockito.verifyNoInteractions(host.typingListener)
    }

    private fun assertQueryTouchRouting() {
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
        assertEquals(View.VISIBLE, keyboard.visibility)
        assertEquals(0, host.returns)
        picker.onBack()
        assertEquals(1, host.returns)
    }

    @Test fun resultsStayAboveFullKeyboardInBothFocusModes() {
        val grid = picker.findViewById<View>(R.id.media_results)
        val keyboard = picker.findViewById<MainKeyboardView>(R.id.media_keyboard)
        val baseHeight = helium314.keyboard.latin.utils.ResourceUtils.getKeyboardHeight(
            picker.resources, helium314.keyboard.latin.settings.Settings.getValues())
        val width = helium314.keyboard.latin.utils.ResourceUtils.getKeyboardWidth(
            picker.context, helium314.keyboard.latin.settings.Settings.getValues())
        fun measure() {
            picker.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
        }
        measure()
        val before = keyboard.keyboard!!.mId.mHeight
        assertTrue(picker.height > baseHeight)
        assertTrue(grid.height > 0)
        val location = IntArray(2)
        grid.getLocationInWindow(location)
        val gridBottom = location[1] + grid.height
        keyboard.getLocationInWindow(location)
        assertTrue(gridBottom <= location[1])
        picker.findViewById<EditText>(R.id.media_query).performClick()
        measure()
        assertEquals(View.VISIBLE, grid.visibility)
        assertEquals(View.VISIBLE, keyboard.visibility)
        assertEquals(before, keyboard.keyboard!!.mId.mHeight)
        assertEquals(activity.getString(R.string.media_focus_search),
            picker.findViewById<TextView>(R.id.media_focus).text.toString())
        picker.findViewById<View>(R.id.media_focus).performClick()
        assertEquals(activity.getString(R.string.media_focus_app),
            picker.findViewById<TextView>(R.id.media_focus).text.toString())
        assertTrue(picker.isTypingInApp)
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

    @Test fun successfulResultsRestoreAfterRestartWithoutAnotherProviderRequest() {
        picker.stop()
        val item = OwnedMediaFixtures.item().copy(
            pageUrl = "https://giphy.com/gifs/owned",
            renditions = listOf(OwnedMediaFixtures.rendition().copy(url = "https://media.giphy.com/owned.gif"))
        )
        source.page = MediaPage(listOf(item), null)
        val library = MediaLibrary(activity, source, { !host.privateMode })
        picker = MediaPickerView(activity, host, source, library)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
        idleUntil { picker.findViewById<TextView>(R.id.media_status).text.toString().contains("Enter") }
        picker.findViewById<EditText>(R.id.media_query).setText("owned query")
        picker.findViewById<View>(R.id.media_submit).performClick()
        idleUntil { picker.findViewById<TextView>(R.id.media_status).text.toString().contains("Results for") }
        assertEquals(1, source.requests.size)
        val bytes = runBlocking { library.download(item.renditions.first(), MediaLimits.PREVIEW_BYTES) }
        picker.stop()
        val restoredLibrary = MediaLibrary(activity, source, { !host.privateMode })
        picker = MediaPickerView(activity, host, source, restoredLibrary)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
        idleUntil { picker.findViewById<EditText>(R.id.media_query).text.toString() == "owned query" }
        assertEquals(1, source.requests.size)
        val warm = runBlocking { restoredLibrary.download(item.renditions.first(), MediaLimits.PREVIEW_BYTES) }
        kotlin.test.assertContentEquals(bytes, warm)
        assertEquals(1, source.downloads)
        assertEquals(1, picker.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.media_results).adapter!!.itemCount)
        picker.findViewById<View>(R.id.media_refresh).performClick()
        idleUntil { source.requests.size == 2 }
    }

    @Test fun privateReopenDoesNotDisplayCachedResultsOrQuery() {
        picker.stop()
        val library = MediaLibrary(activity, source, { !host.privateMode })
        runBlocking {
            val page = library.search(MediaKind.GIF, "private cached query", "en")
            library.remember(MediaSnapshot(MediaKind.GIF, "private cached query", "private cached query",
                "en", page.items, page.nextOffset))
        }
        host.privateMode = true
        picker = MediaPickerView(activity, host, source, library)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("", picker.findViewById<EditText>(R.id.media_query).text.toString())
        assertEquals(0, picker.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.media_results).adapter!!.itemCount)
        assertEquals(1, source.requests.size)
    }

    @Test fun closingBeforeRestoreCompletesDoesNotOverwriteSavedDraftOrPosition() {
        picker.stop()
        val library = MediaLibrary(activity, source, { !host.privateMode })
        runBlocking {
            val page = library.search(MediaKind.GIF, "saved query", "en")
            library.remember(MediaSnapshot(MediaKind.GIF, "saved draft", "saved query",
                "en", page.items, page.nextOffset, 4, 12))
        }
        picker = MediaPickerView(activity, host, source, library)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
        picker.stop()
        shadowOf(Looper.getMainLooper()).idle()
        val restored = runBlocking { library.restore()!!.tabs.single() }
        assertEquals("saved draft", restored.draft)
        assertEquals(4, restored.scrollPosition)
        assertEquals(12, restored.scrollOffset)
    }

    @Test fun longPressPreviewPinsWithoutInsertingAndPersistsSeparateCollection() {
        picker.stop()
        val item = OwnedMediaFixtures.item().copy(
            pageUrl = "https://giphy.com/gifs/owned",
            renditions = listOf(OwnedMediaFixtures.rendition().copy(url = "https://media.giphy.com/owned.gif"))
        )
        source.page = MediaPage(listOf(item), null)
        val library = MediaLibrary(activity, source, { !host.privateMode })
        picker = MediaPickerView(activity, host, source, library)
        activity.setContentView(picker)
        picker.open(MediaKind.GIF)
        picker.findViewById<EditText>(R.id.media_query).setText("owned")
        picker.findViewById<View>(R.id.media_submit).performClick()
        idleUntil { picker.findViewById<TextView>(R.id.media_status).text.toString().contains("Results for") }
        val width = (400 * picker.resources.displayMetrics.density).toInt()
        picker.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        picker.layout(0, 0, picker.measuredWidth, picker.measuredHeight)
        val results = picker.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.media_results)
        assertTrue(results.childCount > 0)
        results.getChildAt(0).performLongClick()
        assertEquals(View.VISIBLE, picker.findViewById<View>(R.id.media_preview).visibility)
        assertEquals(0, host.returns)
        picker.findViewById<View>(R.id.media_pin_toggle).performClick()
        idleUntil { runBlocking { library.pins().size } == 1 }
        picker.findViewById<View>(R.id.media_preview_close).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.GONE, picker.findViewById<View>(R.id.media_preview).visibility)
        assertEquals(0, host.returns)
        assertEquals(listOf("owned"), runBlocking { MediaLibrary(activity, source, { true }).pins().map { it.id } })
    }

    private fun idleUntil(condition: () -> Boolean) {
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue(condition(), "Asynchronous picker operation did not finish")
    }

    private class Host : MediaPickerHost {
        override var editorVersion = 1L
        override var networkAvailable = true
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
        var page = MediaPage(emptyList(), null)
        var downloads = 0
        val requests = mutableListOf<Pair<MediaKind, String>>()
        override fun hasApiKey() = hasKey
        override suspend fun search(kind: MediaKind, query: String, language: String, offset: Int): MediaPage {
            requests.add(kind to query)
            return page
        }
        override suspend fun download(rendition: MediaRendition, maxBytes: Int): ByteArray {
            downloads++
            return OwnedMediaFixtures.gif()
        }
    }
}
