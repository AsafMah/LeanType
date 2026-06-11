/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.latin.common.CoordinateUtils;

/**
 * Debug overlay (#A11) that shows the spacing-policy signals for the most recent combining-mode
 * arm, drawn in the bottom-left corner of the keyboard area on top of all other previews.
 *
 * <p>Gated behind {@code PREF_GESTURE_DEBUG_DRAW_POINTS}; no new preference required.
 *
 * <p>Displayed as decision-first, human-readable text:
 * <ul>
 *   <li>{@code FAST 300ms} – finished word, shorter timer</li>
 *   <li>{@code WAIT 620ms} – many continuations, longer timer</li>
 *   <li>{@code INSTANT} / {@code PAUSE} – Assisted-tier gate labels once enabled</li>
 * </ul>
 *
 * <p>The snapshot string is built once per signal update (in {@link #update}), not per draw
 * frame, so the drawing path is allocation-free.
 *
 * <p>Integration point for the gate branch: pass a non-null {@code gate} string to
 * {@link #update} (e.g. {@code "timer"}, {@code "two-gate"}) or call {@link #updateGate} to
 * re-stamp only the gate label without resetting the other signals.
 */
public final class SpacingInsightDrawingPreview extends AbstractDrawingPreview {

    private static final float TEXT_SIZE_SP       = 11f;  // scaled in setKeyboardViewGeometry
    private static final float PADDING_PX         = 8f;
    private static final long  LINGER_AFTER_CLEAR = 1800L; // keep visible long enough to read
    private static final int   BG_COLOR           = 0xDD1A1A2E;  // dark navy, ~87% opaque
    private static final int   TEXT_COLOR         = 0xFFFFFFFF;  // primary line
    private static final int   LABEL_COLOR        = 0xFFD0E8FF;  // detail line
    /** Gate label used when the caller passes {@code null}. */
    public static final String GATE_TIMER = "timer";
    /** Sentinel gate label for "no gate / idle". */
    public static final String GATE_NONE  = "none";

    private final Paint mBgPaint    = new Paint();
    private final Paint mTextPaint  = new Paint();
    private final Paint mLabelPaint = new Paint();

    // Keyboard area from setKeyboardViewGeometry – used for corner positioning.
    private int mKeyboardWidth;
    private int mKeyboardHeight;

    // Raw signal fields retained so gate branch can call updateGate() without re-supplying all.
    private boolean mComplete;
    private float   mPrefixRichScore;
    private int     mGraceMs;
    @Nullable private String mGate;

    /** Primary/detail readout lines; {@code null} primary means overlay draws nothing. */
    @Nullable private String mPrimary;
    @Nullable private String mDetail;
    private long mVisibleUntilMs;

    public SpacingInsightDrawingPreview() {
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(BG_COLOR);

        mTextPaint.setAntiAlias(true);
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setColor(TEXT_COLOR);
        mTextPaint.setTextSize(TEXT_SIZE_SP * 2.5f); // rough default; refined in setKeyboardViewGeometry

        mLabelPaint.setAntiAlias(true);
        mLabelPaint.setTypeface(Typeface.MONOSPACE);
        mLabelPaint.setColor(LABEL_COLOR);
        mLabelPaint.setTextSize(TEXT_SIZE_SP * 2.5f);
    }

    @Override
    public void setKeyboardViewGeometry(@NonNull final int[] originCoords,
            final int width, final int height) {
        super.setKeyboardViewGeometry(originCoords, width, height);
        mKeyboardWidth  = width;
        mKeyboardHeight = height;
        // Scale text relative to keyboard height so it stays readable at any DPI.
        final float textPx = Math.max(24f, height * 0.045f);
        mTextPaint.setTextSize(textPx);
        mLabelPaint.setTextSize(textPx);
    }

