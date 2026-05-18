/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.os.Handler;
import android.os.Looper;

import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.InputPointers;

/**
 * This class arbitrates batch input.
 * An instance of this class holds a {@link GestureStrokeRecognitionPoints}.
 * And it arbitrates multiple strokes gestured by multiple fingers and aggregates those gesture
 * points into one batch input.
 */
public class BatchInputArbiter {
    public interface BatchInputArbiterListener {
        void onStartBatchInput();
        void onUpdateBatchInput(
                final InputPointers aggregatedPointers, final long moveEventTime);
        void onStartUpdateBatchInputTimer();
        void onEndBatchInput(final InputPointers aggregatedPointers, final long upEventTime);
    }

    // The starting time of the first stroke of a gesture input.
    private static long sGestureFirstDownTime;
    // The {@link InputPointers} that includes all events of a gesture input.
    private static final InputPointers sAggregatedPointers = new InputPointers(
            Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
    private static int sLastRecognitionPointSize = 0; // synchronized using sAggregatedPointers
    private static long sLastRecognitionTime = 0; // synchronized using sAggregatedPointers

    // ---- Two-thumb typing: autospace grace period (#1.2) ----
    // When the last finger of a gesture lifts and the user has configured a non-zero grace
    // window, we delay the actual commit (the "autospace grace period"). If another finger
    // comes down on a letter during the window, the gesture continues into the same composing
    // word (see {@link #continuePendingGesture}). If the user instead taps a non-letter
    // (e.g. space, punctuation), the commit is flushed synchronously so the next keystroke
    // lands AFTER the gesture word (see {@link #flushGrace}). If neither happens within the
    // window the deferred commit fires on its own.
    //
    // All access is on the keyboard view's UI thread (touch events + Handler posts to main
    // looper), so the static state needs no extra synchronization beyond the existing
    // {@code synchronized (sAggregatedPointers)} blocks.
    private static Handler sGraceHandler;
    private static Runnable sPendingGraceRunnable;
    // One-shot flag set by {@link #continuePendingGesture} and consumed by the very next
    // {@link #addDownEventPoint}. Tells the arbiter that the down event belongs to a
    // gesture that was already in progress (just emerging from a grace window), so we must
    // NOT reset {@link #sGestureFirstDownTime} — keeping elapsed-time stamps consistent with
    // the still-living {@link #sAggregatedPointers}.
    private static boolean sNextDownContinuesPendingGesture;

    /**
     * Functional interface (SAM) for the deferred-commit path of {@link #mayEndBatchInput}.
     * Implementations are expected to use only static collaborators of {@link PointerTracker}
     * (not per-instance state) because by the time a grace timer fires the originating
     * {@code PointerTracker} instance may already have been reused for a different finger.
     */
    public interface DeferredCommit {
        void commit(InputPointers aggregatedPointers, long upEventTime);
    }

    private final GestureStrokeRecognitionPoints mRecognitionPoints;

    public BatchInputArbiter(final int pointerId, final GestureStrokeRecognitionParams params) {
        mRecognitionPoints = new GestureStrokeRecognitionPoints(pointerId, params);
    }

    public void setKeyboardGeometry(final int keyWidth, final int keyboardHeight) {
        mRecognitionPoints.setKeyboardGeometry(keyWidth, keyboardHeight);
    }

    /**
     * Calculate elapsed time since the first gesture down.
     * @param eventTime the time of this event.
     * @return the elapsed time in millisecond from the first gesture down.
     */
    public int getElapsedTimeSinceFirstDown(final long eventTime) {
        return (int)(eventTime - sGestureFirstDownTime);
    }

    /**
     * Add a down event point.
     * @param x the x-coordinate of this down event.
     * @param y the y-coordinate of this down event.
     * @param downEventTime the time of this down event.
     * @param lastLetterTypingTime the last typing input time.
     * @param activePointerCount the number of active pointers when this pointer down event occurs.
     */
    public void addDownEventPoint(final int x, final int y, final long downEventTime,
            final long lastLetterTypingTime, final int activePointerCount) {
        // Two-thumb typing (#1.2): if the previous gesture was waiting on a grace window and
        // {@link #continuePendingGesture} just declared this down to be a continuation, leave
        // {@link #sGestureFirstDownTime} alone so the new pointer's elapsed-time stamps stay
        // on the SAME scale as the existing aggregated pointers. We consume the one-shot flag
        // here regardless of whether the {@code activePointerCount == 1} branch fires.
        final boolean continuingPrior = sNextDownContinuesPendingGesture;
        sNextDownContinuesPendingGesture = false;
        if (activePointerCount == 1 && !continuingPrior) {
            sGestureFirstDownTime = downEventTime;
        }
        final int elapsedTimeSinceFirstDown = getElapsedTimeSinceFirstDown(downEventTime);
        final int elapsedTimeSinceLastTyping = (int)(downEventTime - lastLetterTypingTime);
        mRecognitionPoints.addDownEventPoint(
                x, y, elapsedTimeSinceFirstDown, elapsedTimeSinceLastTyping);
    }

    /**
     * Add a move event point.
     * @param x the x-coordinate of this move event.
     * @param y the y-coordinate of this move event.
     * @param moveEventTime the time of this move event.
     * @param isMajorEvent false if this is a historical move event.
     * @param listener {@link BatchInputArbiterListener#onStartUpdateBatchInputTimer()} of this
     *     <code>listener</code> may be called if enough move points have been added.
     * @return true if this move event occurs on the valid gesture area.
     */
    public boolean addMoveEventPoint(final int x, final int y, final long moveEventTime,
            final boolean isMajorEvent, final BatchInputArbiterListener listener) {
        final int beforeLength = mRecognitionPoints.getLength();
        final boolean onValidArea = mRecognitionPoints.addEventPoint(
                x, y, getElapsedTimeSinceFirstDown(moveEventTime), isMajorEvent);
        if (mRecognitionPoints.getLength() > beforeLength) {
            listener.onStartUpdateBatchInputTimer();
        }
        return onValidArea;
    }

    /**
     * Determine whether the batch input has started or not.
     * @param listener {@link BatchInputArbiterListener#onStartBatchInput()} of this
     *     <code>listener</code> will be called when the batch input has started successfully.
     * @return true if the batch input has started successfully.
     */
    public boolean mayStartBatchInput(final BatchInputArbiterListener listener) {
        if (!mRecognitionPoints.isStartOfAGesture()) {
            return false;
        }
        synchronized (sAggregatedPointers) {
            sAggregatedPointers.reset();
            sLastRecognitionPointSize = 0;
            sLastRecognitionTime = 0;
            listener.onStartBatchInput();
        }
        return true;
    }

    /**
     * Add synthetic move event point. After adding the point,
     * {@link #updateBatchInput(long,BatchInputArbiterListener)} will be called internally.
     * @param syntheticMoveEventTime the synthetic move event time.
     * @param listener the listener to be passed to
     *     {@link #updateBatchInput(long,BatchInputArbiterListener)}.
     */
    public void updateBatchInputByTimer(final long syntheticMoveEventTime,
            final BatchInputArbiterListener listener) {
        mRecognitionPoints.duplicateLastPointWith(
                getElapsedTimeSinceFirstDown(syntheticMoveEventTime));
        updateBatchInput(syntheticMoveEventTime, listener);
    }

    /**
     * Determine whether we have enough gesture points to lookup dictionary.
     * @param moveEventTime the time of this move event.
     * @param listener {@link BatchInputArbiterListener#onUpdateBatchInput(InputPointers,long)} of
     *     this <code>listener</code> will be called when enough event points we have. Also
     *     {@link BatchInputArbiterListener#onStartUpdateBatchInputTimer()} will be called to have
     *     possible future synthetic move event.
     */
    public void updateBatchInput(final long moveEventTime,
            final BatchInputArbiterListener listener) {
        synchronized (sAggregatedPointers) {
            mRecognitionPoints.appendIncrementalBatchPoints(sAggregatedPointers);
            final int size = sAggregatedPointers.getPointerSize();
            if (size > sLastRecognitionPointSize && mRecognitionPoints.hasRecognitionTimePast(
                    moveEventTime, sLastRecognitionTime)) {
                listener.onUpdateBatchInput(sAggregatedPointers, moveEventTime);
                listener.onStartUpdateBatchInputTimer();
                // The listener may change the size of the pointers (when auto-committing
                // for example), so we need to get the size from the pointers again.
                sLastRecognitionPointSize = sAggregatedPointers.getPointerSize();
                sLastRecognitionTime = moveEventTime;
            }
        }
    }

    /**
     * Determine whether the batch input has ended successfully or continues.
     *
     * <p>When the last finger lifts and {@code graceMs > 0}, the actual commit is deferred by
     * that many milliseconds (the "autospace grace period", two-thumb typing feature #1.2).
     * During the grace window a follow-up pointer can call {@link #continuePendingGesture} to
     * keep typing the same word, or {@link #flushGrace} to commit immediately so the next
     * keystroke lands after it. If nothing happens within the window, {@code deferredCommit}
     * fires on the main looper.
     *
     * <p>The deferred path takes a {@link DeferredCommit} rather than going through the
     * {@code listener} because by the time a grace timer fires the originating
     * {@link PointerTracker} instance may already have been reused for a different finger —
     * the deferred commit must rely on static state only (see {@link DeferredCommit}).
     *
     * @param upEventTime the time of this up event.
     * @param activePointerCount the number of active pointers when this pointer up event occurs.
     * @param graceMs zero for today's immediate-commit behaviour; otherwise the grace window in ms.
     * @param listener gesture listener; receives {@code onEndBatchInput} immediately when
     *     {@code graceMs <= 0}. Not used by the deferred path.
     * @param deferredCommit invoked when a deferred commit fires (timer expired or grace was
     *     flushed). Ignored when {@code graceMs <= 0}. May be {@code null} only if the caller
     *     guarantees {@code graceMs <= 0}.
     * @return {@code true} only when this call committed the batch synchronously. {@code false}
     *     means either more fingers are still down OR a grace timer was scheduled — in both
     *     cases the gesture is logically still in progress.
     */
    public boolean mayEndBatchInput(final long upEventTime, final int activePointerCount,
            final int graceMs, final BatchInputArbiterListener listener,
            final DeferredCommit deferredCommit) {
        synchronized (sAggregatedPointers) {
            mRecognitionPoints.appendAllBatchPoints(sAggregatedPointers);
            if (activePointerCount != 1) {
                // Other fingers are still down — gesture continues, no commit yet.
                return false;
            }
            if (graceMs <= 0) {
                // Immediate-commit path — exact original behaviour.
                listener.onEndBatchInput(sAggregatedPointers, upEventTime);
                return true;
            }
            scheduleGraceFinish(upEventTime, graceMs, deferredCommit);
            return false;
        }
    }

    /**
     * Backwards-compatible overload used by call sites that don't opt into the autospace
     * grace period; behaves identically to the original method.
     */
    public boolean mayEndBatchInput(final long upEventTime, final int activePointerCount,
            final BatchInputArbiterListener listener) {
        return mayEndBatchInput(upEventTime, activePointerCount, 0, listener, null);
    }

    // ---- Grace-period helpers (two-thumb typing #1.2) ----

    /** @return {@code true} if a deferred batch-end commit is currently pending. */
    public static boolean isGracePending() {
        return sPendingGraceRunnable != null;
    }

    /**
     * Cancel any pending grace-period commit without committing. Intended for cleanup paths
     * (gesture cancellation, view teardown, …). After this call the next down on a fresh
     * gesture resets {@link #sGestureFirstDownTime} like today.
     *
     * @return {@code true} if a commit was pending and was canceled.
     */
    public static boolean cancelGrace() {
        if (sPendingGraceRunnable == null) {
            // Defensive: a stale continuation flag from an aborted continuation path could
            // still be set even if no runnable is pending; clear it so a brand-new gesture
            // starts cleanly.
            sNextDownContinuesPendingGesture = false;
            return false;
        }
        sGraceHandler.removeCallbacks(sPendingGraceRunnable);
        sPendingGraceRunnable = null;
        sNextDownContinuesPendingGesture = false;
        return true;
    }

    /**
     * Cancel a pending grace-period commit AND mark the next pointer-down as a continuation of
     * the gesture word that was waiting to commit. Use this when a follow-up finger lands on a
     * letter during the grace window — the deferred commit is dropped and the new pointer's
     * events flow into the existing {@link #sAggregatedPointers} as if no lift had happened.
     *
     * @return {@code true} if a commit was pending and was canceled. If {@code false}, the
     *     continuation flag is NOT set (no commit to continue from).
     */
    public static boolean continuePendingGesture() {
        if (sPendingGraceRunnable == null) return false;
        sGraceHandler.removeCallbacks(sPendingGraceRunnable);
        sPendingGraceRunnable = null;
        sNextDownContinuesPendingGesture = true;
        return true;
    }

    /**
     * Synchronously fire a pending grace-period commit. Used when a follow-up pointer goes
     * down on a non-letter key (space, punctuation, …) — we need to commit the gesture word
     * NOW so the keystroke that follows lands after it in the input field. No-op if no
     * commit was pending.
     */
    public static void flushGrace() {
        final Runnable pending = sPendingGraceRunnable;
        if (pending == null) return;
        sGraceHandler.removeCallbacks(pending);
        sPendingGraceRunnable = null;
        sNextDownContinuesPendingGesture = false;
        pending.run();
    }

    private static void scheduleGraceFinish(final long upEventTime, final int graceMs,
            final DeferredCommit deferredCommit) {
        // Defensive: cancel any previously-scheduled commit (shouldn't normally happen, but
        // re-entrant up-events from {@code onPhantomUpEvent} have surprised people before).
        cancelGrace();
        if (sGraceHandler == null) {
            sGraceHandler = new Handler(Looper.getMainLooper());
        }
        // Each scheduled runnable carries its own one-shot guard so we never double-commit
        // even if {@link #flushGrace} synchronously runs it while a Handler dispatch is in
        // flight (in practice {@code removeCallbacks} cleans the queue, but the explicit
        // guard makes the invariant easy to reason about).
        final Runnable runnable = new Runnable() {
            private boolean mConsumed;
            @Override
            public void run() {
                if (mConsumed) return;
                mConsumed = true;
                // Null the slot BEFORE invoking the commit so any re-entrant gesture activity
                // inside {@link DeferredCommit#commit} sees a clean "no pending grace" state.
                if (sPendingGraceRunnable == this) sPendingGraceRunnable = null;
                // A timer-fired commit is never a continuation — make sure the flag isn't
                // stale from a prior aborted continuation attempt.
                sNextDownContinuesPendingGesture = false;
                deferredCommit.commit(sAggregatedPointers, upEventTime);
            }
        };
        sPendingGraceRunnable = runnable;
        sGraceHandler.postDelayed(runnable, graceMs);
    }
}
