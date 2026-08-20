/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common;

import androidx.annotation.NonNull;

// TODO: This class is not thread-safe.
public final class InputPointers {
    private static final boolean DEBUG_TIME = false;

    private final int mDefaultCapacity;
    private final ResizableIntArray mXCoordinates;
    private final ResizableIntArray mYCoordinates;
    private final ResizableIntArray mPointerIds;
    private final ResizableIntArray mTimes;

    public InputPointers(final int defaultCapacity) {
        mDefaultCapacity = defaultCapacity;
        mXCoordinates = new ResizableIntArray(defaultCapacity);
        mYCoordinates = new ResizableIntArray(defaultCapacity);
        mPointerIds = new ResizableIntArray(defaultCapacity);
        mTimes = new ResizableIntArray(defaultCapacity);
    }

    private void fillWithLastTimeUntil(final int index) {
        final int fromIndex = mTimes.getLength();
        // Fill the gap with the latest time.
        // See {@link #getTime(int)} and {@link #isValidTimeStamps()}.
        if (fromIndex <= 0) {
            return;
        }
        final int fillLength = index - fromIndex + 1;
        if (fillLength <= 0) {
            return;
        }
        final int lastTime = mTimes.get(fromIndex - 1);
        mTimes.fill(lastTime, fromIndex, fillLength);
    }

    public void addPointerAt(final int index, final int x, final int y, final int pointerId,
            final int time) {
        mXCoordinates.addAt(index, x);
        mYCoordinates.addAt(index, y);
        mPointerIds.addAt(index, pointerId);
        if (DEBUG_TIME) {
            fillWithLastTimeUntil(index);
        }
        mTimes.addAt(index, time);
    }

    public void addPointer(final int x, final int y, final int pointerId, final int time) {
        mXCoordinates.add(x);
        mYCoordinates.add(y);
        mPointerIds.add(pointerId);
        mTimes.add(time);
    }

    public void set(@NonNull final InputPointers ip) {
        mXCoordinates.set(ip.mXCoordinates);
        mYCoordinates.set(ip.mYCoordinates);
        mPointerIds.set(ip.mPointerIds);
        mTimes.set(ip.mTimes);
    }

    public void copy(@NonNull final InputPointers ip) {
        mXCoordinates.copy(ip.mXCoordinates);
        mYCoordinates.copy(ip.mYCoordinates);
        mPointerIds.copy(ip.mPointerIds);
        mTimes.copy(ip.mTimes);
    }

    /**
     * Append the times, x-coordinates and y-coordinates in the specified {@link ResizableIntArray}
     * to the end of this.
     * @param pointerId the pointer id of the source.
     * @param times the source {@link ResizableIntArray} to read the event times from.
     * @param xCoordinates the source {@link ResizableIntArray} to read the x-coordinates from.
     * @param yCoordinates the source {@link ResizableIntArray} to read the y-coordinates from.
     * @param startPos the starting index of the data in {@code times} and etc.
     * @param length the number of data to be appended.
     */
    public void append(final int pointerId, @NonNull final ResizableIntArray times,
            @NonNull final ResizableIntArray xCoordinates,
            @NonNull final ResizableIntArray yCoordinates, final int startPos, final int length) {
        if (length == 0) {
            return;
        }
        mXCoordinates.append(xCoordinates, startPos, length);
        mYCoordinates.append(yCoordinates, startPos, length);
        mPointerIds.fill(pointerId, mPointerIds.getLength(), length);
        mTimes.append(times, startPos, length);
    }

    /**
     * Shift to the left by elementCount, discarding elementCount pointers at the start.
     * @param elementCount how many elements to shift.
     */
    public void shift(final int elementCount) {
        mXCoordinates.shift(elementCount);
        mYCoordinates.shift(elementCount);
        mPointerIds.shift(elementCount);
        mTimes.shift(elementCount);
    }

    /**
     * Append all pointers from {@code other} to the end of this, forcing pointer id 0.
     *
     * <p>Historically this was the only merge path, which is why the decoder's second pointer
     * track was never populated by multi-part composition. Prefer
     * {@link #appendAll(InputPointers, int)} when the caller knows which track the points belong
     * to — see {@link helium314.keyboard.latin.gesture.StrokeAligner}.
     */
    public void appendAll(@NonNull final InputPointers other) {
        appendAll(other, 0);
    }

    /**
     * Append all pointers from {@code other} to the end of this, stamping them with
     * {@code pointerId}.
     *
     * <p>The native decoder keeps one {@code ProximityInfoState} per pointer id (two of them,
     * {@code MAX_POINTER_COUNT_G}) and each state ingests <em>only</em> the points carrying its own
     * id. So this argument decides which decoder track the appended stroke lands in. Ids outside
     * {@code [0, 1]} reach no track at all.
     */
    public void appendAll(@NonNull final InputPointers other, final int pointerId) {
        append(pointerId, other.mTimes, other.mXCoordinates, other.mYCoordinates, 0,
                other.getPointerSize());
    }

    /**
     * Append all pointers from {@code other}, keeping each point's own pointer id.
     *
     * <p>Used when {@code other} is already a genuine multi-pointer stroke whose track assignment
     * must survive the merge.
     */
    public void appendAllPreservingIds(@NonNull final InputPointers other) {
        final int length = other.getPointerSize();
        if (length == 0) {
            return;
        }
        mXCoordinates.append(other.mXCoordinates, 0, length);
        mYCoordinates.append(other.mYCoordinates, 0, length);
        mPointerIds.append(other.mPointerIds, 0, length);
        mTimes.append(other.mTimes, 0, length);
    }

    public void reset() {
        final int defaultCapacity = mDefaultCapacity;
        mXCoordinates.reset(defaultCapacity);
        mYCoordinates.reset(defaultCapacity);
        mPointerIds.reset(defaultCapacity);
        mTimes.reset(defaultCapacity);
    }

    public int getPointerSize() {
        return mXCoordinates.getLength();
    }

    @NonNull
    public int[] getXCoordinates() {
        return mXCoordinates.getPrimitiveArray();
    }

    @NonNull
    public int[] getYCoordinates() {
        return mYCoordinates.getPrimitiveArray();
    }

    @NonNull
    public int[] getPointerIds() {
        return mPointerIds.getPrimitiveArray();
    }

    /**
     * Gets the time each point was registered, in milliseconds, relative to the first event in the
     * sequence.
     * @return The time each point was registered, in milliseconds, relative to the first event in
     * the sequence.
     */
    @NonNull
    public int[] getTimes() {
        return mTimes.getPrimitiveArray();
    }

    @Override
    public String toString() {
        return "size=" + getPointerSize() + " id=" + mPointerIds + " time=" + mTimes
                + " x=" + mXCoordinates + " y=" + mYCoordinates;
    }
}
