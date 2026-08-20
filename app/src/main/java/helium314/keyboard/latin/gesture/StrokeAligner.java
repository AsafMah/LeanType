/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.gesture;

import helium314.keyboard.latin.common.InputPointers;

/**
 * Merges a multi-part word's prior-fragment trail ("the base") with the stroke currently being
 * gestured, into the single {@link InputPointers} stream handed to the gesture decoder.
 *
 * <p>This is the seam where the fork decides <em>what shape the decoder thinks the user drew</em>,
 * and it is deliberately parameterised so the alternatives can be A/B'd on device. See
 * {@code docs/TWO_THUMB_TEMPORAL_ALIGNMENT.md} for the measurements behind the defaults.
 *
 * <h3>The two modes</h3>
 *
 * <b>{@link Mode#CONNECTOR}</b> (default, the historical behaviour): everything is stamped with
 * pointer id 0, so the decoder sees one long single-pointer glide. The jump from the base's last
 * point to the new stroke's first point is an implicit "connector" that the decoder reads as real
 * movement — which is where hallucinated middle letters ({@code techcolony}) come from.
 *
 * <p><b>{@link Mode#DUAL_POINTER}</b>: the base keeps pointer id 0 and the current stroke gets
 * pointer id 1, so the two land in <em>separate</em> decoder tracks
 * ({@code ProximityInfoState[0]} and {@code [1]}). There is then no connector to hallucinate
 * across, and the decoder's built-in two-pointer search can spell the word by alternating between
 * the tracks. Measured in {@code jni/tests/replay/two_pointer_track_test.cpp}.
 *
 * <h3>Invariants this class must preserve</h3>
 *
 * <ol>
 *   <li><b>Track 0 must be non-empty.</b> {@code Suggest::initializeSearch} early-returns when
 *       {@code ProximityInfoState(0)} is unused, yielding zero suggestions. The base always takes
 *       id 0, and callers only reach the merge path with a non-empty base.</li>
 *   <li><b>Only ids 0 and 1 are ever emitted.</b> Anything higher reaches no track at all. With
 *       three or more fragments the older ones stay collapsed into track 0 (joined by connectors,
 *       exactly as today) and only the newest stroke gets track 1.</li>
 *   <li><b>Timestamps stay globally monotonic.</b> The decoder's speed and beeline features walk
 *       the <em>raw</em> arrays across the track boundary, so a decreasing timestamp there yields a
 *       negative duration and a garbage speed rate. The base is therefore always re-timed to end
 *       {@code gapBeforeNewMs} before the current stroke begins — in both modes.</li>
 *   <li><b>Ids are stable for a given raw index across incremental recognition.</b>
 *       {@code checkAndReturnIsContinuousSuggestionPossible} compares x/y/time but not pointer
 *       ids, so a point must never change track mid-gesture. The base is fixed for the duration of
 *       a gesture and the current stroke only grows at the tail, so this holds.</li>
 * </ol>
 *
 * <p>Called on the input path, so it allocates nothing beyond what {@link InputPointers} itself
 * needs to grow.
 */
public final class StrokeAligner {

    private StrokeAligner() {}

    /** Pointer id of the prior-fragment base. Must be 0 — see invariant 1. */
    public static final int BASE_POINTER_ID = 0;
    /** Pointer id of the in-flight stroke under {@link Mode#DUAL_POINTER}. */
    public static final int CURRENT_POINTER_ID = 1;

    public enum Mode {
        /** One merged single-pointer trail (historical behaviour). */
        CONNECTOR,
        /** Base on decoder track 0, current stroke on track 1. */
        DUAL_POINTER;

        /** Parses the stored preference value, falling back to {@link #CONNECTOR}. */
        public static Mode fromPrefValue(final String value) {
            if ("dual_pointer".equals(value)) return DUAL_POINTER;
            return CONNECTOR;
        }
    }

    /** Tunable knobs. Defaults reproduce the historical behaviour exactly. */
    public static final class Params {
        /** Inter-point interval when synthesising timestamps for the base. */
        public static final int DEFAULT_INTERVAL_MS = 25;
        /** Gap between the base's last synthetic point and the stroke's first real point. */
        public static final int DEFAULT_GAP_MS = 60;

        public final Mode mode;
        public final int basePointIntervalMs;
        public final int gapBeforeNewMs;

