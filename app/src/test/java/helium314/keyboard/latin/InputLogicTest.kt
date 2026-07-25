// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.*
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ShadowFacilitator2.Companion.addedWords
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastAddedWord
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastNgramContext
import helium314.keyboard.latin.ShadowFacilitator2.Companion.ngramContexts
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getTimestampFormatter
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.junit.Ignore
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence
import helium314.keyboard.latin.common.InputPointers
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class InputLogicTest {
    private lateinit var latinIME: LatinIME
    private val settingsValues get() = Settings.getValues()
    private val inputLogic get() = latinIME.mInputLogic
    private val connection: RichInputConnection get() = inputLogic.connection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(inputLogic) as WordComposer
    private val spaceStateReader = InputLogic::class.java.getDeclaredField("mSpaceState").apply { isAccessible = true }
    private val spaceState get() = spaceStateReader.get(inputLogic) as Int
    private val beforeComposingReader = RichInputConnection::class.java.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }
    private val connectionTextBeforeComposingText get() = (beforeComposingReader.get(connection) as CharSequence).toString()
    private val composingReader = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
    private val connectionComposingText get() = (composingReader.get(connection) as CharSequence).toString()
    private val combiningGraceExpired = InputLogic::class.java.getDeclaredMethod("onCombiningGraceExpired").apply { isAccessible = true }

    @BeforeTest
    fun setUp() {
        mainKeyboardView = Mockito.mock(MainKeyboardView::class.java)
        latinIME = Robolectric.setupService(LatinIME::class.java)
        // start logging only after latinIME is created, avoids showing the stack traces if library is not found
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @Test fun inputCode() {
        reset()
        input('c')
        assertEquals("c", textBeforeCursor)
        assertEquals("c", getText())
        assertEquals("", textAfterCursor)
        assertEquals("c", composingText)
        latinIME.mHandler.onFinishInput()
        assertEquals("", composingText)
    }

    @Test fun `english space-separated typing keeps composing word`() {
        reset()
        chainInput("hello")
        assertEquals("hello", composingText)
        input(' ')
        assertEquals("hello ", text)
        assertEquals("", composingText)
    }

    @Test fun delete() {
        reset()
        setText("hello there ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)
    }

    @Test fun deleteInsideWord() {
        reset()
        setText("hello you there")
        setCursorPosition(8) // after o in you
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello yu there", text)
        assertEquals("yu", composingText)
    }

    @Test fun insertLetterIntoWord() {
        reset()
        setText("hello")
        setCursorPosition(3) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getText())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        assertEquals("", composingText)
    }

    @Test fun insertLetterIntoWordWithWeirdEditor() {
        reset()
        currentInputType = 180225 // should not change much, but just to be sure
        setText("hello")
        setCursorPosition(3, weirdTextField = true) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getText())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    @Test fun insertLetterIntoOneOfSeveralWords() {
        reset()
        setText("hello my friend")
        setCursorPosition(7) // between m and y
        input('a')
        assertEquals("may", getWordAtCursor())
        assertEquals("hello may friend", getText())
        assertEquals(8, getCursorPosition())
        assertEquals(8, cursor)
    }

    // todo: make it work, but it might not be that simple because adding is done in combiner
    //  https://github.com/Helium314/HeliBoard/issues/214
    @Test fun insertLetterIntoWordHangulFails() {
        if (BuildConfig.BUILD_TYPE == "runTests") return
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('ㄲ') // fails, as expected from the hangul issue when processing the event in onCodeInput
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getWordAtCursor())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getText())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", textBeforeCursor + textAfterCursor)
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    // see issue 1447
    @Test fun separatorAfterHangul() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        chainInput("ㅛ.")
        assertEquals("ㅛ.", text)
    }

    @Test fun `space after thai composing word inserts space`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        chainInput("ภาษาไทย")
        assertEquals("ไทย", composingText)
        input(' ')
        assertEquals("ภาษาไทย ", text)
        assertEquals("", composingText)
    }

    @Test fun `thai composing word follows word boundaries`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        chainInput("ภาษาไทยดี")
        assertEquals("ภาษาไทยดี", text)
        assertEquals("ดี", composingText)
        assertEquals("ไทย", lastAddedWord)
        assertEquals("ภาษา", lastNgramContext)
        assertEquals(listOf("ภาษา", "ไทย"), addedWords)
        assertEquals(listOf("<S>", "ภาษา"), ngramContexts)
    }

    @Test fun `single thai composing segment remains composing`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        chainInput("ไทย")
        assertEquals("ไทย", text)
        assertEquals("ไทย", composingText)
    }

    @Test fun `space after segmented thai composing word inserts one space`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        chainInput("ภาษาไทยดี")
        input(' ')
        assertEquals("ภาษาไทยดี ", text)
        assertEquals("", composingText)
    }

    @Test fun `immediate text expansion uses full segmented thai word`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()
        val shortcuts = mapOf("ภาษาไทย" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("expanded", ""))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        typeNoAssert("ภาษาไทย")

        assertEquals("expanded", text)
        assertEquals("", composingText)
        assertEquals("", lastAddedWord)
    }

    @Test fun `immediate text expansion uses prefixed segmented thai word`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()
        val shortcuts = mapOf(".ภาษาไทย" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("expanded", "."))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        typeNoAssert(".ภาษาไทย")

        assertEquals("expanded", text)
        assertEquals("", composingText)
        assertEquals("", lastAddedWord)
    }

    @Test fun `prefixed immediate text expansion does not defer thai without prefix`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()
        val shortcuts = mapOf(".ภาษาไทย" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("expanded", "."))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        typeNoAssert("ภาษาไทย")

        assertEquals("ภาษาไทย", text)
        assertEquals("ไทย", composingText)
        assertEquals("ภาษา", lastAddedWord)
    }

    @Test fun `immediate text expansion still segments thai non-shortcut`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()
        val shortcuts = mapOf("อื่น" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("expanded", ""))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        chainInput("ภาษาไทยดี")

        assertEquals("ภาษาไทยดี", text)
        assertEquals("ดี", composingText)
        assertEquals("ไทย", lastAddedWord)
    }

    @Test fun `failed immediate expansion commits thai segments separately`() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("th".constructLocale()).first())
        currentScript = ScriptUtils.SCRIPT_THAI
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()
        val shortcuts = mapOf("ภาษาไทยดี" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("expanded", ""))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        typeNoAssert("ภาษาไทยแดง")

        assertEquals("ภาษาไทยแดง", text)
        assertEquals("แดง", composingText)
        assertEquals("ไทย", lastAddedWord)
        assertEquals("ภาษา", lastNgramContext)
    }

    // see issue 1551 (debug only)
    @Test fun deleteHangul() {
        reset()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("ko".constructLocale()).first())
        setText("ㅛㅛ ")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
    }

    @Test fun separatorUnselectsWord() {
        reset()
        setText("hello")
        assertEquals("hello", composingText)
        input('.')
        assertEquals("", composingText)
    }

    @Test fun autospace() {
        reset()
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
    }

    @Test fun autospaceButWithTextAfter() {
        reset()
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        assertEquals("hello.a there", text)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
        assertEquals("hello. a there", text)
    }

    @Test fun joinNextSuppressesSingleAutospaceDecision() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        functionalKeyPress(KeyCode.JOIN_NEXT)
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        input('.')
        input('b')
        assertEquals("hello.a. b", textBeforeCursor)
    }

    @Test fun joinNextDoesNotOverrideExplicitSpace() {
        reset()
        setText("hello")
        functionalKeyPress(KeyCode.JOIN_NEXT)
        input(Constants.CODE_SPACE)
        input('a')
        assertEquals("hello a", textBeforeCursor)
    }

    @Test fun forceNextSpaceInsertsSpaceImmediately() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        functionalKeyPress(KeyCode.FORCE_NEXT_SPACE)
        assertEquals("hello ", textBeforeCursor)
        chainInput("world")
        input('.')
        input('a')
        assertEquals("hello world.a", textBeforeCursor)
        input('.')
        input('b')
        assertEquals("hello world.a. b", textBeforeCursor)
    }

    @Test fun joinNextAfterCombiningAutospaceResumesWordForNextGesture() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING, true)
        }
        chainInput("tech")
        expireCombiningGrace()
        assertEquals("tech ", textBeforeCursor)

        functionalKeyPress(KeyCode.JOIN_NEXT)
        assertEquals("tech", textBeforeCursor)
        gestureInput("technology")
        assertEquals("technology", textBeforeCursor)
        assertEquals("technology", composingText)

        expireCombiningGrace()
        assertEquals("technology ", textBeforeCursor)
    }

    @Test fun forceNextSpaceAfterCombiningAutospaceDoesNotDoubleSpaceAndSuppressesNextAutospace() {
        reset()
        latinIME.prefs().edit { putInt(Settings.PREF_COMBINING_GRACE_MS, 1000) }
        chainInput("hello")
        expireCombiningGrace()
        assertEquals("hello ", textBeforeCursor)

        functionalKeyPress(KeyCode.FORCE_NEXT_SPACE)
        assertEquals("hello ", textBeforeCursor)
        gestureInput("world")
        assertEquals("hello world", textBeforeCursor)

        expireCombiningGrace()
        assertEquals("hello world", textBeforeCursor)
    }

    @Test fun forceNextSpaceDuringCombiningCommitsSpaceAndSuppressesNextAutospace() {
        reset()
        latinIME.prefs().edit { putInt(Settings.PREF_COMBINING_GRACE_MS, 1000) }
        chainInput("hello")
        functionalKeyPress(KeyCode.FORCE_NEXT_SPACE)
        assertEquals("hello ", textBeforeCursor)

        gestureInput("world")
        assertEquals("hello world", textBeforeCursor)

        expireCombiningGrace()
        assertEquals("hello world", textBeforeCursor)
    }

    @Test fun forceNextSpaceSurvivesExpectedSelectionUpdateAfterInsertedSpace() {
        reset()
        latinIME.prefs().edit { putInt(Settings.PREF_COMBINING_GRACE_MS, 1000) }
        chainInput("hello")
        val oldCursor = cursor
        functionalKeyPress(KeyCode.FORCE_NEXT_SPACE)
        latinIME.onUpdateSelection(oldCursor, oldCursor, cursor, cursor, -1, -1)
        assertEquals("hello ", textBeforeCursor)

        gestureInput("world")
        expireCombiningGrace()
        assertEquals("hello world", textBeforeCursor)
    }

    @Test fun tapOnlyCombiningWordDoesNotAutospaceWhenGestureGateEnabled() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, true)
        }

        chainInput("hello")
        expireCombiningGrace()

        assertEquals("hello", textBeforeCursor)
        assertEquals("", composingText)

        input(' ')
        assertEquals("hello ", textBeforeCursor)
    }

    @Test fun tapOnlyCombiningWordDoesNotShowAutospaceIndicatorWhenGestureGateEnabled() {
        if (BuildConfig.BUILD_TYPE == "runTests") return // needs main dictionary, unavailable in JVM env; see #12
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, true)
        }

        input('h')

        Mockito.verify(mainKeyboardView, Mockito.atLeastOnce())
            .setCombiningMode(Mockito.eq(false), Mockito.anyLong(), Mockito.anyInt())
        Mockito.verify(mainKeyboardView, Mockito.never())
            .setCombiningMode(Mockito.eq(true), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test fun gestureCombiningWordStillAutospacesWhenGestureGateEnabled() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, true)
        }

        gestureInput("hello")
        expireCombiningGrace()

        assertEquals("hello ", textBeforeCursor)
    }

    @Test fun deferredGraceSpaceMaterializesOnNextInput() {
        // #23: with PREF_SPACING_DEFER_GRACE_SPACE on, the grace commit does NOT write the space
        // eagerly (the default path gives "hello "); it arms PHANTOM so the space appears on the
        // next input instead.
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_SPACING_DEFER_GRACE_SPACE, true)
        }
        gestureInput("hello")
        expireCombiningGrace()
        assertEquals("hello", textBeforeCursor)        // deferred: no trailing space yet
        chainInput("world")
        assertEquals("hello world", textBeforeCursor)  // materialized on the next letter
    }

    @Test fun deferredGraceCommitIsBackspaceReversible() {
        // The deferred commit leaves no eager space to orphan; the first backspace deletes the
        // gesture word cleanly (PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD default on).
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_SPACING_DEFER_GRACE_SPACE, true)
        }
        gestureInput("hello")
        expireCombiningGrace()
        assertEquals("hello", textBeforeCursor)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", textBeforeCursor)
    }

    @Test fun tapThenGestureCombiningWordStillAutospacesWhenGestureGateEnabled() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_AUTOSPACE_ONLY_AFTER_GESTURE, true)
            putBoolean(Settings.PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING, true)
        }

        chainInput("fire")
        gestureInput("firetruck")
        expireCombiningGrace()

        assertEquals("firetruck ", textBeforeCursor)
    }

    // Manual spacing is the primary Nintype-style mode: the word never auto-commits and stays
    // open until the user taps space. The fix lets a tap-built head extend into a swipe so
    // tap(s)+swipe build ONE word. Pre-fix, the merged-trail path was gated on the combining
    // grace timer (grace > 0), so under manual spacing the already-composed head ("he") was
    // concatenated AGAIN onto the gesture result -> "hehello". With the fix, the non-empty
    // tap trail arms the merged-trail path, prevTypedWord is dropped, and we get "hello".
    @Test fun manualSpacingTapThenGestureBuildsOneOpenWord() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }

        // Tap the head of the word; taps populate the WordComposer pointer trail.
        chainInput("he")
        assertEquals("he", composingText)

        // Swipe the rest. In real use the merged tap+swipe trail makes the recognizer emit the
        // whole word; here we inject that whole word. The fix must NOT prepend "he" again.
        gestureInput("hello")
        assertEquals("hello", composingText)
        assertEquals("hello", textBeforeCursor)

        // Manual spacing: no autospace happened; the word was open until this explicit space.
        input(' ')
        assertEquals("hello ", textBeforeCursor)
        assertEquals("", composingText)
    }

    // Gesture-then-tap under manual spacing must EXTEND the still-open word, not finalize it.
    // Pre-fix, `mAutospaceAfterGestureTyping` (on by default) set a post-gesture PHANTOM space
    // because the guard only excluded combining-grace mode (grace > 0), not manual spacing
    // (grace = 0). The next tap then committed the swiped word and autospaced before the new
    // letter -> "dea l" (or "sea l" once autocorrect was on) instead of "deal".
    @Test fun manualSpacingGestureThenTapExtendsWithoutAutospace() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING, true) // the trigger
        }

        gestureInput("dea")
        assertEquals("dea", composingText)
        assertEquals("dea", textBeforeCursor)

        input('l')
        // The word stays open and the tap appends: "deal", not "dea l".
        assertEquals("deal", composingText)
        assertEquals("deal", textBeforeCursor)
    }

    // Live-converge OFF (default): a tap after a swipe appends literally to the recognized
    // fragment. This documents the baseline the opt-in changes (on-device, the tap would instead
    // re-recognize the whole stroke). "RJ" stands in for a mis-resolved short swipe fragment.
    @Test fun liveConvergeOffAppendsTapLiterally() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }

        gestureInput("RJ")
        input('e')
        assertEquals("RJe", composingText)
        assertEquals("RJe", textBeforeCursor)
    }

    // Live-converge ON: in the JVM harness the native recognizer isn't loaded and tap events
    // carry no key coordinates, so the feature must degrade gracefully — fall back to a literal
    // append, never lose the tap, never crash. (The real re-recognition path is validated
    // on-device; it can't be exercised here without the gesture library.)
    @Test fun liveConvergeOnDegradesGracefullyWithoutRecognizer() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_MULTIPART_RERECOGNIZE_TAPS, true)
        }

        gestureInput("RJ")
        input('e')
        // Same literal fallback as OFF — the tap is preserved and the word stays open.
        assertEquals("RJe", composingText)
        assertEquals("RJe", textBeforeCursor)
    }

    @Test fun manualSpacingActivatesMultipartCompose() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }
        assertEquals(true, settingsValues.isMultipartComposeActive)

        // Neither manual spacing nor the grace timer -> multi-part composition is off.
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, false)
            putInt(Settings.PREF_COMBINING_GRACE_MS, 0)
            putBoolean(Settings.PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING, false)
        }
        assertEquals(false, settingsValues.isMultipartComposeActive)
    }

    // Whole-word delete of a word re-composed by backspacing into committed text must remove
    // exactly that word, not mash it into the preceding words. The bug was using
    // deleteTextBeforeCursor (which can't remove an active composing region and ate committed
    // text before it: "This is pretty cool" -> "This is precool"). Note: the JVM mock's
    // deleteSurroundingText doesn't reproduce the real-editor composing quirk, so this guards the
    // intended outcome / the commitText path rather than the editor-specific corruption itself.
    @Test fun wholeWordDeleteRemovesComposingWordWithoutMashingPrecedingText() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }
        setText("This is pretty cool ")
        functionalKeyPress(KeyCode.DELETE) // removes the trailing space, re-composes "cool"
        assertEquals("This is pretty cool", text)
        assertEquals("cool", composingText)

        functionalKeyPress(KeyCode.DELETE) // whole-word delete of "cool"
        assertEquals("This is pretty ", text)
        assertEquals("", composingText)
    }

    @Test fun wholeWordBackspaceDeletesManualSpacingComposingWord() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, false)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
        }

        chainInput("hello")
        assertEquals("hello", textBeforeCursor)
        assertEquals("hello", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("", textBeforeCursor)
        assertEquals("", composingText)
    }

    @Test fun wholeWordBackspaceWithLiveComposingDeleteOffFallsBackToOneCharacter() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, false)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, false)
        }

        chainInput("hello")
        assertEquals("hello", textBeforeCursor)
        assertEquals("hello", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("hell", textBeforeCursor)
        assertEquals("hell", composingText)
    }

    @Test fun fragmentBackspaceDeletesOnlySwipeFragment() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }

        gestureInput("fire")
        assertEquals("fire", textBeforeCursor)
        assertEquals("fire", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("", textBeforeCursor)
        assertEquals("", composingText)
    }

    @Test fun fragmentBackspaceDeletesLastSwipeFragmentInMultipartWord() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }

        gestureInput("fire")
        gestureInput("truck")
        assertEquals("firetruck", textBeforeCursor)
        assertEquals("firetruck", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("fire", textBeforeCursor)
        assertEquals("fire", composingText)
    }

    @Test fun fragmentBackspaceDeletesLastSwipeFragmentAfterAutospaceCommit() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, false)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }

        gestureInput("fire")
        gestureInput("truck")
        expireCombiningGrace()
        assertEquals("firetruck ", textBeforeCursor)
        assertEquals("", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("fire", textBeforeCursor)
        assertEquals("", composingText)

        functionalKeyPress(KeyCode.DELETE)

        assertEquals("", textBeforeCursor)
        assertEquals("", composingText)
    }

    @Test fun wholeWordBackspaceWithLiveComposingDeleteOnClearsComposingSpan() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, false)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }

        chainInput("hello")
        functionalKeyPress(KeyCode.DELETE)
        chainInput("hi")
        functionalKeyPress(KeyCode.DELETE)

        assertEquals("", textBeforeCursor)
        assertEquals("", composingText)
    }

    // --- Two-thumb typing: the merged-trail extend-base (WordComposer.mExtendBatchInputBase)
    // must never outlive the word it belonged to. Before the fix it was only cleared on a
    // normally-completing gesture, so an abnormal end (cancel / empty-top recognition) left it
    // armed, and NO deletion path cleared it. A later fresh swipe — most visibly at the start of
    // a text box — then merged with that ghost trail. Each test below arms the base, performs one
    // user action, and asserts the base is dropped. (We arm the base directly because the JVM
    // harness has no native recognizer to produce a real merged trail.) Each fails pre-fix. ---

    @Test fun extendBaseClearedByCharacterBackspace() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            // Character mode = neither fragment-pop nor whole-word delete. Both default ON
            // (Defaults.kt), so they must be explicitly disabled to exercise the per-char path.
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, false)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }
        chainInput("hello")
        armExtendBase()
        functionalKeyPress(KeyCode.DELETE) // character delete; word stays composing as "hell"
        assertEquals("hell", composingText)
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    @Test fun extendBaseClearedByFragmentBackspace() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }
        chainInput("hello") // each tap records a fragment boundary under manual spacing
        armExtendBase()
        functionalKeyPress(KeyCode.DELETE) // fragment pop
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    @Test fun extendBaseClearedByWholeWordBackspace() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }
        chainInput("hello")
        armExtendBase()
        functionalKeyPress(KeyCode.DELETE) // whole-word delete of the composing word
        assertEquals("", composingText)
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    @Test fun extendBaseClearedByDeleteSlider() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }
        chainInput("hello")
        armExtendBase()
        // The delete slider (swipe-from-backspace) routes through inputLogic.finishInput() in
        // KeyboardActionListenerImpl.onMoveDeletePointer / onUpWithDeletePointerActive.
        inputLogic.finishInput()
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    @Test fun extendBaseClearedByFreshGestureStart() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }
        // No composing word: the next gesture is fresh and must not inherit a leaked base.
        armExtendBase()
        inputLogic.onStartBatchInput(settingsValues, KeyboardSwitcher.getInstance(), latinIME.mHandler)
        handleMessages()
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    @Test fun extendBaseClearedByGestureCancel() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }
        armExtendBase()
        inputLogic.onCancelBatchInput(latinIME.mHandler)
        handleMessages()
        assertFalse(composer.isExtendBatchInputBaseSet)
    }

    // Static-seed reachability guard. PointerTracker's tap-seed path (sLastLetterTap*) is gated
    // on (!isMultipartComposeActive() && mCombiningGraceMs > 0). But grace > 0 forces multi-part
    // composition active, so that conjunction is unsatisfiable and the seed is currently
    // unreachable dead code. These pin the interlock: if a future settings refactor decouples
    // them and re-arms the seed, it must first add the stale-static cleanup (the seed statics are
    // process-global and never reset on delete / commit / field switch).
    @Test fun graceImpliesMultipartComposeActive_keepsSeedPathDead() {
        reset()
        latinIME.prefs().edit { putInt(Settings.PREF_COMBINING_GRACE_MS, 1000) }
        setText("") // force a settings reload
        assertTrue(settingsValues.isMultipartComposeActive)
    }

    @Test fun manualSpacingImpliesMultipartComposeActive() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true) }
        setText("")
        assertTrue(settingsValues.isMultipartComposeActive)
    }

    @Test fun forceAutoCapWorksWhenAutoCapIsOff() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_AUTO_CAP, false)
            putBoolean(Settings.PREF_FORCE_AUTO_CAPS, true)
        }
        setText("")
        assertEquals(TextUtils.CAP_MODE_SENTENCES, inputLogic.getCurrentAutoCapsState(settingsValues))
    }

    @Test fun forceAutoCapDoesNotOverridePasswordFields() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_AUTO_CAP, false)
            putBoolean(Settings.PREF_FORCE_AUTO_CAPS, true)
        }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertEquals(Constants.TextUtils.CAP_MODE_OFF, inputLogic.getCurrentAutoCapsState(settingsValues))
    }

    @Test fun noAutospaceInUrlField() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("example.net")
        assertEquals("example. net", text)
        lastAddedWord = ""
        setText("")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("example.net")
        assertEquals("", lastAddedWord)
        assertEquals("example.net", text)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceInUrlFieldWhenPickingSuggestion() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("exam")
        pickSuggestion("example")
        assertEquals("example", text)
        input('.')
        assertEquals("example.", text)
    }

    @Test fun noAutospaceForDetectedUrl() { // "light" version, should work without url detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("http://example.net")
        assertEquals("http://example.net", text)
        assertEquals("http", lastAddedWord)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceForDetectedEmail() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("mail@example.com")
        assertEquals("mail@example.com", text)
        assertEquals("mail@example", lastAddedWord) // todo: do we want this? not really nice, but don't want to be too aggressive with URL detection disabled
        assertEquals("com", composingText) // todo: maybe this should still see the whole address as a single word? or don't be too aggressive?
        setText("")
        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("mail@example.com")
        assertEquals("", lastAddedWord)
        assertEquals("mail@example.com", composingText)
    }

    @Test fun urlDetectionThings() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("...h")
        assertEquals("...h", text)
        assertEquals("h", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("bla..", text)
        assertEquals("", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.c")
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, true) }
        input("bla")
        input('.')
        functionalKeyPress(KeyCode.SHIFT) // should remove the phantom space (in addition to normal effect)
        input('c')
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
    }

    @Test fun stripSeparatorsBeforeAddingToHistoryWithURLDetection() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("example.com.")
        assertEquals("example.com.", composingText)
        input(' ')
        assertEquals("example.com", lastAddedWord)
    }

    @Test fun dontSelectConsecutiveSeparatorsWithURLDetection() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("", composingText)
        assertEquals("bla..", text)
    }

    @Test fun selectDoesSelect() {
        reset()
        setText("this is some text")
        setCursorPosition(3, 8)
        assertEquals("s is ", text.substring(3, 8))
    }

    @Test fun noComposingForPasswordFields() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        input('a')
        input('b')
        assertEquals("", composingText)
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        input('.')
        input('c')
        assertEquals("", composingText)
    }

    @Test fun `don't select whole thing as composing word if URL detection disabled`() {
        reset()
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and selecting`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example.com", composingText)
        setText("http://bla.com http://example.com ")
        setCursorPosition(29) // between l and e
        assertEquals("example.com", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and typing`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://example.com")
        assertEquals("example.com", composingText)
    }

    @Test fun `don't add partial URL to history`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http:/") // just so lastAddedWord isn't set to http
        chainInput("/bla.com")
        assertEquals("", lastAddedWord)
    }

    @Test fun urlProperlySelected() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete com
        // todo: do we really want no composing text?
        //  probably not... try not to break composing
        assertEquals("", composingText)
        chainInput("net")
        assertEquals("example.net", composingText)
    }

    @Test fun urlProperlySelectedWhenNotDeletingFullTld() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete om
        // todo: this is a weird difference to deleting the full TLD (see urlProperlySelected)
        //  what do we want here? (probably consistency)
        assertEquals("example.c/here", composingText)
        chainInput("z")
        assertEquals("", composingText) // todo: this is a weird difference to deleting the full TLD
