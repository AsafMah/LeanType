/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import helium314.keyboard.keyboard.AdaptiveKeyContext;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.latin.database.TouchModelDao;
import helium314.keyboard.latin.database.TouchModelManager;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;

/**
 * Debug visualization for adaptive typing (opt-in via {@code PREF_ADAPTIVE_DEBUG_OVERLAY}). Drawn
 * on top of the live keyboard, it makes the two halves of the feature visible as you type:
 *
 * <ul>
 *   <li><b>Learned key geometry</b> — for each letter key with a confident learned landing offset,
 *       a faint ring marks the key's geometric centre, an arrow points to where the user's taps
 *       actually land on average, and a filled dot marks that learned target. This is the same
 *       offset {@link helium314.keyboard.keyboard.KeyDetector} biases taps toward.</li>
 *   <li><b>Next-key context prior</b> — keys the current suggestions predict get a translucent
 *       halo whose radius grows with the prior weight, centred on the (possibly shifted) target.
 *       The halo appears/grows/shrinks between keystrokes, so the keyboard visibly "leans" toward
 *       the likely next key.</li>
 * </ul>
 *
 * <p>The overlay is purely visual — it never changes detection. It reads the same live model
 * ({@link TouchModelDao}), prior ({@link AdaptiveKeyContext}) and {@link SettingsValues} the engine
 * uses, so what you see is what the engine does (the halo radius is exaggerated relative to the
 * engine's sub-key boost so the effect is legible). Drawing is gated on the live pref each frame,
 * so it costs nothing when the toggle is off.
 *
 * <p>Threading mirrors the other previews: {@link #setKeyboard} runs on the keyboard-view layout
 * path and {@link #drawPreview} on {@code DrawingPreviewPlacerView}'s {@code onDraw}, both on the
 * main thread. Repaints between keystrokes are driven by {@link AdaptiveKeyContext}'s change
 * listener (also fired on the main thread), wired up by {@code MainKeyboardView}.
 */
public final class AdaptiveTargetsDrawingPreview extends AbstractDrawingPreview {
    // Halo radius for a fully-agreed prior (weight 1.0), as a fraction of key width. Deliberately
    // larger than KeyDetector's PRIOR_MAX_FRACTION (0.18) — this is a visualization, so the bulge
    // is exaggerated to read clearly; smaller weights scale down linearly.
    private static final float HALO_MAX_FRACTION = 0.55f;
    private static final float GEOM_DOT_RADIUS_PX = 3f;
    private static final float EFF_DOT_RADIUS_PX = 6f;
    private static final float MIN_ARROW_LENGTH_PX = 1.5f;

    /** Current keyboard, set on the layout path; iterated for keys at draw time. */
    private Keyboard mKeyboard;
    /** Keyboard-view padding: keys render at {@code getX()+paddingLeft, getY()+paddingTop}, and the
     *  placer canvas is already translated to the keyboard-view origin, so we add padding here. */
    private int mPaddingLeft;
    private int mPaddingTop;

    private final Paint mGeomPaint = new Paint();   // reference: geometric key centre
    private final Paint mArrowPaint = new Paint();  // shift from centre to learned target
    private final Paint mEffPaint = new Paint();    // learned landing target
    private final Paint mHaloFill = new Paint();    // prior bulge (fill)
    private final Paint mHaloStroke = new Paint();  // prior bulge (outline)

    public AdaptiveTargetsDrawingPreview() {
        mGeomPaint.setAntiAlias(true);
        mGeomPaint.setColor(Color.WHITE);
        mGeomPaint.setAlpha(0x66);
        mGeomPaint.setStyle(Paint.Style.STROKE);
        mGeomPaint.setStrokeWidth(2f);

        mArrowPaint.setAntiAlias(true);
        mArrowPaint.setColor(Color.rgb(255, 171, 0)); // amber
        mArrowPaint.setAlpha(0xCC);
        mArrowPaint.setStyle(Paint.Style.STROKE);
        mArrowPaint.setStrokeWidth(3f);
        mArrowPaint.setStrokeCap(Paint.Cap.ROUND);

        mEffPaint.setAntiAlias(true);
        mEffPaint.setColor(Color.rgb(255, 171, 0)); // amber
        mEffPaint.setAlpha(0xEE);
        mEffPaint.setStyle(Paint.Style.FILL);

        mHaloFill.setAntiAlias(true);
        mHaloFill.setColor(Color.rgb(0, 200, 83)); // green = "predicted, easier to hit"
        mHaloFill.setStyle(Paint.Style.FILL);

        mHaloStroke.setAntiAlias(true);
        mHaloStroke.setColor(Color.rgb(0, 200, 83));
        mHaloStroke.setStyle(Paint.Style.STROKE);
        mHaloStroke.setStrokeWidth(2.5f);
    }

