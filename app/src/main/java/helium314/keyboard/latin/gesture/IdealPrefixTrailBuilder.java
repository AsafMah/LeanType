/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.gesture;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.common.InputPointers;

import java.util.HashMap;
import java.util.Map;

/**
 * Synthesises an "ideal" gesture trail for a composing prefix by tracing the prefix's key centres,
 * so the merged stream handed to the decoder looks like one plausible whole-word swipe.
 *
 * <p>Motivation: the prior-fragment base that {@link StrokeAligner} prepends is otherwise the
 * <em>raw</em> trail of what came before — which is sparse and un-stroke-like for a tap (a single
 * coordinate) and noisy for a partial swipe. Replacing it with a clean key-centre path gives the
 * recognizer the shape it was trained on.
 *
 * <p><b>Tap promotion.</b> A single-letter prefix becomes a small out-and-back micro-stroke around
 * the key centre rather than a lone point, so the recognizer sees a vertex — this is the
 * "promote taps into small swipes" idea, and it is why an isolated tap coordinate no longer has to
 * masquerade as a stroke.
 *
 * <p>Only coordinates matter here: {@link StrokeAligner#merge} discards the base's timestamps and
 * re-synthesises them relative to the incoming stroke, so the times written below are placeholders.
 *
 * <p>Originally written for issue #99 (B7b) and gated to a side-by-side {@code swipetest} build;
 * it is now reachable at runtime via {@code PREF_STROKE_IDEAL_PREFIX}.
 */
public final class IdealPrefixTrailBuilder {

    private IdealPrefixTrailBuilder() {}

    /** Roughly one sample per (keyWidth / SPACING_DIVISOR) px along each inter-key segment. */
    private static final int SPACING_DIVISOR = 4;
    /** Micro-stroke radius for a tap prefix, as a fraction of key width. */
    private static final int TAP_ARC_RADIUS_DIVISOR = 6;
    /** Fallback key width when the keyboard reports none. */
    private static final int FALLBACK_KEY_WIDTH = 40;

    /**
     * @return a key-centre trail for {@code word}, or {@code null} if it can't be built — an empty
     *     word, no keyboard, or <em>any letter that isn't on this keyboard</em>. Returning null
     *     rather than a partial path matters: a hole in the synthetic trail is worse for
     *     recognition than the raw trail the caller falls back to.
     */
    public static InputPointers build(final String word, final Keyboard keyboard) {
        if (word == null || word.isEmpty() || keyboard == null) return null;

        final Map<Integer, Key> keyByCode = new HashMap<>();
        int keyWidth = 0;
        for (final Key key : keyboard.getSortedKeys()) {
            final int code = key.getCode();
            if (code <= 0 || key.isModifier() || !Character.isLetter(code)) continue;
            keyByCode.put(Character.toLowerCase(code), key);
            if (keyWidth == 0) keyWidth = key.getWidth();
        }
        if (keyByCode.isEmpty()) return null;

        final int len = word.length();
        final int[] cx = new int[len];
        final int[] cy = new int[len];
        int count = 0;
        for (int i = 0; i < len; ) {
            final int cp = word.codePointAt(i);
            i += Character.charCount(cp);
            final Key key = keyByCode.get(Character.toLowerCase(cp));
            if (key != null) {
                cx[count] = key.getX() + key.getWidth() / 2;
                cy[count] = key.getY() + key.getHeight() / 2;
                count++;
                continue;
            }
            if (Character.isLetter(cp) || Character.getType(cp) == Character.NON_SPACING_MARK) {
                // A letter we cannot place would leave a hole in the synthetic path, which is
                // worse than the raw trail. Bail out and let the caller fall back. Covers popup
                // letters, accented/combining forms and unsupported scripts.
                return null;
            }
            // Non-letters (apostrophes, digits, punctuation) are legitimately not on the trail.
        }
        if (count == 0) return null;

        final int effectiveKeyWidth = keyWidth > 0 ? keyWidth : FALLBACK_KEY_WIDTH;
        final InputPointers out = new InputPointers(64);

        if (count == 1) {
            // Tap prefix → small out-and-back micro-stroke so the recognizer sees a vertex
            // instead of an isolated point.
            final int r = Math.max(1, effectiveKeyWidth / TAP_ARC_RADIUS_DIVISOR);
            addPoint(out, cx[0] - r, cy[0]);
            addPoint(out, cx[0], cy[0]);
            addPoint(out, cx[0] + r, cy[0]);
            addPoint(out, cx[0], cy[0]);
            return out;
        }

        final int step = Math.max(1, effectiveKeyWidth / SPACING_DIVISOR);
        addPoint(out, cx[0], cy[0]);
        for (int i = 1; i < count; i++) {
            final int x0 = cx[i - 1], y0 = cy[i - 1], x1 = cx[i], y1 = cy[i];
            final double dist = Math.hypot(x1 - x0, y1 - y0);
            final int samples = Math.max(1, (int) (dist / step));
            for (int s = 1; s <= samples; s++) {
                final float t = (float) s / samples;
                addPoint(out, Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t));
            }
        }
        return out;
    }

    private static void addPoint(final InputPointers out, final int x, final int y) {
        // pointerId 0 / time 0: StrokeAligner re-stamps both when it merges the base.
        out.addPointer(x, y, StrokeAligner.BASE_POINTER_ID, 0);
    }
}