//        assertEquals("example.cz", composingText) // fails, but probably would be better than above
    }

    @Test fun dontCommitPartialUrlBeforeFirstPeriod() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        // type http://bla. -> bla not selected, but clearly url, also means http://bla is committed which we probably don't want
        chainInput("http://bla.")
        assertEquals("bla.", composingText)
    }

    @Test fun `intermediate commits in text field without protocol`() {
        reset()
        chainInput("bla.")
        assertEquals("bla", lastAddedWord)
        chainInput("com/")
        assertEquals("com", lastAddedWord)
        chainInput("img.jpg")
        assertEquals("img", lastAddedWord)
        assertEquals("jpg", composingText)
    }

    @Test fun `intermediate commit in text field without protocol and with URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.com/img.jpg")
        assertEquals("bla", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `only protocol commit in text field with protocol and URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol`() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol and URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord) // todo: somehow avoid?
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol`() {
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol and with URL detection`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `don't accidentally detect some other text fields as URI`() {
        // see comment in InputLogic.textBeforeCursorMayBeUrlOrSimilar
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
        chainInput("Hey,why")
        assertEquals("Hey, why", text)
    }

    @Test fun `URL detection does not trigger on non-words`() {
        // first make sure it works without URL detection
        reset()
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
        // then with URL detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
    }

    @Test fun `autospace after selecting a suggestion`() {
        reset()
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    @Test fun immediateAutospaceAfterSelectingSuggestionIsInsertedOnce() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_IMMEDIATE_AUTO_SPACE, true) }

        pickSuggestion("this")

        assertEquals("this ", text)
        assertEquals(SpaceState.DOUBLE, spaceState)
        input('b')
        assertEquals("this b", text)
    }

    @Test fun combiningRevertSpaceTakesPriorityOverImmediateSuggestionAutospace() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_IMMEDIATE_AUTO_SPACE, true) }
        InputLogic::class.java.getDeclaredField("mInsertTrailingSpaceAfterPick")
            .apply { isAccessible = true }
            .setBoolean(inputLogic, true)

        pickSuggestion("the")

        assertEquals("the ", text)
        assertEquals(SpaceState.NONE, spaceState)
    }

    @Test fun `autospace works in URL field when input isn't URL`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    // https://github.com/Helium314/HeliBoard/issues/215
    // https://github.com/Helium314/HeliBoard/issues/229
    @Test fun `autospace works in URL field when input isn't URL, also for multiple suggestions`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        pickSuggestion("is")
        assertEquals("this is", text)
        pickSuggestion("not")
        assertEquals("this is not", text)
        input('c')
        assertEquals("this is not c", text)
        assertEquals("c", composingText)
    }

    @Test fun `emoji is added to dictionary`() {
        // check both text and codepoint input
        reset()
        chainInput("hello ")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        reset()
        chainInput("hello ")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)

        reset()
        chainInput("hello ")
        input("why 🤗 ") // not added because it's not only emoji (input can come from pasting)
        assertEquals("hello", lastAddedWord)
    }

    @Test fun `emoji uses phantom space`() {
        // check both text and codepoint input
        reset()
        pickSuggestion("hi")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)
        assertEquals("hi \uD83E\uDD17", text)
        reset()
        pickSuggestion("hi")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        assertEquals("hi ${StringUtils.newSingleCodePointString(0x1F36D)}", text)
    }

    // https://github.com/Helium314/HeliBoard/issues/230
    @Test fun `no autospace after opening quotes`() {
        reset()
        chainInput("\"Hi\" \"h")
        assertEquals("\"Hi\" \"h", text)
        assertEquals("h", composingText)
        reset()
        chainInput("\"Hi\", \"h")
        assertEquals("\"Hi\", \"h", text)
        assertEquals("h", composingText)
    }

    @Test fun `autospace works in URL field when starting with quotes`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input("\"")
        pickSuggestion("this")
        input("i")
        assertEquals("\"this i", text)
    }

    @Test fun `double space results in period and space, and delete removes the period`() {
        reset()
        chainInput("hello")
        input(' ')
        input(' ')
        assertEquals("hello. ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", text)
    }

    @Test fun `no weird space inside multi-"`() {
        reset()
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)

        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)
    }

    @Test fun `autospace still happens after "`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if next word is in quotes`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"\"you\"")
        assertEquals("\"hello\" \"you\"", text)
    }

    @Test fun `autospace propagates over "`() {
        reset()
        input('"')
        pickSuggestion("hello")
        assertEquals(spaceState, SpaceState.PHANTOM) // picking a suggestion sets phantom space state
        chainInput("\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if nex word is in " and after comma`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\",\"you\"")
        assertEquals("\"hello\", \"you\"", text)
    }

    @Test fun `autospace in json editor`() {
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("{\"label\":\"")
        assertEquals("{\"label\": \"", text)
        input('c')
        assertEquals("{\"label\": \"c", text)
    }

    @Test fun `text input and delete`() {
        reset()
        input("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)

        reset()
        input("hello ")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `emoji text input and delete`() {
        reset()
        input("🕵🏼")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        input("\uD83D\uDD75\uD83C\uDFFC")
        input(' ')
        assertEquals("🕵🏼 ", text)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    // emoRegex update to unicode 16.0 was required, https://github.com/Helium314/HeliBoard/issues/1760
    @Test fun `emojis deleted one by one`() {
        reset()
        chainInput("\uD83E\uDEC6\uD83E\uDEC6\uD83E\uDEC6")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("\uD83E\uDEC6\uD83E\uDEC6", text)
    }

    @Test fun `revert autocorrect on delete`() {
        if (BuildConfig.BUILD_TYPE == "runTests") return // needs autocorrect dictionary, unavailable in JVM env; see #12
        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)

        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        latinIME.prefs().edit { putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, false) }
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `remove glide typing word on delete`() {
        reset()
        glideTypingInput("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        // todo: now we want some way to disable delete-all on backspace, either per setting or something else
        //  need to avoid getting into the mWordComposer.isBatchMode() part of handleBackspaceEvent
    }

    @Test fun timestamp() {
        reset()
        chainInput("hello")
        functionalKeyPress(KeyCode.TIMESTAMP)
        assertEquals(Calendar.getInstance().time.time.toDouble(),
            getTimestampFormatter(latinIME).parse(text.substring(5))!!.time.toDouble(), 1000.0)
    }

    @Test fun inlineEmojiSearchStart() {
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, ' '.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch(' '.code, ':'.code, ' '.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '.'.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, "🌍".codePoints().asSequence().last(), settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, 't'.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '3'.code, settingsValues))
    }

    @Test fun inlineEmojiSearchString() {
        assertEquals("test", InputLogic.getInlineEmojiSearchString(":test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(" :test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("t:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("6:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString("🌍:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(",:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString(":test\nt"))
        assertEquals("/48", InputLogic.getInlineEmojiSearchString("2606:127.0.0.1::/48")) // do we want this?
    }

    // ------- #21 backspace corpus ---------------------------------------------------
    // Golden-master regression safety net for the #31 backspace refactor.
    // Each test documents the behavior CONTRACT it locks; a failing test after refactor
    // identifies the regression. Run with:
    //   gradlew :app:testOfflineDebugUnitTest --tests "helium314.keyboard.latin.InputLogicTest.corpus*"
    //
    // GESTURE-ONLY behaviors NOT covered here (gesture recognizer needed):
    //  • Fragment pop on a COMMITTED (post-autospace) gesture word — relies on
    //    mLastGestureCommittedFragmentLengths; only populated by onCombiningGraceExpired.
    //  • mWordComposer.isBatchMode() whole-word delete — covered by `remove glide typing
    //    word on delete` above; batch mode is cleared before our fragment path is taken.
    // ---------------------------------------------------------------------------------

    /**
     * CONTRACT: DEFAULT mode — backspace removes exactly one character from the composing word.
     * Locks the `mWordComposer.applyProcessedEvent(event)` path (~line 2532 in InputLogic.java)
     * taken when no combining/fragment prefs are set.
     */
    @Test fun `corpus - default mode char-by-char backspace`() {
        reset()
        chainInput("hello")
        assertEquals("hello", composingText)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", textBeforeCursor)
        assertEquals("hell", composingText)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hel", textBeforeCursor)
        assertEquals("hel", composingText)

        // drain to empty — no crash, no negative length
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", textBeforeCursor)
        assertEquals("", text)
    }

    /**
     * CONTRACT: DEFAULT mode — first backspace after a committed word + trailing space removes
     * the space; subsequent backspaces shrink the re-composed word char by char.
     */
    @Test fun `corpus - default mode backspace after committed word and space`() {
        reset()
        chainInput("hello ")
        assertEquals("hello ", text)
        assertEquals("", composingText)

        // Removes the trailing space and re-composes "hello".
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
        assertEquals("hello", composingText)

        // Shrinks the re-composed word by one char.
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)
        assertEquals("hell", composingText)
    }

    /**
     * CONTRACT: DEFAULT mode — backspace from within multi-word committed text re-composes the
     * word immediately left of the cursor and trims it char-by-char; earlier words are NOT touched.
     */
    @Test fun `corpus - default mode backspace into committed word recomposes and shrinks`() {
        reset()
        setText("hello there ")
        assertEquals("", composingText)

        // DELETE removes trailing space; "there" is re-composed.
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)

        // Second DELETE shrinks "there" → "ther".
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ther", text)
        assertEquals("ther", composingText)

        // "hello " is never touched.
        assertTrue(text.startsWith("hello "))
    }

    /**
     * CONTRACT: RECOMPOSE CORRUPTION GUARD — combining whole-word delete must NOT mash the
     * composing word into the preceding committed text.
     *
     * The comment at InputLogic.java line ~2519 documents the bug this path fixes:
     *   deleteTextBeforeCursor(4) on composing "cool" in "This is pretty cool" routes through
     *   InputConnection.deleteSurroundingText which ignores the composing span and deletes
     *   committed text BEFORE it, yielding "This is precool".
     * The fix: mWordComposer.reset() + commitText("", 1) clears the composing span instead.
     *
     * Prefs to reach this path (else if at ~line 2515):
     *   PREF_COMBINING_GRACE_MS > 0, PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD = true,
     *   PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT = true.
     * The composing word "cool" is typed (not gestured) so !isBatchMode().
     */
    @Test fun `corpus - combining whole-word delete does not produce precool corruption`() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }

        // Type the full sentence; "cool" is the composing word after the last char.
        chainInput("This is pretty cool")
        assertEquals("This is pretty cool", textBeforeCursor)
        assertEquals("cool", composingText)

        // One backspace: whole composing word "cool" removed cleanly.
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("This is pretty ", textBeforeCursor)
        assertEquals("", composingText)
        // CORRUPTION GUARD: if deleteTextBeforeCursor were used instead of reset+commitText("",1),
        // this would be "This is precool".
        assertFalse(text.contains("precool"),
            "Corruption: 'precool' found — indicates deleteTextBeforeCursor was used instead of " +
            "mWordComposer.reset()+commitText(\"\",1). See InputLogic.java line ~2519.")
    }

    /**
     * CONTRACT: COMBINING whole-word delete mid-sentence — composing word removed in full;
     * preceding committed words survive intact (no word-mash).
     */
    @Test fun `corpus - combining whole-word delete mid-sentence leaves surrounding text intact`() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }

        // Seed editor with committed text; cursor at end → "world" is re-composed.
        setText("hello world")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", textBeforeCursor)
        assertFalse(text.contains("helloworld") || text.contains("hellworld"),
            "Word mash detected after combining whole-word delete")
    }

    /**
     * CONTRACT: FRAGMENT BACKSPACE — legacy MANUAL_SPACING mode, tap input.
     * With per-tap fragment tracking and gesture-word delete OFF, DELETE pops the last
     * fragment of the composing word. For tap input each fragment is one char, so the
     * observable effect is a char-by-char shrink. Locks that fragment-mode backspace does
     * NOT delete the whole word and does NOT mash into preceding text.
     *
     * Reaches tryFragmentBackspace: legacy tracking needs MANUAL_SPACING + FRAGMENT_BACKSPACE,
     * and DELETES_GESTURE_WORD must be false (else InputLogic ~line 1339 bails to whole-word
     * delete — which empties "hello" in one press). Asserts observable text only, not the
     * internal boundary list, so it survives the #31 input-unit-stack refactor.
     */
    @Test fun `corpus - fragment backspace legacy tap pops one fragment`() {
        reset()
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_GESTURE_MANUAL_SPACING, true)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }

        chainInput("hello")
        assertEquals("hello", composingText)

        // Fragment pop of a one-char fragment → "hell" (NOT whole-word delete to "").
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", textBeforeCursor)
        assertEquals("hell", composingText)

        // Continues shrinking one fragment per press, down to empty, with no word-mash.
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hel", composingText)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", textBeforeCursor)
        assertEquals("", composingText)
    }

    /**
     * CONTRACT (gesture-only, NOT JVM-reachable): in multipart combining mode a second
     * gesture EXTENDS the composing word, and DELETE pops the whole appended gesture
     * fragment atomically (e.g. "technology" → DELETE → "tech").
     *
     * @Ignore: the JVM harness cannot simulate combining-mode gesture extension. Two
     * successive gestureInput() calls compose two independent batch words, so
     * gestureInput("tech") then gestureInput("technology") yields composing
     * "techtechnology" (expected:<tech[]nology> but was:<tech[tech]nology>), not an
     * extended "technology". Real extension needs native batch timing + combining state
     * carried across strokes. Verify on-device; kept as executable contract documentation.
     */
    @Ignore("gesture-only: harness cannot simulate combining gesture-extension across strokes")
    @Test fun `corpus - fragment backspace pops gesture-sized fragment in multipart combining`() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_GESTURE_FRAGMENT_BACKSPACE, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, false)
        }
        gestureInput("tech")
        gestureInput("technology") // would extend on-device; appends in the harness
        assertEquals("technology", composingText)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("tech", composingText)

        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", composingText)
    }

    /**
     * CONTRACT: CURSOR-FRONT recompose path — when cursor is inside a composing word
     * (isCursorFrontOrMiddleOfComposingWord()), backspace first commits the word via
     * resetEntireInputState then removes one char from the committed text.  No surrounding
     * text is damaged.
     */
    @Test fun `corpus - cursor in middle of composing word resets then deletes one char`() {
        reset()
        chainInput("hello")
        assertEquals("hello", composingText)
        setCursorPosition(2) // cursor between 'e' and first 'l'

        // With cursor inside composing word: reset + delete char at position 2.
        // resetEntireInputState commits "hello" then deleteTextBeforeCursor(1) removes 'e'.
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hllo", text)
        // The word is re-composed after the delete.
        assertEquals("hllo", composingText)
    }

    /**
     * CONTRACT: MONOTONICITY INVARIANT — across a run of backspaces total text length is
     * non-increasing and characters from earlier committed words never spontaneously appear.
     * Guards against over-deletion or re-insertion bugs at word boundaries.
     */
    @Test fun `corpus - monotonicity repeated backspaces never increase text length`() {
        reset()
        chainInput("hello world")
        val initial = text.length
        assertTrue(initial > 0)

        var prev = initial
        // Stop at empty: the mock IC throws on delete-from-empty (real editors no-op),
        // which is not the behavior under test.
        var guard = initial + 5
        while (text.isNotEmpty() && guard-- > 0) {
            functionalKeyPress(KeyCode.DELETE)
            val cur = text.length
            assertTrue(cur <= prev,
                "Text grew after backspace: $prev → $cur  text='$text'")
            prev = cur
        }
        assertEquals("", text)
    }

    /**
     * CONTRACT: MONOTONICITY INVARIANT — combining whole-word delete mode.
     * commitText("",1) must never over-delete (removing chars from preceding words).
     */
    @Test fun `corpus - monotonicity combining whole-word delete never increases length`() {
        reset()
        latinIME.prefs().edit {
            putInt(Settings.PREF_COMBINING_GRACE_MS, 1000)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_GESTURE_WORD, true)
            putBoolean(Settings.PREF_COMBINING_BACKSPACE_DELETES_COMPOSING_TEXT, true)
        }

        chainInput("alpha beta gamma")
        val initial = text.length

        var prev = initial
        // Stop at empty: the mock IC throws on delete-from-empty (real editors no-op),
        // which is not the behavior under test.
        var guard = initial + 5
        while (text.isNotEmpty() && guard-- > 0) {
            functionalKeyPress(KeyCode.DELETE)
            val cur = text.length
            assertTrue(cur <= prev,
                "Text grew after backspace: $prev → $cur  text='$text'")
            prev = cur
        }
        assertEquals("", text)
    }
    private fun typeNoAssert(text: String) {
        text.forEach {
            latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(it.code))
            handleMessages()
        }
    }

    @Test fun testTextExpanderPlaceholders() {
        reset()
        // Enable text expander
        latinIME.prefs().edit().apply {
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_ENABLED, true)
            putBoolean(helium314.keyboard.latin.utils.TextExpanderUtils.PREF_IMMEDIATE, true)
        }.commit()

        // Define a shortcut
        val shortcuts = mapOf("exp" to helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry("Hi %cursor1%,your order %cursor2% is ready for %cursor3%.", ""))
        helium314.keyboard.latin.utils.TextExpanderUtils.saveShortcuts(latinIME, shortcuts)

        // Type the shortcut
        typeNoAssert("exp")

        // Type bob at %cursor1%
        typeNoAssert("bob")

        // Press ENTER to jump to %cursor2%
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(Constants.CODE_ENTER))
        handleMessages()

        // Type pizza at %cursor2%
        typeNoAssert("pizza")

        // Press ENTER to jump to %cursor3%
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(Constants.CODE_ENTER))
        handleMessages()

        // Type takeout at %cursor3%
        typeNoAssert("takeout")

        // Press ENTER (no more placeholders)
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(Constants.CODE_ENTER))
        handleMessages()

        assertEquals("Hi bob,your order pizza is ready for takeout.", getText())
    }



    // ------- helper functions ---------

    // should be called before every test, so the same state is guaranteed
    private fun reset() {
        // Drop messages left by asynchronous service setup or a previous scenario.
        messages.clear()
        delayedMessages.clear()

        // reset input connection & facilitator
        currentScript = ScriptUtils.SCRIPT_LATIN
        text = ""
        batchEdit = 0
        currentInputType = InputType.TYPE_CLASS_TEXT
        lastAddedWord = ""
        lastNgramContext = ""
        addedWords.clear()
        ngramContexts.clear()

        // reset settings
        latinIME.prefs().edit {
            clear()
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
        }

        setText("", requireIdle = false) // initializes the input connection before switching subtype
        latinIME.dictionaryFacilitator.waitForLoadingMainDictionaries(1, java.util.concurrent.TimeUnit.SECONDS)
        messages.clear()
        delayedMessages.clear()
        latinIME.switchToSubtype(SubtypeSettings.getResourceSubtypesForLocale("en_US".constructLocale())
            .first { it.languageTag == "en-US" })
        setText("", requireIdle = false) // (re)sets selection and composing word for the English subtype
        latinIME.dictionaryFacilitator.waitForLoadingMainDictionaries(1, java.util.concurrent.TimeUnit.SECONDS)
        messages.clear()
        delayedMessages.clear()
    }

    private fun chainInput(text: String) = text.forEach { input(it.code) }

    private fun input(char: Char) = input(char.code)

    private fun input(codePoint: Int) {
        require(codePoint > 0) { "not a codePoint: $codePoint" }
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val insert = StringUtils.newSingleCodePointString(codePoint)
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
        handleMessages()

        if (currentScript != ScriptUtils.SCRIPT_HANGUL // check fails if hangul combiner merges symbols
            && !(codePoint == Constants.CODE_SPACE && oldBefore.lastOrNull() == ' ') // check fails when 2 spaces are converted into a period
            && !latinIME.mInputLogic.suggestedWords.mWillAutoCorrect // autocorrect obviously creates inconsistencies
            ) {
            if (phantomSpaceToInsert.isEmpty())
                assertEquals(oldBefore + insert, textBeforeCursor)
            else // in some cases autospace might be suppressed
                assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        }
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getText())
        checkConnectionConsistency()
    }

    private fun functionalKeyPress(keyCode: Int) {
        require(keyCode < 0) { "not a functional key code: $keyCode" }
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        checkConnectionConsistency()
    }

    // almost the same as codePoint input, but calls different latinIME function
    private fun input(insert: String) {
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onTextInput(insert)
        handleMessages()

        if (phantomSpaceToInsert.isEmpty())
            assertEquals(oldBefore + insert, textBeforeCursor)
        else // in some cases autospace might be suppressed
            assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        assert(oldBefore + insert == textBeforeCursor || "$oldBefore $insert" == textBeforeCursor)
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getText())
        checkConnectionConsistency()
    }

    private fun getWordAtCursor() = connection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentScript)?.mWord

    private fun setCursorPosition(start: Int, end: Int = start, weirdTextField: Boolean = false) {
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.initialSelStart = start
        ei.initialSelEnd = end
        // imeOptions should not matter

        // adjust text in inputConnection first, otherwise fixLyingCursorPosition will move cursor
        // to the end of the text
        val fullText = textBeforeCursor + selectedText + textAfterCursor
        assertEquals(fullText, getText())

        // need to update ic before, otherwise when reloading text cache from ic, ric will load wrong text before cursor
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = start
        selectionEnd = end
        assertEquals(fullText, textBeforeCursor + selectedText + textAfterCursor)

        latinIME.onUpdateSelection(oldStart, oldEnd, start, end, composingStart, composingEnd)
        handleMessages()

        if (weirdTextField) {
            latinIME.mHandler.onStartInput(ei, true) // essentially does nothing
            latinIME.mHandler.onStartInputView(ei, true) // does the thing
            handleMessages()
        }

        assertEquals(fullText, getText())
        assertEquals(start, selectionStart)
        assertEquals(end, selectionEnd)
        checkConnectionConsistency()
    }

    // assumes we have nothing selected
    private fun getCursorPosition(): Int {
        assertEquals(cursor, connection.expectedSelectionStart)
        assertEquals(cursor, connection.expectedSelectionEnd)
        return cursor
    }

    // just sets the text and starts input so connection it set up correctly
    private fun setText(newText: String, requireIdle: Boolean = true) {
        text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingStart = -1

        // we need to start input to notify that something changed
        // restarting is false, so this is seen as a new text field
        val ei = EditorInfo()
        ei.inputType = currentInputType
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages() // this is important so the composing span is set correctly
        checkConnectionConsistency()
    }

    // like selecting a suggestion from strip
    private fun pickSuggestion(suggestion: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        latinIME.pickSuggestionManually(info)
        checkConnectionConsistency()
    }

    // only works when autocorrect is on, separator after word is required
    private fun getAutocorrectedWithSpaceAfter(suggestion: String, typedWord: String?) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        val typedInfo = SuggestedWordInfo(typedWord, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(sw) // this prepares for autocorrect
        input(' ')
        checkConnectionConsistency()
    }

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(settingsValues, sw, KeyboardSwitcher.getInstance())
    }

    private fun gestureInput(word: String) {
        latinIME.mInputLogic.onStartBatchInput(settingsValues, KeyboardSwitcher.getInstance(), latinIME.mHandler)
        glideTypingInput(word)
        handleMessages()
        checkConnectionConsistency()
    }

    private fun expireCombiningGrace() {
        combiningGraceExpired.invoke(inputLogic)
        handleMessages()
        checkConnectionConsistency()
    }

    // Arm the merged-trail extend-base with a non-empty trail, simulating the state left by a
    // prior gesture fragment. (A single empty InputPointers is treated as "clear", so two real
    // points are required for isExtendBatchInputBaseSet to become true.)
    private fun armExtendBase() {
        val base = InputPointers(8)
        base.addPointer(10, 20, 0, 0)
        base.addPointer(30, 40, 0, 0)
        composer.setExtendBatchInputBase(base)
        assertTrue(composer.isExtendBatchInputBaseSet)
    }

    private fun checkConnectionConsistency() {
        // RichInputConnection only has composing text up to cursor, but InputConnection has full composing text
        val expectedConnectionComposingText = if (composingStart == -1 || composingEnd == -1) ""
        else text.substring(composingStart, min(composingEnd, selectionEnd))
        assert(composingText.startsWith(expectedConnectionComposingText))
        // RichInputConnection only returns text up to cursor
        val textBeforeComposingText = if (composingStart == -1) textBeforeCursor else text.substring(0, composingStart)

        println("consistency: $selectionStart, ${connection.expectedSelectionStart}, $selectionEnd, ${connection.expectedSelectionEnd}, $textBeforeComposingText, " +
                "$connectionTextBeforeComposingText, $composingText, $connectionComposingText, $textBeforeCursor, ${connection.getTextBeforeCursor(textBeforeCursor.length, 0)}" +
                ", $textAfterCursor, ${connection.getTextAfterCursor(textAfterCursor.length, 0)}")
        assertEquals(selectionStart, connection.expectedSelectionStart)
        assertEquals(selectionEnd, connection.expectedSelectionEnd)
        assertEquals(textBeforeComposingText, connectionTextBeforeComposingText)
        assertEquals(expectedConnectionComposingText, connectionComposingText)
        assertEquals(textBeforeCursor, connection.getTextBeforeCursor(textBeforeCursor.length, 0).toString())
        assertEquals(textAfterCursor, connection.getTextAfterCursor(textAfterCursor.length, 0).toString())
    }

    private fun getText() =
        connection.getTextBeforeCursor(100, 0).toString() + (connection.getSelectedText(0) ?: "") + connection.getTextAfterCursor(100, 0)

    private fun setInputType(inputType: Int) {
        // set text to actually apply input type
        currentInputType = inputType
        setText(text)
    }

    // always need to handle messages for proper simulation
    private fun handleMessages(requireIdle: Boolean = true) {
        while (messages.isNotEmpty()) {
            latinIME.mHandler.handleMessage(messages.first())
            messages.removeAt(0)
        }
        while (delayedMessages.isNotEmpty()) {
            val msg = delayedMessages.first()
            if (msg.what != 2) // MSG_UPDATE_SUGGESTION_STRIP, we want to ignore it because it's irrelevant and has a 500 ms timeout
                latinIME.mHandler.handleMessage(delayedMessages.first())
            delayedMessages.removeAt(0)
            // delayed messages may post further messages, handle before next delayed message
            while (messages.isNotEmpty()) {
                latinIME.mHandler.handleMessage(messages.first())
                messages.removeAt(0)
            }
        }
        if (requireIdle) {
            assertEquals(0, messages.size)
            assertEquals(0, delayedMessages.size)
        }
    }


    // ---- A3: data-driven input-trace corpus -------------------------------------------------
    // Each scenario is an ordered list of replayable input events; the runner replays it through
    // the same simulated pipeline and asserts the resulting editor text (a golden-master baseline
    // of the two-thumb spacing / combining logic). Adding a regression case is a data row, not a
    // new test method. NOTE: gesture results are fed deterministically by the harness
    // (ShadowFacilitator), so this covers the input/spacing/combining LOGIC, not native glide
    // recognition (which needs on-device instrumented tests).
    private val twoThumbCorpus = listOf(
        Scenario("plain typing commits words",
            steps = listOf(Type("hello world")), expected = "hello world"),
        Scenario("combining grace commits and autospaces on expiry",
            prefs = mapOf(Settings.PREF_COMBINING_GRACE_MS to 1000),
            steps = listOf(Type("tech"), GraceExpire), expected = "tech "),
        Scenario("force-next-space after combining autospace does not double-space",
            prefs = mapOf(Settings.PREF_COMBINING_GRACE_MS to 1000),
            steps = listOf(Type("hello"), GraceExpire, Func(KeyCode.FORCE_NEXT_SPACE),
                Gesture("world"), GraceExpire),
            expected = "hello world"),
        Scenario("force-next-space during combining commits the space",
            prefs = mapOf(Settings.PREF_COMBINING_GRACE_MS to 1000),
            steps = listOf(Type("hello"), Func(KeyCode.FORCE_NEXT_SPACE), Gesture("world"), GraceExpire),
            expected = "hello world"),
        Scenario("join-next resumes the word for the next gesture",
            prefs = mapOf(
                Settings.PREF_COMBINING_GRACE_MS to 1000,
                Settings.PREF_MULTIPART_AUTO_EXTEND_IN_COMBINING to true),
            steps = listOf(Type("tech"), GraceExpire, Func(KeyCode.JOIN_NEXT),
                Gesture("technology"), GraceExpire),
            expected = "technology "),
    )

    @Test fun replayTwoThumbCorpus() {
        for (s in twoThumbCorpus) {
            reset()
            if (s.prefs.isNotEmpty()) latinIME.prefs().edit {
                for ((key, value) in s.prefs) when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    is Float -> putFloat(key, value)
                    else -> error("unsupported pref type for $key: $value")
                }
            }
            for (step in s.steps) when (step) {
                is SetField -> setText(step.text)
                is Type -> chainInput(step.text)
                is Gesture -> gestureInput(step.word)
                is Func -> functionalKeyPress(step.keyCode)
                GraceExpire -> expireCombiningGrace()
            }
            assertEquals(s.expected, textBeforeCursor, "[${s.name}] textBeforeCursor")
            if (s.expectedComposing != null)
                assertEquals(s.expectedComposing, composingText, "[${s.name}] composingText")
        }
    }
}

