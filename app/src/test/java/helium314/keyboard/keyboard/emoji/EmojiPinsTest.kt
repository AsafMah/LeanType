// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class EmojiPinsTest {
    @Test fun pinnedVariantPersistsWithoutChangingSkinTonePreference() {
        val context = RuntimeEnvironment.getApplication()
        val pins = EmojiPins(context)
        val variant = "\uD83D\uDC4D\uD83C\uDFFD"
        pins.toggle(variant)
        pins.toggle("\u2764\uFE0F")
        assertEquals(listOf("\u2764\uFE0F", variant), EmojiPins(context).items())
        pins.toggle("\u2764\uFE0F")
        assertEquals(listOf(variant), EmojiPins(context).items())
    }

    @Test fun boundedPinsEvictOldestAndRejectOversizedStrings() {
        val pins = EmojiPins(RuntimeEnvironment.getApplication())
        repeat(60) { pins.toggle(String(Character.toChars(0x1f600 + it))) }
        assertEquals(50, pins.items().size)
        assertEquals(String(Character.toChars(0x1f600 + 59)), pins.items().first())
        assertFailsWith<IllegalArgumentException> { pins.toggle("x".repeat(65)) }
    }
}
