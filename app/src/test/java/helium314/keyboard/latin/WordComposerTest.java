package helium314.keyboard.latin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.CoordinateUtils;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.define.DebugFlags;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class WordComposerTest {

    @Test
    public void testSetCursorPositionWithinWord() throws Exception {
        final WordComposer wordComposer = new WordComposer();

        // Initial state
        Field cursorPositionField = WordComposer.class.getDeclaredField("mCursorPositionWithinWord");
        cursorPositionField.setAccessible(true);
        assertEquals(0, cursorPositionField.getInt(wordComposer));

        // Set to a new value
        wordComposer.setCursorPositionWithinWord(5);

        // Verify state is updated via reflection
        assertEquals(5, cursorPositionField.getInt(wordComposer));

        // Test behavioral effects
        wordComposer.reset();

        // Create a composing word of size 3
        int[] codePoints = new int[] { 'a', 'b', 'c' };
        int[] coordinates = CoordinateUtils.newCoordinateArray(3, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        wordComposer.setComposingWord(codePoints, coordinates);

        assertTrue(wordComposer.isComposingWord());
        assertEquals(3, wordComposer.size());

        // Set cursor to front (0)
        wordComposer.setCursorPositionWithinWord(0);
        assertTrue(wordComposer.isCursorInFrontOfComposingWord());
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Set cursor to middle (1)
        wordComposer.setCursorPositionWithinWord(1);
        assertFalse(wordComposer.isCursorInFrontOfComposingWord());
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Set cursor to end (3)
        wordComposer.setCursorPositionWithinWord(3);
        assertFalse(wordComposer.isCursorInFrontOfComposingWord());
        assertFalse(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Test error condition for invalid cursor position
        boolean originalDebugState = DebugFlags.DEBUG_ENABLED;
        try {
            DebugFlags.DEBUG_ENABLED = true;
            // Set an out-of-bounds cursor position (4 > size 3)
            wordComposer.setCursorPositionWithinWord(4);
            try {
                wordComposer.isCursorFrontOrMiddleOfComposingWord();
                fail("Should throw RuntimeException for invalid cursor position when DEBUG_ENABLED is true");
            } catch (RuntimeException e) {
                // Expected exception
                assertTrue(e.getMessage().contains("Wrong cursor position"));
            }
        } finally {
            DebugFlags.DEBUG_ENABLED = originalDebugState;
        }
    }

    // Two-thumb typing: the merged-trail mechanism that lets tap(s)+swipe build one word.
    // A prior fragment's trail (the "extend base") is prepended to each new gesture's pointers
    // with re-timed coordinates so the native recognizer sees ONE continuous hand-drawn stroke
    // instead of an isolated fragment. This is the machinery the manual-spacing fix routes into.
    @Test
    public void testExtendBatchInputBaseMergesAndRetimes() {
        final WordComposer wordComposer = new WordComposer();

        // Prior fragment trail (e.g. the tapped "he" key centers) — 2 points.
        final InputPointers base = new InputPointers(16);
        base.addPointer(10, 20, 0, 0);
        base.addPointer(30, 40, 0, 0);

        // The new gesture's raw pointers — 3 points with real, increasing times.
        final InputPointers batch = new InputPointers(16);
        batch.addPointer(100, 200, 0, 1000);
        batch.addPointer(110, 210, 0, 1025);
        batch.addPointer(120, 220, 0, 1050);

        wordComposer.setExtendBatchInputBase(base);
        assertTrue(wordComposer.isExtendBatchInputBaseSet());

        wordComposer.setBatchInputPointers(batch);

        final InputPointers merged = wordComposer.getInputPointers();
        // base (2) + new gesture (3) = 5, fed to the recognizer as one continuous stroke.
        assertEquals(5, merged.getPointerSize());

        final int[] xs = merged.getXCoordinates();
        final int[] times = merged.getTimes();

        // Base coordinates come first, in order, untouched.
        assertEquals(10, xs[0]);
        assertEquals(30, xs[1]);
        // New gesture coordinates follow, untouched.
        assertEquals(100, xs[2]);
        assertEquals(110, xs[3]);
        assertEquals(120, xs[4]);

        // The new gesture's ORIGINAL times are preserved verbatim (appendAll).
        assertEquals(1000, times[2]);
        assertEquals(1025, times[3]);
        assertEquals(1050, times[4]);

        // The re-timed base sits strictly BEFORE the new gesture, and the whole stream is
        // monotonically increasing — that's what makes the recognizer treat it as a single
        // stroke rather than two distinct ones.
        for (int i = 1; i < merged.getPointerSize(); i++) {
            assertTrue("times must strictly increase at index " + i, times[i] > times[i - 1]);
        }
        assertTrue("base must end before the new gesture begins", times[1] < times[2]);
    }

    @Test
    public void testExtendBatchInputBaseEmptyIsNoOp() {
        final WordComposer wordComposer = new WordComposer();

        // An empty base must NOT arm the merge — this is exactly the situation in unit tests
        // where a prior fragment was a (test-injected) gesture with no real pointers, and is
        // what keeps the legacy concat path intact when there's no trail to merge.
        wordComposer.setExtendBatchInputBase(new InputPointers(4));
        assertFalse(wordComposer.isExtendBatchInputBaseSet());

        final InputPointers batch = new InputPointers(4);
        batch.addPointer(5, 6, 0, 700);
        batch.addPointer(7, 8, 0, 725);

        wordComposer.setBatchInputPointers(batch);

        final InputPointers result = wordComposer.getInputPointers();
        // No base set -> mInputPointers is exactly the new gesture, unchanged.
        assertEquals(2, result.getPointerSize());
        assertEquals(700, result.getTimes()[0]);
        assertEquals(725, result.getTimes()[1]);
    }
}