private var currentInputType = InputType.TYPE_CLASS_TEXT
private var currentScript = ScriptUtils.SCRIPT_LATIN
private lateinit var mainKeyboardView: MainKeyboardView
private val messages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
private val delayedMessages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
// inputconnection stuff
private var batchEdit = 0
private var text = ""
private var selectionStart = 0
private var selectionEnd = 0
private var composingStart = -1
private var composingEnd = -1
// convenience for access
private val textBeforeCursor get() = text.substring(0, selectionStart)
private val textAfterCursor get() = text.substring(selectionEnd)
private val selectedText get() = text.substring(selectionStart, selectionEnd)
private val cursor get() = if (selectionStart == selectionEnd) selectionStart else -1

// composingText should return everything, but RichInputConnection.mComposingText only returns up to cursor
private val composingText get() = if (composingStart == -1 || composingEnd == -1) ""
    else text.substring(composingStart, composingEnd)

// essentially this is the text field we're editing in
private val ic = object : InputConnection {
    // pretty clear (though this may be slow depending on the editor)
    // bad return value here is likely the cause for that weird bug improved/fixed by fixIncorrectLength
    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence = textBeforeCursor.take(p0)
    // pretty clear (though this may be slow depending on the editor)
    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence = textAfterCursor.take(p0)
    // pretty clear
    override fun getSelectedText(p0: Int): CharSequence? = if (selectionStart == selectionEnd) null
        else text.substring(selectionStart, selectionEnd)
    // inserts text at cursor (right?), and sets it as composing text
    // this REPLACES currently composing text (even if at a different position)
    // moves the cursor: positive means relative to composing text start, negative means relative to start
    override fun setComposingText(newText: CharSequence, cursor: Int): Boolean {
        // first remove the composing text if any
        if (composingStart != -1 && composingEnd != -1)
            text = text.substring(0, composingStart) + text.substring(composingEnd)
        else // no composing span active, we should remove selected text
            if (selectionStart != selectionEnd) {
                text = textBeforeCursor + textAfterCursor
                selectionEnd = selectionStart
            }
        // then set the new text at old composing start
        // if no composing start, set it at cursor position
        val insertStart = if (composingStart == -1) selectionStart else composingStart
        text = text.substring(0, insertStart) + newText + text.substring(insertStart)
        composingStart = insertStart
        composingEnd = insertStart + newText.length
        // the cursor -1 is not clear in documentation, but
        // "So a value of 1 will always advance you to the position after the full text being inserted"
        // means that 1 must be composingEnd
        selectionStart = if (cursor > 0) composingEnd + cursor - 1
            else -cursor
        selectionEnd = selectionStart
        // todo: this should call InputMethodManager#updateSelection(View, int, int, int, int)
        //  but only after batch edit has ended
        //  this is not used in RichInputMethodManager, but probably ends up in LatinIME.onUpdateSelection
        //  -> DO IT (though it will likely only trigger that belatedSelectionUpdate thing, it might be relevant)
        return true
    }
    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        println("setComposingRegion, $p0, $p1")
        composingStart = p0
        composingEnd = p1
        return true // never checked
    }
    // sets composing text empty, but doesn't change actual text
    override fun finishComposingText(): Boolean {
        composingStart = -1
        composingEnd = -1
        return true // always true
    }
    // as per documentation: "This behaves like calling setComposingText(text, newCursorPosition) then finishComposingText()"
    override fun commitText(p0: CharSequence, p1: Int): Boolean {
        setComposingText(p0, p1)
        finishComposingText()
        return true // whether we added the text
    }
    // just tells the text field that we add many updated, and that the editor should not
    // send status updates until batch edit ended (not actually used for this simulation)
    override fun beginBatchEdit(): Boolean {
        ++batchEdit
        return true // always true
    }
    // end a batch edit, but maybe there are multiple batch edits happening
    override fun endBatchEdit(): Boolean {
        if (batchEdit > 0)
            return --batchEdit == 0
        return false // returns true if there is still a batch edit ongoing
    }
    // should notify about cursor info containing composing text, selection, ...
    // todo: maybe that could be interesting, implement it?
    override fun requestCursorUpdates(p0: Int): Boolean {
        // we call this, but don't have onUpdateCursorAnchorInfo overridden in latinIME, so it does nothing
        // also currently we don't care about the return value
        return false
    }
    override fun setSelection(p0: Int, p1: Int): Boolean {
        selectionStart = p0
        selectionEnd = p1
        // todo: call InputMethodService.onUpdateSelection(int, int, int, int, int, int), but only after batch edit is done!
        return true
    }
    // delete beforeLength before cursor position, and afterLength after cursor position
    // chars, not codepoints or glyphs
    // todo: may delete only one half of a surrogate pair, but this should be avoided by RichInputConnection (maybe throw error)
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        // delete only before or after selection
        text = textBeforeCursor.substring(0, textBeforeCursor.length - beforeLength) +
                text.substring(selectionStart, selectionEnd) +
                textAfterCursor.substring(afterLength)

        // if parts of the composing span are deleted, shorten the span (set end to shorter)
        if (selectionStart <= composingStart) {
            composingStart -= beforeLength // is this correct?
            composingEnd -= beforeLength
        } else if (selectionStart <= composingEnd) {
            composingEnd -= beforeLength // is this correct?
        }
        if (selectionEnd <= composingStart) {
            composingStart -= afterLength
            composingEnd -= afterLength
        } else if (selectionEnd <= composingEnd) {
            composingEnd -= afterLength
        }
        // update selection
        selectionStart -= beforeLength
        selectionEnd -= beforeLength
        return true
    }
    override fun sendKeyEvent(p0: KeyEvent): Boolean {
        if (p0.action != KeyEvent.ACTION_DOWN) return true // only change the text on key down, like RichInputConnection does
        if (p0.keyCode == KeyEvent.KEYCODE_DEL) {
            if (selectionEnd == 0) return true // nothing to delete
            if (selectedText.isEmpty()) {
                text = text.substring(0, selectionStart - 1) + text.substring(selectionEnd)
                selectionStart -= 1
            } else {
                text = text.substring(0, selectionStart) + text.substring(selectionEnd)
            }
            selectionEnd = selectionStart
            return true
        }
        val textToAdd = when (p0.keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\n"
            KeyEvent.KEYCODE_DEL -> null
            KeyEvent.KEYCODE_UNKNOWN -> p0.characters
            else -> StringUtils.newSingleCodePointString(p0.unicodeChar)
        }
        if (textToAdd != null) {
            text = text.substring(0, selectionStart) + textToAdd + text.substring(selectionEnd)
            selectionStart += textToAdd.length
            selectionEnd = selectionStart
            composingStart = -1
            composingEnd = -1
        }
        return true
    }
    // implementation is only to work with getTextBeforeCursorAndDetectLaggyConnection
    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText {
        return ExtractedText().also {
            it.startOffset = 0
            it.selectionStart = selectionStart
            it.selectionEnd = selectionEnd
        }
    }
    // only effect is flashing, so whatever...
    override fun commitCorrection(p0: CorrectionInfo?): Boolean = true
    // implement only when necessary
    override fun getCursorCapsMode(p0: Int): Int = TODO("Not yet implemented")
    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean = TODO("Not yet implemented")
    override fun commitCompletion(p0: CompletionInfo?): Boolean = TODO("Not yet implemented")
    override fun performEditorAction(p0: Int): Boolean = true
    override fun performContextMenuAction(p0: Int): Boolean = TODO("Not yet implemented")
    override fun clearMetaKeyStates(p0: Int): Boolean = TODO("Not yet implemented")
    override fun reportFullscreenMode(p0: Boolean): Boolean = TODO("Not yet implemented")
    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean = TODO("Not yet implemented")
    override fun getHandler(): Handler = TODO("Not yet implemented")
    override fun closeConnection() = TODO("Not yet implemented")
    override fun commitContent(p0: InputContentInfo, p1: Int, p2: Bundle?): Boolean = TODO("Not yet implemented")
}

