// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.AppQuirk
import helium314.keyboard.compat.AppQuirksManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaPrivacyTest {
    @Test fun appProfileIncognitoChangesAreRestrictedImmediately() {
        val editor = EditorInfo().apply {
            packageName = "media.privacy.fixture"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        try {
            assertFalse(MediaPrivacy.isRestricted(false, editor))
            AppQuirksManager.saveQuirk(AppQuirk(editor.packageName, forceIncognito = true))
            assertTrue(MediaPrivacy.isRestricted(false, editor))
            assertFalse(MediaPrivacy.isRestricted(false, EditorInfo().apply {
                packageName = "media.privacy.other"
                inputType = editor.inputType
            }))
            AppQuirksManager.saveQuirk(AppQuirk(
                editor.packageName, forceIncognito = true, forceNonIncognito = true
            ))
            assertTrue(MediaPrivacy.isRestricted(false, editor))
            AppQuirksManager.removeQuirk(editor.packageName)
            assertFalse(MediaPrivacy.isRestricted(false, editor))
        } finally {
            AppQuirksManager.removeQuirk(editor.packageName)
        }
    }

    @Test fun nonIncognitoProfileCannotRelaxMediaPrivacyRestrictions() {
        val editor = EditorInfo().apply {
            packageName = "media.privacy.fixture"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        try {
            AppQuirksManager.saveQuirk(AppQuirk(editor.packageName, forceNonIncognito = true))
            assertFalse(MediaPrivacy.isRestricted(false, editor))
            assertTrue(MediaPrivacy.isRestricted(true, editor))
            editor.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            assertTrue(MediaPrivacy.isRestricted(false, editor))
            editor.imeOptions = 0
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            assertTrue(MediaPrivacy.isRestricted(false, editor))
        } finally {
            AppQuirksManager.removeQuirk(editor.packageName)
        }
    }

    @Test fun sameInputTypeWithChangedPrivacyFlagIsRestrictedImmediately() {
        val ordinary = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        val noLearning = EditorInfo().apply {
            inputType = ordinary.inputType
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        assertFalse(MediaPrivacy.isRestricted(false, ordinary))
        assertTrue(MediaPrivacy.isRestricted(false, noLearning))
        assertFalse(MediaPrivacy.isRestricted(false, ordinary))
    }

    @Test fun passwordMissingEditorAndExplicitIncognitoNeverAllowMedia() {
        assertTrue(MediaPrivacy.isRestricted(false, null))
        assertTrue(MediaPrivacy.isRestricted(true, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }))
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )) {
            assertTrue(MediaPrivacy.isRestricted(false, EditorInfo().apply { inputType = type }))
        }
    }
}
