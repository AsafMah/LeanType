// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import android.content.res.AssetManager
import android.app.KeyguardManager
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.PopupKeysKeyboardView
import helium314.keyboard.keyboard.SwipeShortcutMenu
import helium314.keyboard.keyboard.SwipeShortcutMenu.Direction.DOWN
import helium314.keyboard.keyboard.SwipeShortcutMenu.Direction.UP
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SwipeShortcutMenuTest(private val glideEnabled: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "glide={0}")
        fun gestureModes() = listOf(arrayOf(false), arrayOf(true))
    }

    private lateinit var context: Context
    private lateinit var keyboard: Keyboard
    private lateinit var view: MainKeyboardView
    private lateinit var listener: KeyboardActionListener
    private var eventTime = 10_000L
    private var downTime = 0L
    private var useTouchEvent = false

    @Before
    fun setUp() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        val assets = mock(AssetManager::class.java)
        // Android uses slash-separated asset paths, even when Robolectric runs on Windows.
        `when`(assets.list(anyString())).thenAnswer {
            ime.assets.list(it.getArgument<String>(0).replace('\\', '/'))
        }
        `when`(assets.open(anyString())).thenAnswer {
            ime.assets.open(it.getArgument<String>(0).replace('\\', '/'))
        }
        context = object : ContextThemeWrapper(ime, KeyboardTheme.getKeyboardTheme(ime).mStyleId) {
            override fun getAssets() = assets
        }
        context.prefs().edit()
            .putBoolean(Settings.PREF_SWIPE_UP_MENU, true)
            .putBoolean(Settings.PREF_SWIPE_DOWN_MENU, true)
            .putBoolean(Settings.PREF_POPUP_ON, false)
            .remove(Settings.PREF_LAYOUT_PREFIX + LayoutType.SWIPE_UP.name)
            .remove(Settings.PREF_LAYOUT_PREFIX + LayoutType.SWIPE_DOWN.name)
            .apply()
        Settings.getInstance().loadSettings(context)
        LayoutUtilsCustom.onLayoutFileChanged()
        KeyboardLayoutSet.onKeyboardThemeChanged()
        keyboard = buildKeyboard()
        view = MainKeyboardView(context)
        view.setKeyboard(keyboard)
        view.measure(exact(700), exact(400))
        view.layout(0, 0, 700, 400)
        listener = mock(KeyboardActionListener::class.java)
        view.setKeyboardActionListener(listener)
        configureGlide()
    }

    @After
    fun tearDown() {
        if (::view.isInitialized) {
            useTouchEvent = false
            event(MotionEvent.ACTION_CANCEL, 0, 0)
            view.cancelAllOngoingEvents()
        }
        LayoutParser.clearCache()
    }

    @Test
    fun `seven bottom shortcuts align with z through m`() {
        val row = keyboard.swipeShortcutRows.getValue(DOWN)
        assertEquals("zxcvbnm", row.keys.map { it.code.toChar() }.joinToString(""))
        val menu = SwipeShortcutMenu.Builder(context, keyboard, row, DOWN).build()
        assertEquals(
            listOf(KeyCode.ARROW_LEFT, KeyCode.ARROW_RIGHT, KeyCode.WORD_LEFT, KeyCode.WORD_RIGHT,
                KeyCode.CLIPBOARD, KeyCode.NUMPAD, KeyCode.EMOJI),
            menu.sortedKeys.map { it.code }
        )
        row.keys.zip(menu.sortedKeys).forEach { (source, shortcut) ->
            assertTrue(abs(center(source) - row.left - center(shortcut)) <= 1)
        }
    }

    @Test
    fun `top row follows visible number row`() {
        assertEquals("qwertyuiop", keyboard.swipeShortcutRows.getValue(UP).keys.map { it.code.toChar() }.joinToString(""))
        val numbered = buildKeyboard(numberRow = true)
        assertEquals("1234567890", numbered.swipeShortcutRows.getValue(UP).keys.map { it.code.toChar() }.joinToString(""))
        assertEquals("zxcvbnm", numbered.swipeShortcutRows.getValue(DOWN).keys.map { it.code.toChar() }.joinToString(""))
    }

    @Test
    fun `nonalphabet layouts do not intercept swipes`() {
        assertTrue(buildKeyboard(element = KeyboardId.ELEMENT_SYMBOLS).swipeShortcutRows.isEmpty())
        assertTrue(buildKeyboard(element = KeyboardId.ELEMENT_NUMBER).swipeShortcutRows.isEmpty())
    }

    @Test
    fun `menu endpoints remain aligned at different item counts`() {
        val row = keyboard.swipeShortcutRows.getValue(DOWN)
        for (count in listOf(2, 3, 7, 10, 20)) {
            val centers = row.centers(count)
            assertTrue(abs(center(row.keys.first()) - centers.first()) <= 1)
            assertTrue(abs(center(row.keys.last()) - centers.last()) <= 1)
            assertEquals(count, centers.distinct().size)
        }
        assertEquals(center(row.keys[3]), row.centers(1).single())
    }

    @Test
    fun `split layout preserves physical source centers`() {
        val split = buildKeyboard(split = true)
        assertTrue(split.mId.mIsSplitLayout)
        val row = split.swipeShortcutRows.getValue(DOWN)
        assertEquals(row.keys.map(::center), row.centers(row.keys.size))
    }

    @Test
    fun `custom secondary menu preserves order text and all rows`() {
        val layout = """[[{"label":"copy"},{"label":"Hello","code":${KeyCode.MULTIPLE_CODE_POINTS}}], [{"label":"!"}]]"""
        // Use the standard layout editor's file naming and storage path.
        val name = LayoutUtilsCustom.getLayoutName("My menu", LayoutType.SWIPE_DOWN) + "json"
        val file = LayoutUtilsCustom.getLayoutFile(name, LayoutType.SWIPE_DOWN, context)
        file.writeText(layout)
        assertEquals(layout, file.readText())
        LayoutUtilsCustom.onLayoutFileChanged()
        Settings.writeDefaultLayoutName(name, LayoutType.SWIPE_DOWN, context.prefs())
        LayoutParser.clearCache()
        val row = keyboard.swipeShortcutRows.getValue(DOWN)
        val menu = SwipeShortcutMenu.Builder(context, keyboard, row, DOWN).build()
        assertEquals(3, menu.sortedKeys.size)
        assertEquals(KeyCode.CLIPBOARD_COPY, menu.sortedKeys.first().code)
        assertEquals("Hello", menu.sortedKeys[1].outputText)
        assertEquals('!'.code, menu.sortedKeys.last().code)
        file.writeText("""[[{"label":"paste"}]]""")
        LayoutUtilsCustom.onLayoutFileChanged()
        KeyboardLayoutSet.onKeyboardThemeChanged()
        val edited = SwipeShortcutMenu.Builder(context, keyboard, row, DOWN).build()
        assertEquals(KeyCode.CLIPBOARD_PASTE, edited.sortedKeys.single().code)
        file.delete()
        LayoutUtilsCustom.onLayoutFileChanged()
        Settings.writeDefaultLayoutName(null, LayoutType.SWIPE_DOWN, context.prefs())
    }

    @Test
    fun `downward swipe sends shortcut but not source letter`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_UP, x, y + source.height)
        assertFalse(view.isShowingPopupKeysPanel())
        verify(listener).onCodeInput(eq(KeyCode.ARROW_LEFT), anyInt(), anyInt(), eq(false))
        verify(listener, never()).onCodeInput(eq('z'.code), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `number row swipe opens upward menu and q does not`() {
        keyboard = buildKeyboard(numberRow = true)
        view.setKeyboard(keyboard)
        swipe(keyboard.getKey('1'.code)!!, UP)
        verify(listener).onCodeInput(eq(KeyCode.UNDO), anyInt(), anyInt(), eq(false))
        clearInvocations(listener)
        val q = keyboard.getKey('q'.code)!!
        event(MotionEvent.ACTION_DOWN, center(q), q.y + q.height / 2)
        event(MotionEvent.ACTION_MOVE, center(q), q.y - q.height / 2)
        assertFalse(view.isShowingPopupKeysPanel())
    }

    @Test
    fun `swiping from each bottom letter chooses corresponding item`() {
        val row = keyboard.swipeShortcutRows.getValue(DOWN)
        val codes = SwipeShortcutMenu.Builder(context, keyboard, row, DOWN).build().sortedKeys.map { it.code }
        row.keys.zip(codes).forEach { (source, code) ->
            clearInvocations(listener)
            swipe(source, DOWN)
            verify(listener).onCodeInput(eq(code), anyInt(), anyInt(), eq(false))
            verify(listener, times(1)).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        }
    }

    @Test
    fun `dragging across menu selects last item and keeps menu fixed`() {
        val row = keyboard.swipeShortcutRows.getValue(DOWN)
        val source = row.keys.first()
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, center(source), y)
        event(MotionEvent.ACTION_MOVE, center(source), y + source.height)
        event(MotionEvent.ACTION_MOVE, center(row.keys.last()), y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_UP, center(row.keys.last()), y + source.height)
        verify(listener).onCodeInput(eq(KeyCode.EMOJI), anyInt(), anyInt(), eq(false))
    }

    @Test
    fun `returning to starting height cancels without typing`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_MOVE, x, y)
        event(MotionEvent.ACTION_UP, x, y)
        assertFalse(view.isShowingPopupKeysPanel())
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `action cancel cannot commit a popup through phantom up`() {
        val source = keyboard.getKey('m'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_CANCEL, x, y + source.height)
        assertFalse(view.isShowingPopupKeysPanel())
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `locked keyboard cannot expose editable shortcuts`() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(true)
        val locked = buildKeyboard()
        assertTrue(locked.mId.mDeviceLocked)
        assertTrue(locked.swipeShortcutRows.isEmpty())
        shadowOf(keyguard).setIsDeviceLocked(false)
    }

    @Test
    fun `second finger cancels before the popup event filter`() {
        val source = keyboard.getKey('z'.code)!!
        val other = keyboard.getKey('q'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        multiEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(0 to (x to y + source.height), 1 to (center(other) to other.y + other.height / 2)))
        assertFalse(view.isShowingPopupKeysPanel())
        multiEvent(MotionEvent.ACTION_POINTER_UP,
            listOf(0 to (x to y + source.height), 1 to (center(other) to other.y + other.height / 2)))
        multiEvent(MotionEvent.ACTION_UP, listOf(1 to (center(other) to other.y + other.height / 2)))
        verify(listener, never()).onCodeInput(eq(KeyCode.ARROW_LEFT), anyInt(), anyInt(), anyBoolean())
        verify(listener, never()).onCodeInput(eq('z'.code), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `non distinct multitouch cannot select through its synthetic up`() {
        context.prefs().edit().putBoolean(DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, true).apply()
        view = MainKeyboardView(context)
        view.setKeyboard(keyboard)
        view.measure(exact(700), exact(400))
        view.layout(0, 0, 700, 400)
        view.setKeyboardActionListener(listener)
        configureGlide()
        useTouchEvent = true
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        multiEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            listOf(0 to (x to y + source.height), 1 to (300 to 100)))
        assertFalse(view.isShowingPopupKeysPanel())
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `custom symbol toggle drives real keyboard state`() {
        val name = LayoutUtilsCustom.getLayoutName("Mode menu", LayoutType.SWIPE_DOWN) + "json"
        val file = LayoutUtilsCustom.getLayoutFile(name, LayoutType.SWIPE_DOWN, context)
        file.writeText("""[[{"label":"symbol_alpha"}]]""")
        LayoutUtilsCustom.onLayoutFileChanged()
        Settings.writeDefaultLayoutName(name, LayoutType.SWIPE_DOWN, context.prefs())
        LayoutParser.clearCache()
        val switches = mock(KeyboardState.SwitchActions::class.java)
        val state = KeyboardState(switches)
        state.onLoadKeyboard(0, null, false)
        clearInvocations(switches)
        val stateListener = object : KeyboardActionListener by KeyboardActionListener.EMPTY_LISTENER {
            override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
                state.onPressKey(primaryCode, isSinglePointer, 0, null)
            }
            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
                state.onEvent(Event.createSoftwareKeypressEvent(primaryCode, 0, x, y, isKeyRepeat), 0, null)
            }
            override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
                state.onReleaseKey(primaryCode, withSliding, 0, null)
            }
        }
        view.setKeyboardActionListener(stateListener)
        try {
            swipe(keyboard.getKey('z'.code)!!, DOWN)
            verify(switches, times(1)).setSymbolsKeyboard()
            verify(switches, never()).setAlphabetKeyboard()
        } finally {
            Settings.writeDefaultLayoutName(null, LayoutType.SWIPE_DOWN, context.prefs())
            file.delete()
            LayoutUtilsCustom.onLayoutFileChanged()
        }
    }

    @Test
    fun `fast swipe with no move event still commits once`() {
        val source = keyboard.getKey('z'.code)!!
        event(MotionEvent.ACTION_DOWN, center(source), source.y + source.height / 2)
        event(MotionEvent.ACTION_UP, center(source), source.y + source.height * 2)
        verify(listener).onCodeInput(eq(KeyCode.ARROW_LEFT), anyInt(), anyInt(), eq(false))
        verify(listener, times(1)).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `horizontal glide keeps its path when it later moves outward`() {
        for (direction in listOf(UP, DOWN)) assertGlideSurvivesOutwardMove(direction)
    }

    @Test
    fun `holding shortcut menu cannot deliver a pending glide update`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        // Sampled by the gesture arbiter but still below the shortcut activation threshold.
        event(MotionEvent.ACTION_MOVE, x, y + source.width / 4)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertTrue(view.isShowingPopupKeysPanel())
        assertFalse(mockingDetails(listener).invocations.any { it.method.name == "onUpdateBatchInput" },
            "A pending glide update must not run after a shortcut menu takes ownership")
        verify(listener, never()).onStartBatchInput()
        event(MotionEvent.ACTION_UP, x, y + source.height)
        verify(listener).onCodeInput(eq(KeyCode.ARROW_LEFT), anyInt(), anyInt(), eq(false))
    }

    @Test
    fun `cancelled shortcut does not disable the next glide`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_MOVE, x, y)
        event(MotionEvent.ACTION_UP, x, y)
        assertFalse(view.isShowingPopupKeysPanel())
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        assertGlideSurvivesOutwardMove(UP)
    }

    @Test
    fun `coalesced sideways history prevents a late outward menu`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        eventTime += 100
        val motion = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE,
            (x + source.width).toFloat(), y.toFloat(), 0)
        motion.addBatch(eventTime + 100, x.toFloat(), (y + source.height).toFloat(), 1f, 1f, 0)
        assertEquals(1, motion.historySize)
        view.processMotionEvent(motion)
        motion.recycle()
        assertFalse(view.isShowingPopupKeysPanel())
    }

    @Test
    fun `changing keyboards cancels the menu without committing`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertTrue(view.isShowingPopupKeysPanel())
        view.setKeyboard(buildKeyboard(numberRow = true))
        assertFalse(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_UP, x, y + source.height)
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `shortcut press and release are delivered only on commitment`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        verify(listener, never()).onPressKey(KeyCode.ARROW_LEFT, 0, true, HapticEvent.NO_HAPTICS)
        event(MotionEvent.ACTION_UP, x, y + source.height)
        val order = inOrder(listener)
        order.verify(listener).onPressKey(KeyCode.ARROW_LEFT, 0, true, HapticEvent.NO_HAPTICS)
        order.verify(listener).onCodeInput(eq(KeyCode.ARROW_LEFT), anyInt(), anyInt(), eq(false))
        order.verify(listener).onReleaseKey(KeyCode.ARROW_LEFT, false)
    }

    @Test
    fun `disabled menus and ordinary taps keep original behavior`() {
        context.prefs().edit().putBoolean(Settings.PREF_SWIPE_DOWN_MENU, false).apply()
        Settings.getInstance().loadSettings(context)
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_UP, x, y)
        verify(listener).onCodeInput(eq('z'.code), anyInt(), anyInt(), eq(false))
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertFalse(view.isShowingPopupKeysPanel())
    }

    @Test
    fun `sideways start cannot later become an outward shortcut`() {
        val source = keyboard.getKey('z'.code)!!
        val x = center(source)
        val y = source.y + source.height / 2
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x + source.width, y)
        event(MotionEvent.ACTION_MOVE, x, y + source.height)
        assertFalse(view.isShowingPopupKeysPanel())
    }

    @Test
    fun `period long press still opens normal punctuation popup`() {
        val period = keyboard.getKey('.'.code)!!
        event(MotionEvent.ACTION_DOWN, center(period), period.y + period.height / 2)
        PointerTracker.getPointerTracker(0).onLongPressed()
        assertTrue(view.isShowingPopupKeysPanel())
    }

    @Test
    fun `popup placement does not depend on initiating key or view padding`() {
        view.setPadding(9, 5, 7, 3)
        for (direction in listOf(UP, DOWN)) {
            val row = keyboard.swipeShortcutRows.getValue(direction)
            for (source in listOf(row.keys.first(), row.keys.last())) {
                val x = center(source) + view.paddingLeft
                val y = source.y + source.height / 2 + view.paddingTop
                event(MotionEvent.ACTION_DOWN, x, y)
                val panel = assertNotNull(view.showSwipeShortcutMenu(PointerTracker.getPointerTracker(0), direction))
                val popup = panel as PopupKeysKeyboardView
                val menu = assertNotNull(popup.keyboard)
                menu.sortedKeys.zip(row.centers(menu.sortedKeys.size)).forEach { (key, expected) ->
                    val actual = popup.getContainerView().x + popup.x + popup.paddingLeft + center(key)
                    assertTrue(abs(actual - view.paddingLeft - expected) <= 1, "$direction $actual != $expected")
                }
                popup.dismissPopupKeysPanel()
                event(MotionEvent.ACTION_CANCEL, x, y)
            }
        }
    }

    private fun buildKeyboard(
        numberRow: Boolean = false, split: Boolean = false, element: Int = KeyboardId.ELEMENT_ALPHABET
    ): Keyboard = KeyboardLayoutSet.Builder(context, EditorInfo())
        .setKeyboardGeometry(700, 400)
        .setSubtype(RichInputMethodSubtype.get(
            SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.ENGLISH, "qwerty", true)
        ))
        .setNumberRowEnabled(numberRow)
        .setSplitLayoutEnabled(split)
        .disableTouchPositionCorrectionData()
        .build().getKeyboard(element)

    private fun swipe(key: Key, direction: SwipeShortcutMenu.Direction) {
        val x = center(key)
        val y = key.y + key.height / 2
        val endY = y + if (direction == UP) -key.height else key.height
        event(MotionEvent.ACTION_DOWN, x, y)
        event(MotionEvent.ACTION_MOVE, x, endY)
        assertTrue(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_UP, x, endY)
    }

    private fun configureGlide() {
        view.setMainDictionaryAvailability(true)
        PointerTracker.setClipboardInlineInputActive(false)
        view.setGestureHandlingEnabledByUser(glideEnabled, false, false)
    }

    private fun assertGlideSurvivesOutwardMove(direction: SwipeShortcutMenu.Direction) {
        val row = keyboard.swipeShortcutRows.getValue(direction)
        val source = row.keys.first()
        val y = source.y + source.height / 2
        clearInvocations(listener)
        event(MotionEvent.ACTION_DOWN, center(source), y)
        for (key in row.keys.drop(1).take(3)) {
            event(MotionEvent.ACTION_MOVE, center(key), y)
        }
        if (glideEnabled) verify(listener).onStartBatchInput()
        else verify(listener, never()).onStartBatchInput()
        val x = center(row.keys[3])
        val outwardY = y + if (direction == UP) -source.height else source.height
        event(MotionEvent.ACTION_MOVE, x, outwardY)
        assertFalse(view.isShowingPopupKeysPanel())
        event(MotionEvent.ACTION_UP, x, outwardY)
        val completions = mockingDetails(listener).invocations.filter { it.method.name == "onEndBatchInput" }
        assertEquals(if (glideEnabled) 1 else 0, completions.size)
        if (glideEnabled) {
            val points = completions.single().arguments.single() as InputPointers
            assertEquals(5, points.pointerSize)
            assertEquals(center(source), points.xCoordinates[0])
            assertEquals(x, points.xCoordinates[4])
            assertEquals(outwardY, points.yCoordinates[4])
            verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        }
    }

    private fun event(action: Int, x: Int, y: Int) {
        eventTime += 100
        if (action == MotionEvent.ACTION_DOWN) downTime = eventTime
        val motion = MotionEvent.obtain(downTime, eventTime, action, x.toFloat(), y.toFloat(), 0)
        if (useTouchEvent) view.onTouchEvent(motion) else view.processMotionEvent(motion)
        motion.recycle()
    }

    private fun multiEvent(action: Int, points: List<Pair<Int, Pair<Int, Int>>>) {
        eventTime += 100
        val properties = points.map { (id, _) -> MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = points.map { (_, point) -> MotionEvent.PointerCoords().apply {
            x = point.first.toFloat()
            y = point.second.toFloat()
            pressure = 1f
            size = 1f
        } }.toTypedArray()
        val motion = MotionEvent.obtain(downTime, eventTime, action, points.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, 0, 0)
        if (useTouchEvent) view.onTouchEvent(motion) else view.processMotionEvent(motion)
        motion.recycle()
    }

    private fun center(key: Key) = key.x + key.width / 2
    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
