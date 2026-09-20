// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.AppQuirksManager
import helium314.keyboard.latin.utils.InputTypeUtils

internal object MediaPrivacy {
    // Editor flags and app profiles can change without rebuilding cached SettingsValues.
    fun isRestricted(alwaysIncognito: Boolean, editor: EditorInfo?): Boolean =
        alwaysIncognito || editor == null ||
            AppQuirksManager.isIncognitoApp(editor.packageName) ||
            (editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 ||
            InputTypeUtils.isPasswordInputType(editor.inputType) ||
            InputTypeUtils.isVisiblePasswordInputType(editor.inputType)
}
