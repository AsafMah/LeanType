/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

/**
 * Maps raw {@link android.view.MotionEvent} pointer ids onto the dense track slots the native
 * gesture decoder actually reads.
 *
 * <p><b>Why this exists.</b> The in-tree native preprocessing keeps exactly
 * {@code MAX_POINTER_COUNT_G == 2} per-pointer tracks ({@code jni/src/defines.h}).
 * {@code DicTraverseSession} seeds track <i>i</i> with pointer id <i>i</i>, and
 * {@code ProximityInfoStateUtils::updateTouchPoints} keeps only points whose
 * {@code pointerIds[k] == i}. Two consequences follow:
 *
 * <ul>
 *   <li>If <b>no</b> point carries id 0, track 0 is unused and {@code Suggest::initializeSearch}
 *       returns immediately — the gesture yields <b>zero suggestions</b>. This is reachable in
 *       ordinary two-thumb use: thumb A goes down (id 0), thumb B goes down (id 1), thumb A lifts,
 *       and thumb B swipes on alone still carrying id 1.</li>
 *   <li>Any id {@code >= 2} reaches no track at all and is silently discarded.</li>
 * </ul>
 *
 * <p>Android assigns pointer ids as the lowest currently-free index, so ids are neither guaranteed
 * to start at 0 for a given stroke nor to be contiguous. This class removes that dependency by
 * renumbering ids in <b>first-seen order</b> within a gesture: the first pointer to contribute
 * becomes slot 0, the second becomes slot 1, and so on.
 *
 * <p>For the overwhelmingly common cases the mapping is the identity (single finger 0 → 0; two
 * fingers 0,1 → 0,1), so this is a no-op in normal use and only repairs the broken cases. Slots
 * {@code >= 2} are still dropped by the native side exactly as before — this class deliberately
 * does not merge a third finger into an existing track, which would change recognition behaviour.
 *
 * <p>Not thread-safe: like the rest of the batch-input machinery it is only touched from the
 * keyboard view's UI thread.
 */
public final class PointerIdNormalizer {

    /** Beyond this many distinct pointers in one gesture we stop renumbering and pass ids through. */
    private static final int MAX_TRACKED_POINTERS = 8;

    private final int[] mRawIds = new int[MAX_TRACKED_POINTERS];
    private int mCount;

    /** Forget every mapping; call at the start of each gesture. */
    public void reset() {
        mCount = 0;
    }

    /**
     * @return the dense slot for {@code rawPointerId}, allocating one in first-seen order if this
     *     is the first time the id is seen in the current gesture. Returns {@code rawPointerId}
     *     unchanged if more than {@link #MAX_TRACKED_POINTERS} distinct pointers appear (which the
     *     decoder would discard anyway).
     */
    public int slotFor(final int rawPointerId) {
        for (int i = 0; i < mCount; i++) {
            if (mRawIds[i] == rawPointerId) {
                return i;
            }
        }
        if (mCount >= MAX_TRACKED_POINTERS) {
            return rawPointerId;
        }
        mRawIds[mCount] = rawPointerId;
        return mCount++;
    }

    /** @return how many distinct pointers have been seen since the last {@link #reset()}. */
    public int trackedPointerCount() {
        return mCount;
    }
}