    /** Hand the overlay the current keyboard and the view padding at which keys are rendered. */
    public void setKeyboard(final Keyboard keyboard, final int paddingLeft, final int paddingTop) {
        mKeyboard = keyboard;
        mPaddingLeft = paddingLeft;
        mPaddingTop = paddingTop;
        invalidateDrawingView();
    }

    /** Repaint hook for {@link AdaptiveKeyContext}'s change listener (fires on each keystroke). */
    public void onAdaptiveContextChanged() {
        invalidateDrawingView();
    }

    @Override
    public void onDeallocateMemory() {
        mKeyboard = null;
    }

    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        // No-op: the overlay is derived from the keyboard + model, not a single pointer.
    }

    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled()) return; // geometry valid
        final Keyboard keyboard = mKeyboard;
        if (keyboard == null) return;
        final SettingsValues sv = Settings.getValues();
        if (sv == null || !sv.mAdaptiveDebugOverlay) return;
        final boolean learn = sv.mAdaptiveKeyGeometry;
        final boolean prior = sv.mAdaptiveContextPrior;
        if (!learn && !prior) return;
        final int strength = sv.mAdaptiveKeyGeometryStrength;

        // Learned-offset lookup is keyed by layout + orientation, like KeyDetector.
        TouchModelDao dao = null;
        String layout = null;
        int orientation = 0;
        if (learn) {
            final Context ctx = Settings.getCurrentContext();
            if (ctx != null) {
                dao = TouchModelDao.getInstance(ctx);
                layout = Integer.toString(keyboard.mId.mElementId);
                orientation = ctx.getResources().getConfiguration().orientation;
            }
        }

        for (final Key key : keyboard.getSortedKeys()) {
            if (key.isSpacer()) continue;
            final int code = key.getCode();
            if (code <= 0 || !Character.isLetter(code)) continue;
            final int w = key.getWidth();
            final int h = key.getHeight();
            if (w <= 0 || h <= 0) continue;
            final float cx = mPaddingLeft + key.getX() + w / 2f;
            final float cy = mPaddingTop + key.getY() + h / 2f;

            float effX = cx;
            float effY = cy;
            boolean haveLearned = false;
            if (dao != null) {
                final TouchModelDao.Stat st = dao.get(code, layout, orientation);
                if (st != null && st.getCount() >= TouchModelDao.MIN_CONFIDENT_SAMPLES) {
                    final float[] off = TouchModelManager.adjustedOffset(st, w, h, strength);
                    effX = cx + off[0];
                    effY = cy + off[1];
                    haveLearned = true;
                }
            }

            // Prior halo first, so the learned dot/arrow sit on top of it.
            if (prior) {
                final float weight = AdaptiveKeyContext.weight(code);
                if (weight > 0f) {
                    final float r = weight * HALO_MAX_FRACTION * w;
                    mHaloFill.setAlpha(0x33);
                    canvas.drawCircle(effX, effY, r, mHaloFill);
                    mHaloStroke.setAlpha(0x99);
                    canvas.drawCircle(effX, effY, r, mHaloStroke);
                }
            }

            if (haveLearned) {
                canvas.drawCircle(cx, cy, GEOM_DOT_RADIUS_PX, mGeomPaint);
                final float dx = effX - cx;
                final float dy = effY - cy;
                if (dx * dx + dy * dy > MIN_ARROW_LENGTH_PX * MIN_ARROW_LENGTH_PX) {
                    canvas.drawLine(cx, cy, effX, effY, mArrowPaint);
                }
                canvas.drawCircle(effX, effY, EFF_DOT_RADIUS_PX, mEffPaint);
            }
        }
    }
}
