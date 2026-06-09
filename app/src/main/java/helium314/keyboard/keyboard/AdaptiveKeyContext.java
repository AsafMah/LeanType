/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.keyboard;

import helium314.keyboard.latin.SuggestedWords;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds a transient "likely next key" prior derived from the current suggestion strip, used to
 * gently enlarge the touch target of likely next keys (adaptive typing, see
 * docs/ADAPTIVE_TYPING.md).
 *
 * <p>It is rebuilt between keystrokes — whenever the suggestions change — on the UI thread, and
 * read per tap in {@link KeyDetector}. Reads are lock-free via {@code volatile} parallel arrays,
 * which are tiny (at most a handful of distinct next-characters), so the tap hot path stays fast
 * even for very fast typists. Because suggestions are computed asynchronously, the prior may lag
 * the latest keystroke by one tap under very fast typing; that is harmless, as the prior is only
 * a soft, capped bias.
 *
 * <p>Suggestions are weighted EQUALLY (averaged) rather than by score, so the result is not skewed
 * toward the single top suggestion.
 */
public final class AdaptiveKeyContext {
    /** How many suggestions to average over. */
    private static final int TOP_N = 5;

    private static volatile int[] sCodes;
    private static volatile float[] sWeights;

    /**
     * Optional observer notified whenever the prior changes, on the same (UI) thread that mutates
     * it. Used only by the debug overlay (see AdaptiveTargetsDrawingPreview) to repaint the live
     * keyboard as the prior shifts between keystrokes; null in normal operation. Volatile so the
     * keyboard view can register/clear it from its own lifecycle without extra locking.
     */
    private static volatile Runnable sChangeListener;

    private AdaptiveKeyContext() {}

    /** Register (or clear, with {@code null}) the debug repaint observer. */
    public static void setChangeListener(final Runnable listener) {
        sChangeListener = listener;
    }

    private static void fireChanged() {
        final Runnable l = sChangeListener;
        if (l != null) l.run();
    }

    /**
     * Rebuild the prior from the top suggestions.
     *
     * @param words    the current suggestion strip contents.
     * @param position index of the NEXT character within each suggestion — the current
     *                 composing-word length while a word is being built, or 0 for a new word
     *                 (using next-word predictions, whose first letter is the likely next key).
     */
    public static void update(final SuggestedWords words, final int position) {
        if (words == null || words.isEmpty() || position < 0) {
            clear();
            return;
        }
        final int n = Math.min(TOP_N, words.size());
        final HashMap<Integer, Integer> tally = new HashMap<>();
        int considered = 0;
        for (int i = 0; i < n; i++) {
            final String w = words.getWord(i);
            if (w == null || position >= w.length()) continue; // typed word itself / too short
            final int cp = Character.toLowerCase(w.charAt(position));
            if (!Character.isLetter(cp)) continue;
            tally.merge(cp, 1, Integer::sum);
            considered++;
        }
        if (considered == 0) {
            clear();
            return;
        }
        final int[] codes = new int[tally.size()];
        final float[] weights = new float[tally.size()];
        int j = 0;
        for (final Map.Entry<Integer, Integer> e : tally.entrySet()) {
            codes[j] = e.getKey();
            weights[j] = (float) e.getValue() / considered; // 0..1, equal-weight average
            j++;
        }
        sCodes = codes;
        sWeights = weights;
        fireChanged();
    }

    public static void clear() {
        sCodes = null;
        sWeights = null;
        fireChanged();
    }

    /** Prior weight in [0, 1] for the given key code (0 if none / no prior). Case-insensitive:
     *  the prior stores lowercase next-characters, but a shifted keyboard reports uppercase key
     *  codes, so we fold to lowercase to match (otherwise the bias/overlay miss capital letters). */
    public static float weight(final int code) {
        final int[] c = sCodes;
        final float[] w = sWeights;
        if (c == null || w == null) return 0f;
        final int lower = Character.toLowerCase(code);
        for (int i = 0; i < c.length && i < w.length; i++) {
            if (c[i] == lower) return w[i];
        }
        return 0f;
    }

    public static boolean hasPrior() {
        return sCodes != null;
    }

    /** Human-readable snapshot of the current prior, e.g. {@code [e=0.60,o=0.40]}, for debug logs. */
    public static String debugString() {
        final int[] c = sCodes;
        final float[] w = sWeights;
        if (c == null || w == null) return "(none)";
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < c.length && i < w.length; i++) {
            if (i > 0) sb.append(',');
            sb.append((char) c[i]).append('=')
              .append(String.format(java.util.Locale.US, "%.2f", w[i]));
        }
        return sb.append(']').toString();
    }
}
