package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.SourceKeyActionTargets.SourceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Test
import java.util.Locale
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourceKeyActionTargetsTest {
    @Before
    fun setup() {
        Settings.init(ApplicationProvider.getApplicationContext<Context>())
        SourceKeyActionTargets.clearCache()
    }
    @Test
    fun parseAcceptsTargetListsForSourceKeys() {
        val targets = SourceKeyActionTargets.parse(
            "ACTION=JOIN_NEXT,FORCE_NEXT_SPACE,UNDO_WORD;PERIOD=UNDO_WORD;COMMA=EMOJI,SETTINGS"
        )

        assertEquals(
            listOf(ToolbarKey.JOIN_NEXT, ToolbarKey.FORCE_NEXT_SPACE, ToolbarKey.UNDO_WORD),
            targets[SourceKey.ACTION_KEY],
        )
        assertEquals(listOf(ToolbarKey.UNDO_WORD), targets[SourceKey.PERIOD])
        assertEquals(listOf(ToolbarKey.EMOJI, ToolbarKey.SETTINGS), targets[SourceKey.COMMA])
    }

    @Test
    fun parseAcceptsUserFriendlySourceAliases() {
        val targets = SourceKeyActionTargets.parse("ENTER=JOIN_NEXT;.=UNDO_WORD;,=FORCE_NEXT_SPACE")

        assertEquals(listOf(ToolbarKey.JOIN_NEXT), targets[SourceKey.ACTION_KEY])
        assertEquals(listOf(ToolbarKey.UNDO_WORD), targets[SourceKey.PERIOD])
        assertEquals(listOf(ToolbarKey.FORCE_NEXT_SPACE), targets[SourceKey.COMMA])
    }

    @Test
    fun parseSkipsMalformedEntriesAndUnknownActions() {
        val targets = SourceKeyActionTargets.parse(
            "ACTION=JOIN_NEXT,NO_SUCH_ACTION;BAD=JOIN_NEXT;PERIOD=NO_SUCH_ACTION;garbage"
        )

        assertEquals(mapOf(SourceKey.ACTION_KEY to listOf(ToolbarKey.JOIN_NEXT)), targets)
    }

    @Test
    fun hasTargetsCanonicalizesSupportedSourceCodes() {
        val spec = "ACTION=JOIN_NEXT;PERIOD=UNDO_WORD;COMMA=FORCE_NEXT_SPACE"

        assertTrue(SourceKeyActionTargets.hasTargetsForSource(Constants.CODE_ENTER, spec))
        assertTrue(SourceKeyActionTargets.hasTargetsForSource(Constants.CODE_PERIOD, spec))
        assertTrue(SourceKeyActionTargets.hasTargetsForSource(','.code, spec))
        assertFalse(SourceKeyActionTargets.hasTargetsForSource('a'.code, spec))
    }

    @Test
    fun popupKeysResolveTargetsToExistingToolbarKeyCodes() {
        val popupKeys = SourceKeyActionTargets.popupKeysForSource(
            Constants.CODE_ENTER,
            "ACTION=JOIN_NEXT,FORCE_NEXT_SPACE",
            Locale.US,
        )

        assertNotNull(popupKeys)
        assertEquals(2, popupKeys!!.size)
        assertEquals(getCodeForToolbarKey(ToolbarKey.JOIN_NEXT), popupKeys[0].mCode)
        assertEquals(getCodeForToolbarKey(ToolbarKey.FORCE_NEXT_SPACE), popupKeys[1].mCode)
    }
}
