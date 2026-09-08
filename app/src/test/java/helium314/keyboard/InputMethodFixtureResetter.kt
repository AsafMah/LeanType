// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import org.robolectric.internal.ShadowProvider

// Test-local Kotlin shadows are not annotation-processed, so register the reset hook explicitly.
class InputMethodFixtureResetter : ShadowProvider {
    override fun reset() = ShadowInputMethodManager2.reset()
    override fun getProvidedPackageNames(): Array<String> = emptyArray()
    override fun getShadows(): Collection<Map.Entry<String, String>> = emptyList()
}
