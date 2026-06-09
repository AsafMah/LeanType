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

    // Phase 1 (COMPOSING_WORD_SOURCE_OF_TRUTH.md): after a fragment-pop truncates the word, the
    // raw stroke buffer still holds the longer pre-pop geometry (reset() doesn't clear it; the
    // length doesn't shrink). seedInputPointersFromKeyCenters realigns it to the truncated word's
    // key centers, so a following swipe-extend merges with geometry that matches the text instead
    // of building an ever-longer garbage word.
    @Test
    public void testSeedInputPointersFromKeyCentersRealignsToText() {
        final WordComposer wordComposer = new WordComposer();

        // Stale 5-point stroke left by a prior, longer word.
        final InputPointers stale = new InputPointers(16);
        for (int i = 0; i < 5; i++) {
            stale.addPointer(i, i, 0, i * 10);
        }
        wordComposer.setBatchInputPointers(stale);
        assertEquals(5, wordComposer.getInputPointers().getPointerSize());

        // reset() deliberately does NOT clear mInputPointers — the stale buffer survives.
        wordComposer.reset();
        assertEquals(5, wordComposer.getInputPointers().getPointerSize());

        // Realign to a 2-char truncated word's key centers (CoordinateUtils format: x0,y0,x1,y1).
        final int[] codePoints = new int[] { 't', 'h' };
        final int[] coords = new int[] { 100, 200, 110, 210 };
        wordComposer.seedInputPointersFromKeyCenters(codePoints, coords);

        final InputPointers result = wordComposer.getInputPointers();
        assertEquals(2, result.getPointerSize());
        assertEquals(100, result.getXCoordinates()[0]);
        assertEquals(200, result.getYCoordinates()[0]);
        assertEquals(110, result.getXCoordinates()[1]);
        assertEquals(210, result.getYCoordinates()[1]);
    }

    // A key the layout can't resolve comes back as NOT_A_COORDINATE; feeding that to the
    // recognizer as a real point would warp the stroke toward (-1,-1). The seed must skip it.
    @Test
    public void testSeedInputPointersSkipsUnresolvableKeys() {
        final WordComposer wordComposer = new WordComposer();

        final InputPointers stale = new InputPointers(16);
        for (int i = 0; i < 5; i++) {
            stale.addPointer(i, i, 0, i * 10);
        }
        wordComposer.setBatchInputPointers(stale);

        // 3-char word whose middle key has no geometry on the current layout.
        final int[] codePoints = new int[] { 't', '\'', 'h' };
        final int[] coords = new int[] { 100, 200,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE,
                110, 210 };
        wordComposer.seedInputPointersFromKeyCenters(codePoints, coords);

        final InputPointers result = wordComposer.getInputPointers();
        assertEquals(2, result.getPointerSize());
        assertEquals(100, result.getXCoordinates()[0]);
        assertEquals(200, result.getYCoordinates()[0]);
        assertEquals(110, result.getXCoordinates()[1]);
        assertEquals(210, result.getYCoordinates()[1]);
    }

    // End-to-end simulation of the on-device "th ing backspace ing -> thinking" failure
    // (fragment-pop stale stroke). Replays the exact WordComposer call sequence InputLogic
    // makes: tap T,H -> arm extend base -> first gesture (24 pts, merged trail) ->
    // gesture-end rebuild as "Thing" -> backspace fragment-pop ("Th" + unsetBatchMode +
    // seed realignment) -> re-arm extend base -> second gesture (25 pts). The second
    // gesture's merged stroke must be base(2) + batch(25) = 27 points anchored at the
    // truncated word's key centers — NOT ~51 points carrying the popped fragment's
    // geometry. If this passes but the device still misbehaves, the contamination lives
    // outside WordComposer (threading / a caller repopulating the buffer).
    @Test
    public void testFragmentPopThenReswipeUsesSeededBaseNotStaleTrail() {
        final WordComposer wordComposer = new WordComposer();

        // Key centers for t and h (CoordinateUtils format).
        final int T_X = 520, T_Y = 84, H_X = 637, H_Y = 230;
        final int[] thCodePoints = new int[] { 't', 'h' };
        final int[] thCoords = new int[] { T_X, T_Y, H_X, H_Y };

        // 1. Taps T, H — the non-batch applyProcessedEvent path records tap coords.
        wordComposer.setComposingWord(thCodePoints, thCoords);
        assertEquals(2, wordComposer.getInputPointers().getPointerSize());

        // 2. First swipe starts: InputLogic#onStartBatchInput arms the merged-trail base
        //    from the composer's own pointers.
        wordComposer.setExtendBatchInputBase(wordComposer.getInputPointers());
        assertEquals(2, wordComposer.getExtendBatchInputBaseSize());

        // 3. First gesture ("ing"-shaped, 24 raw points) merges onto the base.
        final InputPointers firstGesture = new InputPointers(32);
        for (int i = 0; i < 24; i++) {
            firstGesture.addPointer(700 + i * 4, 100 + i * 6, 0, 2000 + i * 16);
        }
        wordComposer.setBatchInputPointers(firstGesture);
        assertEquals(26, wordComposer.getInputPointers().getPointerSize());

        // 4. Gesture end (onUpdateTailBatchInputCompleted): clear the base, rebuild the
        //    composer text as the recognized word. Pointers are deliberately untouched.
        wordComposer.setExtendBatchInputBase(null);
        wordComposer.setBatchInputWord("Thing");
        assertEquals(26, wordComposer.getInputPointers().getPointerSize());
        assertTrue(wordComposer.isBatchMode());

        // 5. Backspace fragment-pop (InputLogic#tryFragmentBackspace truncation branch):
        //    rebuild as the truncated word, leave batch mode, realign the stroke buffer
        //    to the truncated word's key centers.
        wordComposer.setBatchInputWord("Th");
        wordComposer.unsetBatchMode();
        wordComposer.seedInputPointersFromKeyCenters(thCodePoints, thCoords);
        assertEquals("after the pop the stroke buffer must hold ONLY the truncated word's"
                + " key centers", 2, wordComposer.getInputPointers().getPointerSize());

        // 6. Second swipe starts: re-arm the base from the (now seeded) pointers.
        wordComposer.setExtendBatchInputBase(wordComposer.getInputPointers());
        assertEquals("the re-armed base must be the 2-point seed, not the stale 26-point"
                + " pre-pop trail", 2, wordComposer.getExtendBatchInputBaseSize());

        // 7. Second gesture (re-swiped "ing", 25 raw points).
        final InputPointers secondGesture = new InputPointers(32);
        for (int i = 0; i < 25; i++) {
            secondGesture.addPointer(700 + i * 4, 100 + i * 6, 0, 9000 + i * 16);
        }
        wordComposer.setBatchInputPointers(secondGesture);

        final InputPointers merged = wordComposer.getInputPointers();
        assertEquals("merged stroke must be seed(2) + new gesture(25)",
                27, merged.getPointerSize());
        // The stroke is anchored at t and h key centers, then the new gesture follows.
        assertEquals(T_X, merged.getXCoordinates()[0]);
        assertEquals(H_X, merged.getXCoordinates()[1]);
        assertEquals(700, merged.getXCoordinates()[2]);
        // Times strictly increase across the whole synthetic stroke.
        final int[] times = merged.getTimes();
        for (int i = 1; i < merged.getPointerSize(); i++) {
            assertTrue("times must strictly increase at index " + i, times[i] > times[i - 1]);
        }
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
