// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic;

import java.util.ArrayList;
import java.util.List;

/**
 * One revertible input-unit stack for backspace (issue #31).
 *
 * <p>Consolidates the length bookkeeping that drives fragment- and whole-word-backspace, which
 * was previously three separate fields scattered across {@link InputLogic}:
 * <ul>
 *   <li><b>Composing side</b> — the cumulative fragment boundaries of the <em>active</em>
 *       composing word (one strictly-increasing entry per recorded gesture/tap fragment).</li>
 *   <li><b>Committed side</b> — the total length and per-fragment lengths of the <em>last
 *       committed</em> gesture word, so a backspace right after commit can pop the last fragment
 *       or delete the whole word.</li>
 * </ul>
 *
 * <p>This class owns only the unit-length bookkeeping. The editor side effects (composing-text
 * updates, {@code deleteTextBeforeCursor}, stats) and the policy decisions (when to record/pop)
 * stay in {@link InputLogic}. Behaviour is identical to the pre-extraction code; the value is a
 * single, separately unit-testable home for the corruption-prone boundary math.
 */
final class BackspaceUnitStack {

    /** Composing side: cumulative fragment boundaries (strictly increasing word lengths). */
    private final ArrayList<Integer> mComposingBoundaries = new ArrayList<>();

    /** Committed side: total length of the last committed gesture word (0 = none / tap-only). */
    private int mCommittedLength;
    /** Committed side: per-fragment lengths of the last committed gesture word. */
    private final ArrayList<Integer> mCommittedFragmentLengths = new ArrayList<>();

    // ===== composing side =====

    boolean hasComposingBoundaries() {
        return !mComposingBoundaries.isEmpty();
    }

    /**
     * Record a fragment boundary at the given composing-word length. No-op for a non-positive
     * length or a duplicate of the current top (the same fragment appended twice in quick
     * succession).
     */
    void recordComposingBoundary(final int len) {
        if (len <= 0) return;
        if (!mComposingBoundaries.isEmpty()
                && mComposingBoundaries.get(mComposingBoundaries.size() - 1) == len) {
            return;
        }
        mComposingBoundaries.add(len);
    }

    /** Drop all composing boundaries. Call after committing / resetting the composing word. */
    void clearComposing() {
        if (!mComposingBoundaries.isEmpty()) mComposingBoundaries.clear();
    }

    /**
     * Pop the most-recent composing fragment, given the current composing-word length.
     *
     * <p>Stale boundaries past {@code currentLen} are trimmed first. Returns the new word length
     * the composing word should shrink to: the previous boundary (or {@code 0} for a
     * single-fragment word) when the top marker is the current fragment end, or the top boundary
     * itself as a defensive fallback when the current fragment end was never recorded. Returns
     * {@code -1} when there is no fragment to pop (caller should fall through to char-delete).
     */
    int popComposingFragment(final int currentLen) {
        if (mComposingBoundaries.isEmpty()) return -1;
        while (!mComposingBoundaries.isEmpty()
                && mComposingBoundaries.get(mComposingBoundaries.size() - 1) > currentLen) {
            mComposingBoundaries.remove(mComposingBoundaries.size() - 1);
        }
        if (mComposingBoundaries.isEmpty()) return -1;
        final int lastBoundary = mComposingBoundaries.get(mComposingBoundaries.size() - 1);
        if (lastBoundary == currentLen) {
            // Top marker is the end of the current fragment: pop it and shrink to the previous
            // marker, or to 0 for a single-fragment word.
            mComposingBoundaries.remove(mComposingBoundaries.size() - 1);
            return mComposingBoundaries.isEmpty()
                    ? 0
                    : mComposingBoundaries.get(mComposingBoundaries.size() - 1);
        }
        // Defensive fallback for words whose current fragment end was not recorded.
        return lastBoundary;
    }

    /**
     * Build the per-fragment lengths (deltas) for a composing word of {@code currentLen} that is
     * about to be committed: a delta per in-range boundary, plus a trailing fragment for any tail
     * past the last boundary.
     */
    ArrayList<Integer> fragmentLengthsForCommit(final int currentLen) {
        final ArrayList<Integer> fragmentLengths = new ArrayList<>();
        if (currentLen <= 0) return fragmentLengths;
        int previousBoundary = 0;
        for (int i = 0; i < mComposingBoundaries.size(); ++i) {
            final int boundary = mComposingBoundaries.get(i);
            if (boundary <= previousBoundary || boundary > currentLen) continue;
            fragmentLengths.add(boundary - previousBoundary);
            previousBoundary = boundary;
        }
        if (previousBoundary < currentLen) {
            fragmentLengths.add(currentLen - previousBoundary);
        }
        return fragmentLengths;
    }

    // ===== committed side =====

    int committedLength() {
        return mCommittedLength;
    }

    /** A defensive copy of the committed fragment lengths (snapshot before a pop). */
    ArrayList<Integer> copyCommittedFragmentLengths() {
        return new ArrayList<>(mCommittedFragmentLengths);
    }

    /** Replace the committed gesture word: its total length and per-fragment lengths. */
    void setCommitted(final int length, final List<Integer> fragmentLengths) {
        mCommittedLength = length;
        mCommittedFragmentLengths.clear();
        mCommittedFragmentLengths.addAll(fragmentLengths);
    }

    /** Replace just the committed fragment lengths (after popping one fragment off the top). */
    void setCommittedFragmentLengths(final List<Integer> fragmentLengths) {
        mCommittedFragmentLengths.clear();
        mCommittedFragmentLengths.addAll(fragmentLengths);
    }

    /** Reset all committed-gesture state (total length + fragment lengths). */
    void clearCommitted() {
        mCommittedLength = 0;
        if (!mCommittedFragmentLengths.isEmpty()) mCommittedFragmentLengths.clear();
    }
}
