/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.inputlogic;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.common.InputPointers;

import java.util.HashMap;
import java.util.Map;

/**
 * B7b (#99): synthesizes an "ideal" gesture trail for a composing prefix by tracing the prefix's
 * key centers, so the merged fake-track fed to the recognizer looks like one plausible whole-word
 * swipe. Replaces the raw prior-fragment trail when the {@code swipetest} build is active
 * (BuildConfig.FAKE_TRACK_V2).
 *
 * <p>Only the coordinates matter: {@link helium314.keyboard.latin.WordComposer#setBatchInputPointers}
 * re-times the base relative to the incoming stroke, so synthetic timestamps here are ignored.
 *
 * <p>A single-letter prefix (a tap) becomes a tiny "arrival" micro-stroke around the key center
 * rather than a lone point, which the single-finger recognizer handles far better than an isolated
 * tap coordinate.
 */
final class IdealPrefixTrailBuilder {

    private IdealPrefixTrailBuilder() {}

    /** Roughly one sample per (keyWidth / SPACING_DIVISOR) px along each inter-key segment. */
    private static final int SPACING_DIVISOR = 4;

    /**
     * @return a key-center trail for {@code word}, or {@code null} if it can't be built
     *         (empty word, no keyboard, or none of the letters map to keys).
     */
    static InputPointers build(final String word, final Keyboard keyboard) {
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
        for (int i = 0; i < len; i++) {
            final Key key = keyByCode.get(Character.toLowerCase(word.charAt(i)));
            if (key == null) continue; // skip unmapped chars (apostrophe, etc.)
            cx[count] = key.getX() + key.getWidth() / 2;
            cy[count] = key.getY() + key.getHeight() / 2;
            count++;
        }
        if (count == 0) return null;

        final InputPointers out = new InputPointers(64);
        if (count == 1) {
            // Tap prefix → small out-and-back micro-stroke so the recognizer sees a vertex.
            final int r = Math.max(1, (keyWidth > 0 ? keyWidth : 40) / 6);
            addPoint(out, cx[0] - r, cy[0]);
            addPoint(out, cx[0], cy[0]);
            addPoint(out, cx[0] + r, cy[0]);
            addPoint(out, cx[0], cy[0]);
            return out;
        }

        final int step = Math.max(1, (keyWidth > 0 ? keyWidth : 40) / SPACING_DIVISOR);
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
        // pointerId 0, time 0: WordComposer.setBatchInputPointers re-synthesizes timestamps.
        out.addPointer(x, y, 0, 0);
    }
}