// Shadows are handled by Robolectric. @Implementation overrides built-in functionality.
// This is used for avoiding crashes (LocaleManagerCompat, InputMethodManager, KeyboardSwitcher)
// and for simulating system stuff (InputMethodService for controlling the InputConnection, which
// more or less is the contents of the text field), and for setting the current script in
// KeyboardSwitcher without having to care about InputMethodSubtypes

// could also extend LatinIME, it's not final anyway
@Implements(InputMethodService::class)
class ShadowInputMethodService {
    @Implementation
    fun getCurrentInputEditorInfo() = EditorInfo().apply {
        inputType = currentInputType
        // anything else?
    }
    @Implementation
    fun getCurrentInputConnection() = ic
    @Implementation
    fun isInputViewShown() = true // otherwise selection updates will do nothing
}

@Implements(Handler::class)
class ShadowHandler {
    @Implementation
    fun sendMessage(message: Message) {
        messages.add(message)
    }
    @Implementation
    fun sendMessageDelayed(message: Message, delay: Long) {
        delayedMessages.add(message)
    }
}

@Implements(KeyboardSwitcher::class)
class ShadowKeyboardSwitcher {
    @Implementation
    // basically only needed for null check
    fun getMainKeyboardView(): MainKeyboardView = mainKeyboardView
    @Implementation
    // only affects view
    fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitcher.KeyboardSwitchState) = Unit
    @Implementation
    // only affects view
    fun setOneHandedModeEnabled(enabled: Boolean) = Unit
    @Implementation
    fun getCurrentKeyboardScript() = currentScript
}

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator2 {
    @Implementation
    fun addToUserHistory(suggestion: String, wasAutoCapitalized: Boolean,
                         ngramContext: NgramContext, timeStampInSeconds: Long,
                         blockPotentiallyOffensive: Boolean) {
        lastAddedWord = suggestion
        lastNgramContext = ngramContext.extractPrevWordsContext()
        addedWords.add(suggestion)
        ngramContexts.add(lastNgramContext)
    }
    companion object {
        var lastAddedWord = ""
        var lastNgramContext = ""
        val addedWords = mutableListOf<String>()
        val ngramContexts = mutableListOf<String>()
    }
}

// ---- A3 input-trace corpus model (see InputLogicTest.replayTwoThumbCorpus) ----
private sealed interface TraceStep
private data class SetField(val text: String) : TraceStep   // seed the editor field
private data class Type(val text: String) : TraceStep       // tap each character in turn
private data class Gesture(val word: String) : TraceStep    // glide -> recognized word (shadow-fed)
private data class Func(val keyCode: Int) : TraceStep        // functional key (DELETE, JOIN_NEXT, ...)
private data object GraceExpire : TraceStep                  // combining-grace timeout fires

private data class Scenario(
    val name: String,
    val prefs: Map<String, Any> = emptyMap(),
    val steps: List<TraceStep>,
    val expected: String,                  // expected textBeforeCursor after replaying steps
    val expectedComposing: String? = null, // optional: expected composing text
)
