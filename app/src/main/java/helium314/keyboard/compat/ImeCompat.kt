// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("DEPRECATION")

package helium314.keyboard.compat

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.settings.Settings

object ImeCompat {
    fun InputMethodService.switchInputMethod(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return switchToNextInputMethod(false)
        val window = window.window ?: return false
        val token = window.attributes.token ?: return false
        return RichInputMethodManager.getInstance().inputMethodManager.switchToNextInputMethod(token, false)
    }

    fun InputMethodService.shouldSwitchToOtherInputMethods(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return shouldOfferSwitchingToNextInputMethod()
        val settingsValues = Settings.getValues()
        val window = window.window ?: return settingsValues.mLanguageSwitchKeyToOtherImes
        val token = window.attributes.token ?: return settingsValues.mLanguageSwitchKeyToOtherImes
        return RichInputMethodManager.getInstance().inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
    }

    fun InputMethodService.switchInputMethodCompat(imiId: String) {
        val window = window.window
        val token = window?.attributes?.token
        if (token != null) {
            try {
                RichInputMethodManager.getInstance().inputMethodManager.setInputMethod(token, imiId)
                return
            } catch (e: Throwable) {
                // fallback
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchInputMethod(imiId)
        }
    }

    fun InputMethodService.switchInputMethodAndSubtypeCompat(imi: InputMethodInfo, subtype: InputMethodSubtype) {
        val window = window.window
        val token = window?.attributes?.token
        if (token != null) {
            try {
                RichInputMethodManager.getInstance().inputMethodManager.setInputMethodAndSubtype(token, imi.id, subtype)
                return
            } catch (e: Throwable) {
                // fallback
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchInputMethod(imi.id, subtype)
        } else {
            val fallbackToken = token ?: return
            try {
                RichInputMethodManager.getInstance().inputMethodManager.setInputMethod(fallbackToken, imi.id)
            } catch (e: Throwable) {}
        }
    }
}
