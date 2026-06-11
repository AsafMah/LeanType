/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

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
 * <p>Displayed fields:
 * <ul>
 *   <li>{@code complete} – whether the typed stem is a known dictionary word (Y/N)</li>
 *   <li>{@code prefix} – fraction of candidates that are completions of this stem [0.00..1.00]</li>
 *   <li>{@code grace} – resolved grace duration in milliseconds</li>
 *   <li>{@code gate} – which gate controls the commit; defaults to {@code timer} for the
 *       single-timer model. The two-gate branch can pass its own label via
 *       {@link #update(boolean, float, int, String)}.</li>
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

    private static final float TEXT_SIZE_SP  = 11f;  // scaled in setKeyboardViewGeometry
    private static final float PADDING_PX    = 6f;
    private static final int   BG_COLOR      = 0xCC1A1A2E;  // dark navy, 80% opaque
    private static final int   TEXT_COLOR    = 0xFFD0E8FF;  // soft blue-white
    private static final int   LABEL_COLOR   = 0xFF90B8D8;  // dimmer for key labels

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

    /**
     * Pre-formatted snapshot string; {@code null} means no active combining-mode arm
     * (overlay draws nothing).
     */
    @Nullable private String mSnapshot;

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
            mSnapshot = null;
            invalidateDrawingView();
            return;
        }
        mComplete        = complete;
        mPrefixRichScore = prefixRichScore;
        mGraceMs         = graceMs;
        mGate            = gate;
        mSnapshot        = buildSnapshot(complete, prefixRichScore, graceMs, gate);
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
        if (mSnapshot == null) return;
        mGate     = gate;
        mSnapshot = buildSnapshot(mComplete, mPrefixRichScore, mGraceMs, gate);
        invalidateDrawingView();
    }

    private static String buildSnapshot(final boolean complete, final float prefixRichScore,
            final int graceMs, @Nullable final String gate) {
        // Avoid String.format for the numeric fields to reduce alloc pressure; still called
        // only once per keystroke/gesture commit (not per frame), so a bit of string work here
        // is fine.
        final int prefixPct = Math.round(prefixRichScore * 100f);
        return "spacing | c:" + (complete ? "Y" : "N")
                + " px:" + prefixPct + "%"
                + " g:" + graceMs + "ms"
                + " [" + (gate != null ? gate : GATE_TIMER) + "]";
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled()) return;
        final String snap = mSnapshot;
        if (snap == null) return;

        final float textH  = mTextPaint.getTextSize();
        final float textW  = mTextPaint.measureText(snap);
        final float padH   = PADDING_PX;
        final float padV   = PADDING_PX;

        // Position: bottom-left of the keyboard area with a small margin.
        final float left   = padH;
        final float top    = mKeyboardHeight - textH - padV * 2f;
        final float right  = left + textW + padH * 2f;
        final float bottom = mKeyboardHeight - padV * 0.4f;

        canvas.drawRoundRect(left, top, right, bottom, 4f, 4f, mBgPaint);
        canvas.drawText(snap, left + padH, bottom - padV * 0.6f, mTextPaint);
    }

    @Override
    public void onDeallocateMemory() {
        mSnapshot = null;
    }

    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        // Position is fixed (bottom-left of keyboard) — no tracker tracking needed.
    }
}
