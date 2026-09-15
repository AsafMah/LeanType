// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.media

import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.utils.InputTypeUtils

internal object MediaPrivacy {
    // Editor flags can change without the keyboard's cached SettingsValues being rebuilt.
    fun isRestricted(alwaysIncognito: Boolean, editor: EditorInfo?): Boolean =
        alwaysIncognito || editor == null ||
            (editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 ||
            InputTypeUtils.isPasswordInputType(editor.inputType) ||
            InputTypeUtils.isVisiblePasswordInputType(editor.inputType)
}
