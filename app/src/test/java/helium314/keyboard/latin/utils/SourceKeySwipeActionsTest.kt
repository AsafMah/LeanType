package helium314.keyboard.latin.utils

import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.SourceKeySwipeActions.Direction
import helium314.keyboard.latin.utils.SourceKeySwipeActions.SourceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceKeySwipeActionsTest {
    @Test
    fun parseAcceptsCanonicalSourceDirectionAndToolbarActionNames() {
        val bindings = SourceKeySwipeActions.parse(
            "ACTION_KEY:LEFT=JOIN_NEXT;PERIOD:UP=UNDO_WORD;COMMA:DOWN=FORCE_NEXT_SPACE"
        )

        assertEquals(ToolbarKey.JOIN_NEXT, bindings[SourceKey.ACTION_KEY to Direction.LEFT])
        assertEquals(ToolbarKey.UNDO_WORD, bindings[SourceKey.PERIOD to Direction.UP])
        assertEquals(ToolbarKey.FORCE_NEXT_SPACE, bindings[SourceKey.COMMA to Direction.DOWN])
    }

    @Test
    fun parseAcceptsUserFriendlySourceAliases() {
        val bindings = SourceKeySwipeActions.parse("ENTER:RIGHT=JOIN_NEXT;.:UP=UNDO_WORD;,:DOWN=FORCE_NEXT_SPACE")

        assertEquals(ToolbarKey.JOIN_NEXT, bindings[SourceKey.ACTION_KEY to Direction.RIGHT])
        assertEquals(ToolbarKey.UNDO_WORD, bindings[SourceKey.PERIOD to Direction.UP])
        assertEquals(ToolbarKey.FORCE_NEXT_SPACE, bindings[SourceKey.COMMA to Direction.DOWN])
    }

    @Test
    fun parseSkipsMalformedEntries() {
        val bindings = SourceKeySwipeActions.parse(
            "ACTION:LEFT=JOIN_NEXT;BAD:LEFT=JOIN_NEXT;PERIOD:BAD=UNDO_WORD;COMMA:UP=NO_SUCH_ACTION;garbage"
        )

        assertEquals(mapOf(SourceKey.ACTION_KEY to Direction.LEFT to ToolbarKey.JOIN_NEXT), bindings)
    }

    @Test
    fun hasBindingForSourceCanonicalizesEnterPeriodAndComma() {
        val spec = "ACTION:LEFT=JOIN_NEXT;PERIOD:UP=UNDO_WORD;COMMA:DOWN=FORCE_NEXT_SPACE"

        assertTrue(SourceKeySwipeActions.hasBindingForSource(Constants.CODE_ENTER, spec))
        assertTrue(SourceKeySwipeActions.hasBindingForSource(Constants.CODE_PERIOD, spec))
        assertTrue(SourceKeySwipeActions.hasBindingForSource(','.code, spec))
        assertFalse(SourceKeySwipeActions.hasBindingForSource('a'.code, spec))
    }
}