    /**
     * Push a new signal snapshot. Call from {@code InputLogic} each time the combining-mode
     * timer is armed. Call with {@code graceMs <= 0} to clear (e.g. on commit/cancel).
     *
     * @param complete        whether the current stem is a dictionary word
     * @param prefixRichScore fraction of suggestions that are prefix-completions [0..1]
     * @param graceMs         resolved grace duration (ms); {@code <= 0} clears the overlay
     * @param gate            active gate label; {@code null} renders as {@value #GATE_TIMER}
     */
    public void update(final boolean complete, final float prefixRichScore,
            final int graceMs, @Nullable final String gate) {
        if (graceMs <= 0) {
            // Don't disappear immediately on commit/cancel — leave the last decision readable.
            if (mPrimary != null) {
                mVisibleUntilMs = SystemClock.uptimeMillis() + LINGER_AFTER_CLEAR;
                invalidateDrawingView();
            }
            return;
        }
        mComplete        = complete;
        mPrefixRichScore = prefixRichScore;
        mGraceMs         = graceMs;
        mGate            = gate;
        buildSnapshot(complete, prefixRichScore, graceMs, gate);
        mVisibleUntilMs = SystemClock.uptimeMillis() + Math.max(LINGER_AFTER_CLEAR, graceMs + 800L);
        invalidateDrawingView();
    }

    /**
     * Re-stamp only the gate label on the current snapshot without resetting the signal
     * fields. No-op if there is no active snapshot (no live combining-mode arm).
     *
     * <p>This is the integration hook for the gate branch: call it as soon as the gate
     * decision is made to update the readout without waiting for the next keystroke.
     *
     * @param gate new gate label; {@code null} falls back to {@value #GATE_TIMER}
     */
    public void updateGate(@Nullable final String gate) {
        if (mPrimary == null) return;
        mGate = gate;
        buildSnapshot(mComplete, mPrefixRichScore, mGraceMs, gate);
        mVisibleUntilMs = SystemClock.uptimeMillis() + Math.max(LINGER_AFTER_CLEAR, mGraceMs + 800L);
        invalidateDrawingView();
    }

    private void buildSnapshot(final boolean complete, final float prefixRichScore,
            final int graceMs, @Nullable final String gate) {
        final int prefixPct = Math.round(prefixRichScore * 100f);
        final String gateLabel = gate == null ? GATE_TIMER : gate;

        if ("instant".equals(gateLabel)) {
            mPrimary = "INSTANT";
            mDetail = "finished word + low prefix";
        } else if ("pause".equals(gateLabel)) {
            mPrimary = "WAIT " + graceMs + "ms";
            mDetail = "many continuations · px " + prefixPct + "%";
        } else if (complete) {
            mPrimary = "FAST " + graceMs + "ms";
            mDetail = "finished word · px " + prefixPct + "%";
        } else if (prefixRichScore >= 0.50f) {
            mPrimary = "WAIT " + graceMs + "ms";
            mDetail = "many continuations · px " + prefixPct + "%";
        } else {
            mPrimary = "TIMER " + graceMs + "ms";
            mDetail = "not complete · px " + prefixPct + "%";
        }
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled()) return;
        final String primary = mPrimary;
        if (primary == null) return;
        if (SystemClock.uptimeMillis() > mVisibleUntilMs) {
            mPrimary = null;
            mDetail = null;
            return;
        }
        final String detail = mDetail == null ? "" : mDetail;

        final float textH  = mTextPaint.getTextSize();
        final float lineGap = Math.max(2f, textH * 0.18f);
        final float primaryW = mTextPaint.measureText(primary);
        final float detailW = mLabelPaint.measureText(detail);
        final float boxW = Math.min(mKeyboardWidth - PADDING_PX * 2f,
                Math.max(primaryW, detailW) + PADDING_PX * 2f);
        final float boxH = textH * 2f + lineGap + PADDING_PX * 2f;

        // Position: bottom-left of the keyboard area, inset enough to avoid clipping.
        final float left   = PADDING_PX;
        final float top    = Math.max(PADDING_PX, mKeyboardHeight - boxH - PADDING_PX);
        final float right  = left + boxW;
        final float bottom = top + boxH;

        canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, mBgPaint);
        canvas.drawText(primary, left + PADDING_PX, top + PADDING_PX + textH, mTextPaint);
        canvas.drawText(detail, left + PADDING_PX,
                top + PADDING_PX + textH * 2f + lineGap, mLabelPaint);
    }

    @Override
    public void onDeallocateMemory() {
        mPrimary = null;
        mDetail = null;
    }

    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        // Position is fixed (bottom-left of keyboard) — no tracker tracking needed.
    }
}