        public Params(final Mode mode, final int basePointIntervalMs, final int gapBeforeNewMs) {
            this.mode = mode == null ? Mode.CONNECTOR : mode;
            // Clamp to sane values: a non-positive interval would make the base's timestamps
            // non-increasing, which is exactly the negative-duration hazard invariant 3 exists to
            // avoid.
            this.basePointIntervalMs = Math.max(1, basePointIntervalMs);
            this.gapBeforeNewMs = Math.max(1, gapBeforeNewMs);
        }

        public static Params defaults() {
            return new Params(Mode.CONNECTOR, DEFAULT_INTERVAL_MS, DEFAULT_GAP_MS);
        }

        /**
         * The timing knobs are only surfaced in the UI for {@link Mode#DUAL_POINTER}, so
         * {@link Mode#CONNECTOR} pins them to the historical constants. Without this, tuning the
         * sliders in dual mode and switching back would silently leave "one joined trail" behaving
         * differently from how it always has.
         */
        int effectiveIntervalMs() {
            return mode == Mode.DUAL_POINTER ? basePointIntervalMs : DEFAULT_INTERVAL_MS;
        }

        int effectiveGapMs() {
            return mode == Mode.DUAL_POINTER ? gapBeforeNewMs : DEFAULT_GAP_MS;
        }
    }

    /**
     * Merge {@code base} and {@code current} into {@code out}, which is reset first.
     *
     * <p>The base's own timestamps are discarded and re-synthesised backwards from the current
     * stroke's first point, because base coordinates can come from taps (which carry a {@code 0}
     * time sentinel) or from an earlier gesture on an unrelated clock. Only the base's geometry is
     * meaningful.
     *
     * @param out receives the merged stream; must not alias {@code base} or {@code current}.
     * @param base prior fragments' trail. If empty, {@code current} is copied through unchanged.
     * @param current the stroke being gestured now.
     * @param params tuning knobs; {@code null} means {@link Params#defaults()}.
     */
    public static void merge(final InputPointers out, final InputPointers base,
            final InputPointers current, final Params params) {
        final Params p = params == null ? Params.defaults() : params;
        final int baseSize = base == null ? 0 : base.getPointerSize();
        final int currentSize = current == null ? 0 : current.getPointerSize();

        if (baseSize == 0 || currentSize == 0) {
            // Nothing to merge — a lone stroke keeps whatever pointer ids it already carries, so
            // genuinely simultaneous two-thumb input is untouched by this class.
            out.reset();
            if (currentSize > 0) {
                out.set(current);
            } else if (baseSize > 0) {
                out.set(base);
            }
            return;
        }

        final int[] baseX = base.getXCoordinates();
        final int[] baseY = base.getYCoordinates();
        final int intervalMs = p.effectiveIntervalMs();
        final int firstNewTime = current.getTimes()[0];
        final int baseLastTime = firstNewTime - p.effectiveGapMs();
        final int baseFirstTime = baseLastTime - (baseSize - 1) * intervalMs;

        out.reset();
        for (int i = 0; i < baseSize; i++) {
            out.addPointer(baseX[i], baseY[i], BASE_POINTER_ID, baseFirstTime + i * intervalMs);
        }

        if (p.mode != Mode.DUAL_POINTER) {
            out.appendAll(current, BASE_POINTER_ID);
            return;
        }
        // Dual-pointer: the current stroke normally takes track 1 wholesale. But if it is ITSELF a
        // simultaneous two-thumb stroke it already occupies both tracks, and flattening it onto
        // track 1 would destroy that structure. In that case keep its own ids and let the base
        // share track 0 — which reads coherently as "track 0 = this thumb plus the word so far".
        if (isMultiPointer(current)) {
            out.appendAllPreservingIds(current);
        } else {
            out.appendAll(current, CURRENT_POINTER_ID);
        }
    }

    /** @return true if {@code pointers} carries more than one distinct pointer id. */
    private static boolean isMultiPointer(final InputPointers pointers) {
        final int size = pointers.getPointerSize();
        if (size < 2) return false;
        final int[] ids = pointers.getPointerIds();
        final int first = ids[0];
        for (int i = 1; i < size; i++) {
            if (ids[i] != first) return true;
        }
        return false;
    }
}
